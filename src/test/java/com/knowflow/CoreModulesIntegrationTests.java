package com.knowflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.parser.deadletter.service.DeadLetterMessageService;
import com.knowflow.parser.messaging.ParseTaskDispatchMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "knowflow.storage.local.base-path=./target/test-uploads")
class CoreModulesIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeadLetterMessageService deadLetterMessageService;

    @Test
    void shouldLoginAndRunKnowledgeDocumentParseTaskFlow() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");

        String createKbJson = """
                {
                  "kbName": "IT Support Knowledge Base",
                  "description": "Knowledge base used by integration tests"
                }
                """;

        MvcResult createKbResult = mockMvc.perform(post("/api/v1/admin/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createKbJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.kbName").value("IT Support Knowledge Base"))
                .andReturn();

        Long knowledgeBaseId = readLong(createKbResult, "/data/id");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "vpn-guide.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "vpn troubleshooting guide".getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/admin/documents/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.docName").value("vpn-guide.txt"))
                .andExpect(jsonPath("$.data.parseStatus").value("PENDING"))
                .andReturn();

        Long documentId = readLong(uploadResult, "/data/id");
        Long parseTaskId = waitForLatestParseTaskId(token, documentId);
        waitForParseTaskSuccess(token, parseTaskId);

        mockMvc.perform(get("/api/v1/admin/documents/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(documentId))
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.chunkCount").value(1));
    }

    @Test
    void shouldReturnKnowledgeBaseDetailStatsAndFailureReasons() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Knowledge Detail KB", "Knowledge base used by detail stats tests");

        Long successfulDocumentId = uploadDocumentAndWaitForParse(
                token,
                knowledgeBaseId,
                "successful-guide.txt",
                "VPN guide: restart the desktop client, reconnect, and confirm the security certificate is valid."
        );
        waitForDocumentIndexSuccess(token, successfulDocumentId);

        MockMultipartFile failedFile = new MockMultipartFile(
                "file",
                "unsupported-guide.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4 unsupported format".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult failedUploadResult = mockMvc.perform(multipart("/api/v1/admin/documents/upload")
                        .file(failedFile)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.docName").value("unsupported-guide.pdf"))
                .andReturn();

        Long failedDocumentId = readLong(failedUploadResult, "/data/id");
        Long failedTaskId = waitForLatestParseTaskId(token, failedDocumentId);
        waitForParseTaskFailure(token, failedTaskId);

        deadLetterMessageService.recordDeadLetter(
                new ParseTaskDispatchMessage(failedTaskId, LocalDateTime.now()),
                "knowflow.parse.task.dlq",
                "knowflow.parse.task.dlx",
                "knowflow.parse.task"
        );

        MvcResult detailResult = mockMvc.perform(get("/api/v1/admin/knowledge-bases/{id}", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.docCount").value(2))
                .andExpect(jsonPath("$.data.stats.totalDocuments").value(2))
                .andExpect(jsonPath("$.data.stats.parseSuccessCount").value(1))
                .andExpect(jsonPath("$.data.stats.parseFailedCount").value(1))
                .andExpect(jsonPath("$.data.stats.indexSuccessCount").value(1))
                .andExpect(jsonPath("$.data.stats.indexFailedCount").value(1))
                .andExpect(jsonPath("$.data.stats.failedDocumentCount").value(1))
                .andExpect(jsonPath("$.data.stats.totalChunks").value(1))
                .andExpect(jsonPath("$.data.stats.openDeadLetterCount").value(1))
                .andReturn();

        JsonNode detailRoot = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        JsonNode topFailureReasons = detailRoot.at("/data/topFailureReasons");
        assertThat(topFailureReasons)
                .anyMatch(node -> node.path("reason").asText().contains("Unsupported file type")
                        || node.path("reason").asText().contains("Failed to read stored document"));

        JsonNode failedDocuments = detailRoot.at("/data/failedDocuments");
        assertThat(failedDocuments)
                .anyMatch(node -> failedDocumentId.equals(node.path("documentId").asLong())
                        && failedTaskId.equals(node.path("latestTaskId").asLong())
                        && node.path("deadLetterCount").asInt() == 1
                        && (node.path("errorMessage").asText().contains("Unsupported file type")
                        || node.path("errorMessage").asText().contains("Failed to read stored document")));
    }

    @Test
    void shouldSupportAdminManagementLifecycleForKnowledgeBaseDocumentAndParseTask() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(
                token,
                "Operations Runbook KB",
                "Knowledge base used by admin management lifecycle tests"
        );

        mockMvc.perform(put("/api/v1/admin/knowledge-bases/{id}", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kbName": "Operations Runbook KB Updated",
                                  "description": "Updated by the admin lifecycle integration test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(knowledgeBaseId))
                .andExpect(jsonPath("$.data.kbName").value("Operations Runbook KB Updated"));

        mockMvc.perform(put("/api/v1/admin/knowledge-bases/{id}/status", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/admin/knowledge-bases/{id}", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(put("/api/v1/admin/knowledge-bases/{id}/status", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ENABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult kbPageResult = mockMvc.perform(get("/api/v1/admin/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("keyword", "Runbook KB Updated")
                        .param("status", "ENABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode knowledgeBaseRecords = objectMapper.readTree(kbPageResult.getResponse().getContentAsString()).at("/data/records");
        assertThat(knowledgeBaseRecords)
                .anyMatch(node -> knowledgeBaseId.equals(node.path("id").asLong())
                        && "Operations Runbook KB Updated".equals(node.path("kbName").asText()));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "operations-runbook.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Operations runbook: restart the client service before reopening the support portal.".getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/admin/documents/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.docName").value("operations-runbook.txt"))
                .andReturn();

        Long documentId = readLong(uploadResult, "/data/id");
        Long parseTaskId = waitForLatestParseTaskId(token, documentId);
        waitForParseTaskSuccess(token, parseTaskId);
        waitForDocumentIndexSuccess(token, documentId);
        Long indexTaskId = waitForLatestTaskId(token, documentId, "INDEX_VECTOR");

        MvcResult documentPageResult = mockMvc.perform(get("/api/v1/admin/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId))
                        .param("parseStatus", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode documentRecords = objectMapper.readTree(documentPageResult.getResponse().getContentAsString()).at("/data/records");
        assertThat(documentRecords)
                .anyMatch(node -> documentId.equals(node.path("id").asLong())
                        && "operations-runbook.txt".equals(node.path("docName").asText()));

        mockMvc.perform(put("/api/v1/admin/documents/{id}/status", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/admin/documents/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.indexStatus").value("SUCCESS"));

        mockMvc.perform(post("/api/v1/admin/parse-tasks/{id}/retry", indexTaskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40901));

        MvcResult parseTaskPageResult = mockMvc.perform(get("/api/v1/admin/parse-tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("documentId", String.valueOf(documentId))
                        .param("taskType", "PARSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode parseTaskRecords = objectMapper.readTree(parseTaskPageResult.getResponse().getContentAsString()).at("/data/records");
        assertThat(parseTaskRecords)
                .anyMatch(node -> parseTaskId.equals(node.path("id").asLong())
                        && "PARSE".equals(node.path("taskType").asText()));

        MvcResult indexTaskPageResult = mockMvc.perform(get("/api/v1/admin/parse-tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("documentId", String.valueOf(documentId))
                        .param("taskType", "INDEX_VECTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode indexTaskRecords = objectMapper.readTree(indexTaskPageResult.getResponse().getContentAsString()).at("/data/records");
        assertThat(indexTaskRecords)
                .anyMatch(node -> indexTaskId.equals(node.path("id").asLong())
                        && "SUCCESS".equals(node.path("status").asText()));

        mockMvc.perform(delete("/api/v1/admin/documents/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/admin/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/admin/knowledge-bases/{id}", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.docCount").value(0));
    }

    @Test
    void shouldPreviewDownloadAndBatchOperateDocuments() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Document Ops KB", "Knowledge base for preview and batch operation tests");

        String firstContent = "Operations handbook: restart the desktop client before reopening the portal.";
        String secondContent = "Network handbook: verify DNS resolution before reopening the VPN gateway.";

        Long firstDocumentId = uploadDocumentAndWaitForParse(token, knowledgeBaseId, "operations-handbook.txt", firstContent);
        waitForDocumentIndexSuccess(token, firstDocumentId);
        Long secondDocumentId = uploadDocumentAndWaitForParse(token, knowledgeBaseId, "network-handbook.md", secondContent);
        waitForDocumentIndexSuccess(token, secondDocumentId);

        MvcResult previewResult = mockMvc.perform(get("/api/v1/admin/documents/{id}/preview", firstDocumentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.previewable").value(true))
                .andReturn();
        JsonNode previewData = objectMapper.readTree(previewResult.getResponse().getContentAsString()).at("/data");
        assertThat(previewData.path("previewText").asText()).contains("restart the desktop client");
        assertThat(previewData.path("truncated").asBoolean()).isFalse();

        MvcResult downloadResult = mockMvc.perform(get("/api/v1/admin/documents/{id}/download", firstDocumentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(downloadResult.getResponse().getContentAsByteArray())
                .isEqualTo(firstContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(put("/api/v1/admin/documents/batch/status")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentIds": [%d, %d],
                                  "status": "DISABLED"
                                }
                                """.formatted(firstDocumentId, secondDocumentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.affectedCount").value(2));

        mockMvc.perform(get("/api/v1/admin/documents/{id}", secondDocumentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/v1/admin/documents/batch/delete")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentIds": [%d, %d]
                                }
                                """.formatted(firstDocumentId, secondDocumentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.affectedCount").value(2));

        mockMvc.perform(get("/api/v1/admin/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void shouldFilterDeadLettersByTaskAndDocumentForGovernanceLinkage() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Failure Governance KB", "Knowledge base for dead letter filter tests");
        Long documentId = uploadDocumentAndWaitForParse(
                token,
                knowledgeBaseId,
                "failure-governance.txt",
                "Failure governance note: inspect dead letters when an async task cannot recover automatically."
        );
        waitForDocumentIndexSuccess(token, documentId);

        Long indexTaskId = waitForLatestTaskId(token, documentId, "INDEX_VECTOR");
        deadLetterMessageService.recordDeadLetter(
                new ParseTaskDispatchMessage(indexTaskId, LocalDateTime.now()),
                "knowflow.parse.task.dlq",
                "knowflow.parse.task.dlx",
                "knowflow.parse.task"
        );

        MvcResult listResult = mockMvc.perform(get("/api/v1/admin/dead-letters")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("taskType", "INDEX_VECTOR")
                        .param("taskId", String.valueOf(indexTaskId))
                        .param("documentId", String.valueOf(documentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode deadLetterRecords = objectMapper.readTree(listResult.getResponse().getContentAsString()).at("/data/records");
        assertThat(deadLetterRecords).isNotEmpty();
        Long deadLetterId = deadLetterRecords.get(0).path("id").asLong();
        assertThat(deadLetterRecords.get(0).path("taskId").asLong()).isEqualTo(indexTaskId);
        assertThat(deadLetterRecords.get(0).path("documentId").asLong()).isEqualTo(documentId);

        mockMvc.perform(get("/api/v1/admin/dead-letters/{id}", deadLetterId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(indexTaskId))
                .andExpect(jsonPath("$.data.documentId").value(documentId))
                .andExpect(jsonPath("$.data.taskType").value("INDEX_VECTOR"));
    }

    @Test
    void shouldExposeOpsTaskOverviewMetrics() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Ops Metrics KB", "Knowledge base used by ops metrics tests");
        Long documentId = uploadDocumentAndWaitForParse(
                token,
                knowledgeBaseId,
                "ops-metrics-guide.txt",
                "Ops metrics guide: observe parse task duration, failure rate, and dead letter queue count."
        );
        waitForDocumentIndexSuccess(token, documentId);
        Long indexTaskId = waitForLatestTaskId(token, documentId, "INDEX_VECTOR");

        deadLetterMessageService.recordDeadLetter(
                new ParseTaskDispatchMessage(indexTaskId, LocalDateTime.now()),
                "knowflow.parse.task.dlq",
                "knowflow.parse.task.dlx",
                "knowflow.parse.task.dead"
        );

        mockMvc.perform(get("/api/v1/admin/ops/tasks/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalTaskCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.successTaskCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.failedTaskCount").isNumber())
                .andExpect(jsonPath("$.data.failureRate").isNumber())
                .andExpect(jsonPath("$.data.healthScore").isNumber())
                .andExpect(jsonPath("$.data.healthLevel").exists())
                .andExpect(jsonPath("$.data.healthSummary").exists())
                .andExpect(jsonPath("$.data.healthIssues").isArray())
                .andExpect(jsonPath("$.data.avgDurationMs").exists())
                .andExpect(jsonPath("$.data.p95DurationMs").exists())
                .andExpect(jsonPath("$.data.deadLetterMetrics.totalCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.deadLetterMetrics.readyCount").value(1))
                .andExpect(jsonPath("$.data.taskTypeMetrics[0].taskType").exists())
                .andExpect(jsonPath("$.data.statusMetrics[0].status").exists());
        mockMvc.perform(get("/api/v1/admin/ops/infrastructure/health")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.overallStatus").exists())
                .andExpect(jsonPath("$.data.summary").exists())
                .andExpect(jsonPath("$.data.components").isArray())
                .andExpect(jsonPath("$.data.components[0].key").exists())
                .andExpect(jsonPath("$.data.components[0].status").exists());
    }

    @Test
    void shouldRejectProtectedRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/knowledge-bases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void shouldRejectTenantAdminWhenAccessingPlatformTenantApi() throws Exception {
        String tenantAdminToken = loginAndGetToken("tenant.admin", "Tenant@123");

        mockMvc.perform(get("/api/v1/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tenantAdminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void shouldIgnoreDuplicatedParseTaskConsumptionAfterSuccess() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Idempotent Parse KB", "Knowledge base used by idempotent task tests");
        Long documentId = uploadDocumentAndWaitForParse(
                token,
                knowledgeBaseId,
                "idempotent-parse.txt",
                "Idempotent parse guide: duplicate task messages must not turn a successful task into a failed task."
        );
        Long parseTaskId = waitForLatestParseTaskId(token, documentId);

        mockMvc.perform(get("/api/v1/admin/parse-tasks/{id}", parseTaskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mockMvc.perform(post("/api/v1/admin/parse-tasks/{id}/retry", parseTaskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40901));

        mockMvc.perform(get("/api/v1/admin/parse-tasks/{id}", parseTaskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mockMvc.perform(get("/api/v1/admin/documents/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"));
    }

    @Test
    void shouldCreateQaSessionAndAnswerWithKnowledgeSources() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "QA Knowledge Base", "Knowledge base used by QA tests");
        Long documentId = uploadDocumentAndWaitForParse(
                token,
                knowledgeBaseId,
                "vpn-runbook.txt",
                "VPN support guide: To reset the VPN client, open Settings, click Reset, and reconnect to the corporate network."
        );
        waitForDocumentIndexSuccess(token, documentId);

        String createSessionJson = """
                {
                  "knowledgeBaseId": %d,
                  "sessionTitle": "VPN Support Session"
                }
                """.formatted(knowledgeBaseId);

        MvcResult createSessionResult = mockMvc.perform(post("/api/v1/app/qa/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value(knowledgeBaseId))
                .andReturn();

        Long sessionId = readLong(createSessionResult, "/data/id");

        String askJson = """
                {
                  "sessionId": %d,
                  "question": "How do I reset the VPN client and reconnect to the corporate network?"
                }
                """.formatted(sessionId);

        MvcResult askResult = mockMvc.perform(post("/api/v1/app/qa/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answerStatus").value("GENERATING"))
                .andReturn();

        Long messageId = readLong(askResult, "/data/id");
        JsonNode askRoot = waitForQaMessageStatus(token, messageId, "SUCCESS");
        askRoot = waitForQaMessageSourceCount(token, messageId, 1);
        assertThat(askRoot.path("needHumanHandoff").asBoolean()).isFalse();
        assertThat(askRoot.path("sourceCount").asInt()).isGreaterThan(0);
        assertThat(askRoot.path("answerText").asText()).contains("knowledge base");

        MvcResult sourcesResult = mockMvc.perform(get("/api/v1/app/qa/messages/{id}/sources", messageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode sourcesRoot = objectMapper.readTree(sourcesResult.getResponse().getContentAsString());
        assertThat(sourcesRoot.at("/data/0/documentId").asLong()).isEqualTo(documentId);
        assertThat(sourcesRoot.at("/data/0/snippetText").asText()).contains("reset the VPN client");
        assertThat(sourcesRoot.at("/data/0/recallStrategy").asText()).isNotBlank();
        assertThat(sourcesRoot.at("/data/0/lexicalScore").isNumber()).isTrue();
        assertThat(sourcesRoot.at("/data/0/vectorScore").isNumber()).isTrue();

        MvcResult messagePageResult = mockMvc.perform(get("/api/v1/app/qa/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("sessionId", String.valueOf(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode messagePageRoot = objectMapper.readTree(messagePageResult.getResponse().getContentAsString());
        assertThat(messagePageRoot.at("/data/total").asLong()).isEqualTo(1L);
        assertThat(messagePageRoot.at("/data/records/0/id").asLong()).isEqualTo(messageId);

        String feedbackJson = """
                {
                  "feedbackType": "LIKE",
                  "feedbackReason": "Answer is useful"
                }
                """;

        mockMvc.perform(post("/api/v1/app/qa/messages/{id}/feedback", messageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldListEnabledKnowledgeBasesForWorkbench() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Workbench KB", "Knowledge base used by workbench option tests");

        MvcResult result = mockMvc.perform(get("/api/v1/app/knowledge-bases/options")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
        boolean found = false;
        for (JsonNode node : records) {
            if (knowledgeBaseId.equals(node.path("id").asLong())
                    && "Workbench KB".equals(node.path("kbName").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void shouldMarkQaMessageForHumanHandoffWhenNoReliableHit() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Fallback Knowledge Base", "Knowledge base used by no-hit QA tests");
        uploadDocumentAndWaitForParse(
                token,
                knowledgeBaseId,
                "vpn-faq.txt",
                "VPN access guide: If the VPN client is offline, restart the client and try again."
        );

        String createSessionJson = """
                {
                  "knowledgeBaseId": %d,
                  "sessionTitle": "Fallback Session"
                }
                """.formatted(knowledgeBaseId);

        MvcResult createSessionResult = mockMvc.perform(post("/api/v1/app/qa/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        Long sessionId = readLong(createSessionResult, "/data/id");

        String askJson = """
                {
                  "sessionId": %d,
                  "question": "What is the expense reimbursement approval workflow?"
                }
                """.formatted(sessionId);

        MvcResult askResult = mockMvc.perform(post("/api/v1/app/qa/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answerStatus").value("GENERATING"))
                .andReturn();
        JsonNode askRoot = waitForQaMessageStatus(token, readLong(askResult, "/data/id"), "NO_HIT");
        assertThat(askRoot.path("sourceCount").asInt()).isEqualTo(0);
        assertThat(askRoot.path("needHumanHandoff").asBoolean()).isTrue();
    }

    @Test
    void shouldConvertQaMessageToTicketAndCompleteProcessingFlow() throws Exception {
        String adminToken = loginAndGetToken("tenant.admin", "Tenant@123");
        Long supportRoleId = findRoleIdByCode(adminToken, "SUPPORT_AGENT");
        String supportUsername = "support.agent01";
        createUser(adminToken, supportUsername, "Support Agent 01", "Agent@123", supportRoleId);
        String supportToken = loginAndGetToken(supportUsername, "Agent@123");

        Long knowledgeBaseId = createKnowledgeBase(adminToken, "Ticket Handoff KB", "Knowledge base used by ticket handoff tests");
        uploadDocumentAndWaitForParse(
                adminToken,
                knowledgeBaseId,
                "vpn-ticket.txt",
                "VPN support notes: reset the client before reconnecting."
        );

        String createSessionJson = """
                {
                  "knowledgeBaseId": %d,
                  "sessionTitle": "Need Human Support"
                }
                """.formatted(knowledgeBaseId);

        MvcResult createSessionResult = mockMvc.perform(post("/api/v1/app/qa/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long sessionId = readLong(createSessionResult, "/data/id");

        String askJson = """
                {
                  "sessionId": %d,
                  "question": "How does the overseas travel reimbursement approval chain work?"
                }
                """.formatted(sessionId);

        MvcResult askResult = mockMvc.perform(post("/api/v1/app/qa/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answerStatus").value("GENERATING"))
                .andReturn();
        Long qaMessageId = readLong(askResult, "/data/id");
        JsonNode askRoot = waitForQaMessageStatus(adminToken, qaMessageId, "NO_HIT");
        assertThat(askRoot.path("needHumanHandoff").asBoolean()).isTrue();

        String handoffJson = """
                {
                  "title": "Need manual support for reimbursement process",
                  "content": "The AI answer did not solve the issue. Please assign a human agent.",
                  "priority": "HIGH"
                }
                """;

        MvcResult handoffResult = mockMvc.perform(post("/api/v1/app/qa/messages/{id}/handoff", qaMessageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoffJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sourceType").value("QA_HANDOFF"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.sourceQaMessageId").value(qaMessageId))
                .andReturn();
        Long ticketId = readLong(handoffResult, "/data/id");

        mockMvc.perform(get("/api/v1/app/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].id").value(ticketId));

        mockMvc.perform(get("/api/v1/app/tickets/{id}", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.sourceQuestionText").exists())
                .andExpect(jsonPath("$.data.sourceAnswerText").exists());

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/accept", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(supportToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.assigneeName").value("Support Agent 01"));

        String agentCommentJson = """
                {
                  "commentType": "AGENT_REPLY",
                  "content": "Please send a screenshot of the reimbursement page.",
                  "visibleToUser": true
                }
                """;

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/comment", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(supportToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentCommentJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.commentType").value("AGENT_REPLY"))
                .andExpect(jsonPath("$.data.visibleToUser").value(true));

        mockMvc.perform(get("/api/v1/app/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].commentType").value("AGENT_REPLY"));

        String userReplyJson = """
                {
                  "content": "I have uploaded the screenshot and still need help."
                }
                """;

        mockMvc.perform(post("/api/v1/app/tickets/{id}/reply", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userReplyJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.commentType").value("USER_REPLY"));

        String resolveJson = """
                {
                  "solution": "The finance approver group permission has been fixed and the reimbursement workflow is available now."
                }
                """;

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/resolve", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(supportToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        mockMvc.perform(get("/api/v1/admin/tickets/{id}/flows", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(supportToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].actionType").value("CREATE"))
                .andExpect(jsonPath("$.data[1].actionType").value("ACCEPT"))
                .andExpect(jsonPath("$.data[2].actionType").value("AGENT_REPLY"))
                .andExpect(jsonPath("$.data[3].actionType").value("USER_REPLY"))
                .andExpect(jsonPath("$.data[4].actionType").value("RESOLVE"));

        mockMvc.perform(get("/api/v1/admin/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(supportToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].commentType").value("AGENT_REPLY"))
                .andExpect(jsonPath("$.data[1].commentType").value("USER_REPLY"))
                .andExpect(jsonPath("$.data[2].commentType").value("SOLUTION"));

        String closeJson = """
                {
                  "remark": "Issue verified and closed"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/close", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(supportToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(get("/api/v1/app/tickets/{id}", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void shouldGenerateKnowledgeDraftFromResolvedTicketAndPublishBackToKnowledgeBase() throws Exception {
        String adminToken = loginAndGetToken("tenant.admin", "Tenant@123");
        Long supportRoleId = findRoleIdByCode(adminToken, "SUPPORT_AGENT");
        String supportUsername = "support.agent02";
        createUser(adminToken, supportUsername, "Support Agent 02", "Agent@123", supportRoleId);
        String supportToken = loginAndGetToken(supportUsername, "Agent@123");
        String operatorToken = loginAndGetToken("knowledge.operator", "Tenant@123");

        Long knowledgeBaseId = createKnowledgeBase(adminToken, "Backflow Knowledge Base", "Knowledge base used by ticket backflow tests");
        uploadDocumentAndWaitForParse(
                adminToken,
                knowledgeBaseId,
                "backflow-seed.txt",
                "VPN reset note: restart the VPN client before reconnecting."
        );

        String createSessionJson = """
                {
                  "knowledgeBaseId": %d,
                  "sessionTitle": "Backflow Session"
                }
                """.formatted(knowledgeBaseId);

        MvcResult createSessionResult = mockMvc.perform(post("/api/v1/app/qa/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long sessionId = readLong(createSessionResult, "/data/id");

        String originalQuestion = "What is the overseas travel reimbursement approval chain?";
        String askJson = """
                {
                  "sessionId": %d,
                  "question": "%s"
                }
                """.formatted(sessionId, originalQuestion);

        MvcResult askResult = mockMvc.perform(post("/api/v1/app/qa/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answerStatus").value("GENERATING"))
                .andReturn();
        Long qaMessageId = readLong(askResult, "/data/id");
        waitForQaMessageStatus(adminToken, qaMessageId, "NO_HIT");

        String handoffJson = """
                {
                  "title": "Manual support for reimbursement workflow",
                  "content": "Need a human to explain the approval chain.",
                  "priority": "HIGH"
                }
                """;

        MvcResult handoffResult = mockMvc.perform(post("/api/v1/app/qa/messages/{id}/handoff", qaMessageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handoffJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long ticketId = readLong(handoffResult, "/data/id");

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/accept", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(supportToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        String resolveJson = """
                {
                  "solution": "The overseas travel reimbursement approval chain is employee submission, department manager approval, finance manager review, and CFO final approval."
                }
                """;

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/resolve", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(supportToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        String createDraftJson = """
                {
                  "knowledgeBaseId": %d,
                  "draftType": "FAQ",
                  "title": "Overseas Travel Reimbursement Approval Chain"
                }
                """.formatted(knowledgeBaseId);

        MvcResult createDraftResult = mockMvc.perform(post("/api/v1/admin/knowledge-drafts/from-ticket/{ticketId}", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(operatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value(knowledgeBaseId))
                .andExpect(jsonPath("$.data.questionText").value(originalQuestion))
                .andExpect(jsonPath("$.data.answerText").value(org.hamcrest.Matchers.containsString("finance manager review")))
                .andReturn();
        Long draftId = readLong(createDraftResult, "/data/id");

        mockMvc.perform(get("/api/v1/admin/knowledge-drafts")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(operatorToken))
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].id").value(draftId));

        mockMvc.perform(get("/api/v1/admin/knowledge-drafts/{id}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(operatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sourceTicketId").value(ticketId))
                .andExpect(jsonPath("$.data.sourceTicketNo").exists());

        String approveJson = """
                {
                  "reviewRemark": "Reviewed and ready for publishing"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/knowledge-drafts/{id}/approve", draftId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(operatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        MvcResult publishResult = mockMvc.perform(post("/api/v1/admin/knowledge-drafts/{id}/publish", draftId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(operatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedDocumentId").exists())
                .andReturn();
        Long publishedDocumentId = readLong(publishResult, "/data/publishedDocumentId");

        Long parseTaskId = waitForLatestParseTaskId(operatorToken, publishedDocumentId);
        waitForParseTaskSuccess(operatorToken, parseTaskId);

        mockMvc.perform(get("/api/v1/admin/documents/{id}", publishedDocumentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(operatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sourceType").value("TICKET_BACKFLOW"))
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"));

        String recallSessionJson = """
                {
                  "knowledgeBaseId": %d,
                  "sessionTitle": "Backflow Recall Session"
                }
                """.formatted(knowledgeBaseId);

        MvcResult recallSessionResult = mockMvc.perform(post("/api/v1/app/qa/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recallSessionJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long recallSessionId = readLong(recallSessionResult, "/data/id");

        String recallAskJson = """
                {
                  "sessionId": %d,
                  "question": "%s"
                }
                """.formatted(recallSessionId, originalQuestion);

        MvcResult recallAskResult = mockMvc.perform(post("/api/v1/app/qa/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recallAskJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answerStatus").value("GENERATING"))
                .andReturn();
        Long recallMessageId = readLong(recallAskResult, "/data/id");
        JsonNode recallMessage = waitForQaMessageStatus(adminToken, recallMessageId, "SUCCESS");
        assertThat(recallMessage.path("needHumanHandoff").asBoolean()).isFalse();

        MvcResult recallSourcesResult = mockMvc.perform(get("/api/v1/app/qa/messages/{id}/sources", recallMessageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode recallSources = objectMapper.readTree(recallSourcesResult.getResponse().getContentAsString()).at("/data");
        assertThat(recallSources)
                .anyMatch(node -> publishedDocumentId.equals(node.path("documentId").asLong()));
        assertThat(recallSources)
                .anyMatch(node -> node.path("snippetText").asText().toLowerCase()
                        .contains("overseas travel reimbursement approval chain"));
    }

    @Test
    void shouldProvideDashboardOverviewAndNoHitAnalysis() throws Exception {
        String adminToken = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken, "Dashboard Analytics KB", "Knowledge base used by dashboard tests");
        uploadDocumentAndWaitForParse(
                adminToken,
                knowledgeBaseId,
                "dashboard-analytics.txt",
                "Dashboard analytics guide: to reset the dashboard VPN widget, open widget settings, click reset, and reconnect."
        );

        String successQuestion = "To reset the dashboard VPN widget, open widget settings, click reset, and reconnect.";
        String noHitQuestion = "What is the dashboard Mars reimbursement policy for 2026?";

        Long successMessageId = askQuestion(adminToken, knowledgeBaseId, "Dashboard Success Session", successQuestion);
        mockMvc.perform(get("/api/v1/app/qa/messages/{id}", successMessageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.answerStatus").value("SUCCESS"));

        Long noHitMessageId1 = askQuestion(adminToken, knowledgeBaseId, "Dashboard NoHit Session A", noHitQuestion);
        Long noHitMessageId2 = askQuestion(adminToken, knowledgeBaseId, "Dashboard NoHit Session B", noHitQuestion);

        MvcResult handoffResult = mockMvc.perform(post("/api/v1/app/qa/messages/{id}/handoff", noHitMessageId1)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Need manual clarification for Mars reimbursement policy",
                                  "content": "The knowledge base does not cover this policy question.",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sourceQaMessageId").value(noHitMessageId1))
                .andReturn();
        Long ticketId = readLong(handoffResult, "/data/id");

        MvcResult overviewResult = mockMvc.perform(get("/api/v1/admin/dashboard/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.qaCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.qaNoHitCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.ticketCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.handoffCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andReturn();

        JsonNode overviewRoot = objectMapper.readTree(overviewResult.getResponse().getContentAsString());
        assertThat(overviewRoot.at("/data/qaHitRate").asDouble()).isGreaterThanOrEqualTo(0D);
        assertThat(overviewRoot.at("/data/handoffRate").asDouble()).isGreaterThan(0D);

        MvcResult trendsResult = mockMvc.perform(get("/api/v1/admin/dashboard/trends")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode trendRecords = objectMapper.readTree(trendsResult.getResponse().getContentAsString()).at("/data");
        boolean hasTodayTrend = false;
        for (JsonNode node : trendRecords) {
            if (node.path("qaCount").asLong() >= 3
                    && node.path("noHitCount").asLong() >= 2
                    && node.path("ticketCreatedCount").asLong() >= 1) {
                hasTodayTrend = true;
                break;
            }
        }
        assertThat(hasTodayTrend).isTrue();

        MvcResult hotQuestionsResult = mockMvc.perform(get("/api/v1/admin/dashboard/hot-questions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .param("days", "30")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode hotQuestions = objectMapper.readTree(hotQuestionsResult.getResponse().getContentAsString()).at("/data");
        boolean foundHotQuestion = false;
        for (JsonNode node : hotQuestions) {
            if (noHitQuestion.equals(node.path("questionText").asText()) && node.path("askCount").asLong() >= 2) {
                foundHotQuestion = true;
                break;
            }
        }
        assertThat(foundHotQuestion).isTrue();

        MvcResult noHitAnalysisResult = mockMvc.perform(get("/api/v1/admin/dashboard/no-hit-questions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .param("days", "30")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode noHitQuestions = objectMapper.readTree(noHitAnalysisResult.getResponse().getContentAsString()).at("/data");
        boolean foundNoHitAnalysis = false;
        for (JsonNode node : noHitQuestions) {
            if (noHitQuestion.equals(node.path("questionText").asText())
                    && node.path("askCount").asLong() >= 2
                    && node.path("relatedTicketCount").asLong() >= 1
                    && node.path("suggestedAction").asText().contains("Prioritize")) {
                foundNoHitAnalysis = true;
                break;
            }
        }
        assertThat(foundNoHitAnalysis).isTrue();

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/accept", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/resolve", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "solution": "A formal Mars reimbursement policy does not exist. Direct requests to the finance governance group and publish a clear FAQ after confirmation."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        MvcResult draftResult = mockMvc.perform(post("/api/v1/admin/knowledge-drafts/from-ticket/{ticketId}", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "draftType": "FAQ",
                                  "title": "Mars reimbursement policy clarification"
                                }
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andReturn();
        Long draftId = readLong(draftResult, "/data/id");

        MvcResult questionDetailResult = mockMvc.perform(get("/api/v1/admin/dashboard/question-detail")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .param("days", "30")
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId))
                        .param("questionText", noHitQuestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.askCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.relatedTicketCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.relatedDraftCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn();

        JsonNode questionDetailRoot = objectMapper.readTree(questionDetailResult.getResponse().getContentAsString());
        assertThat(questionDetailRoot.at("/data/suggestedAction").asText()).contains("review");

        boolean foundQuestionDetailTicket = false;
        for (JsonNode node : questionDetailRoot.at("/data/tickets")) {
            if (ticketId.equals(node.path("id").asLong())) {
                foundQuestionDetailTicket = true;
                break;
            }
        }
        assertThat(foundQuestionDetailTicket).isTrue();

        boolean foundQuestionDetailDraft = false;
        for (JsonNode node : questionDetailRoot.at("/data/drafts")) {
            if (draftId.equals(node.path("id").asLong())) {
                foundQuestionDetailDraft = true;
                break;
            }
        }
        assertThat(foundQuestionDetailDraft).isTrue();
    }

    @Test
    void shouldProvideConsoleOptionsForTicketAndDraftPages() throws Exception {
        String adminToken = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken, "Console Options KB", "Knowledge base used by console option tests");
        Long supportRoleId = findRoleIdByCode(adminToken, "SUPPORT_AGENT");
        String supportUsername = "support.console";
        createUser(adminToken, supportUsername, "Support Console", "Agent@123", supportRoleId);
        String supportToken = loginAndGetToken(supportUsername, "Agent@123");

        MvcResult assigneeResult = mockMvc.perform(get("/api/v1/admin/tickets/assignees")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(supportToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode assignees = objectMapper.readTree(assigneeResult.getResponse().getContentAsString()).at("/data");
        boolean foundSupportUser = false;
        for (JsonNode node : assignees) {
            if (supportUsername.equals(node.path("username").asText())) {
                foundSupportUser = true;
                break;
            }
        }
        assertThat(foundSupportUser).isTrue();

        MvcResult knowledgeBaseOptionResult = mockMvc.perform(get("/api/v1/admin/knowledge-drafts/knowledge-bases/options")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(supportToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode knowledgeBases = objectMapper.readTree(knowledgeBaseOptionResult.getResponse().getContentAsString()).at("/data");
        boolean foundKnowledgeBase = false;
        for (JsonNode node : knowledgeBases) {
            if (knowledgeBaseId.equals(node.path("id").asLong())) {
                foundKnowledgeBase = true;
                break;
            }
        }
        assertThat(foundKnowledgeBase).isTrue();
    }

    @Test
    void shouldExposeAdminQaRecordsWithLinkedTicketAndDraftData() throws Exception {
        String adminToken = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken, "QA Records KB", "Knowledge base used by admin QA record tests");
        uploadDocumentAndWaitForParse(
                adminToken,
                knowledgeBaseId,
                "qa-records-guide.txt",
                "QA records guide: reset the VPN client from Settings, click Reset, and reconnect to the company network."
        );

        Long successMessageId = askQuestion(
                adminToken,
                knowledgeBaseId,
                "QA Records Success Session",
                "How do I reset the VPN client from Settings and reconnect to the company network?"
        );

        MvcResult adminSourcesResult = mockMvc.perform(get("/api/v1/admin/qa-records/{id}/sources", successMessageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode adminSources = objectMapper.readTree(adminSourcesResult.getResponse().getContentAsString()).at("/data");
        assertThat(adminSources.isArray()).isTrue();
        assertThat(adminSources.size()).isGreaterThan(0);

        String noHitQuestion = "How is the lunar reimbursement process escalated for qa-records testing?";
        Long noHitMessageId = askQuestion(adminToken, knowledgeBaseId, "QA Records NoHit Session", noHitQuestion);

        MvcResult handoffResult = mockMvc.perform(post("/api/v1/app/qa/messages/{id}/handoff", noHitMessageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Need manual clarification for lunar reimbursement",
                                  "content": "This policy question is not covered by the knowledge base.",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long ticketId = readLong(handoffResult, "/data/id");

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/accept", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/resolve", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "solution": "No lunar reimbursement workflow exists. Escalate these requests to the finance governance team."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        MvcResult draftResult = mockMvc.perform(post("/api/v1/admin/knowledge-drafts/from-ticket/{ticketId}", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "draftType": "FAQ",
                                  "title": "Lunar reimbursement escalation policy"
                                }
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andReturn();
        Long draftId = readLong(draftResult, "/data/id");

        MvcResult qaRecordDetailResult = mockMvc.perform(get("/api/v1/admin/qa-records/{id}", noHitMessageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ticketId").value(ticketId))
                .andExpect(jsonPath("$.data.draftId").value(draftId))
                .andReturn();
        JsonNode qaRecordDetail = objectMapper.readTree(qaRecordDetailResult.getResponse().getContentAsString());
        assertThat(qaRecordDetail.at("/data/ticketStatus").asText()).isEqualTo("RESOLVED");
        assertThat(qaRecordDetail.at("/data/draftStatus").asText()).isEqualTo("PENDING_REVIEW");

        MvcResult qaRecordPageResult = mockMvc.perform(get("/api/v1/admin/qa-records")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .param("keyword", "lunar reimbursement process")
                        .param("answerStatus", "NO_HIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode qaRecords = objectMapper.readTree(qaRecordPageResult.getResponse().getContentAsString()).at("/data/records");
        boolean foundLinkedRecord = false;
        for (JsonNode node : qaRecords) {
            if (noHitMessageId.equals(node.path("id").asLong())
                    && ticketId.equals(node.path("ticketId").asLong())
                    && draftId.equals(node.path("draftId").asLong())) {
                foundLinkedRecord = true;
                break;
            }
        }
        assertThat(foundLinkedRecord).isTrue();
    }

    @Test
    void shouldRecordAuditLogsForTicketAndKnowledgeDraftOperations() throws Exception {
        String adminToken = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(adminToken, "Audit Trail KB", "Knowledge base used by audit trail tests");

        String auditQuestion = "What is the Saturn expense approval workflow for audit testing?";
        Long qaMessageId = askQuestion(adminToken, knowledgeBaseId, "Audit Trail Session", auditQuestion);

        MvcResult handoffResult = mockMvc.perform(post("/api/v1/app/qa/messages/{id}/handoff", qaMessageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Need manual clarification for Saturn expense workflow",
                                  "content": "Create a support ticket so that finance can review the request.",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long ticketId = readLong(handoffResult, "/data/id");

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/accept", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/admin/tickets/{id}/resolve", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "solution": "No Saturn expense workflow exists. Route these requests to the finance governance team."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult createDraftResult = mockMvc.perform(post("/api/v1/admin/knowledge-drafts/from-ticket/{ticketId}", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "draftType": "FAQ",
                                  "title": "Saturn expense workflow clarification"
                                }
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long draftId = readLong(createDraftResult, "/data/id");

        mockMvc.perform(post("/api/v1/admin/knowledge-drafts/{id}/approve", draftId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewRemark": "Approved for publication"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/admin/knowledge-drafts/{id}/publish", draftId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        MvcResult handoffAuditListResult = mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .param("actionCode", "HANDOFF_TO_TICKET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode handoffAuditRecords = objectMapper.readTree(handoffAuditListResult.getResponse().getContentAsString()).at("/data/records");
        Long handoffAuditLogId = null;
        for (JsonNode node : handoffAuditRecords) {
            if (ticketId.equals(node.path("bizId").asLong())) {
                handoffAuditLogId = node.path("id").asLong();
                assertThat(node.path("successFlag").asBoolean()).isTrue();
                break;
            }
        }
        assertThat(handoffAuditLogId).isNotNull();

        mockMvc.perform(get("/api/v1/admin/audit-logs/{id}", handoffAuditLogId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.bizId").value(ticketId))
                .andExpect(jsonPath("$.data.requestUri").value("/api/v1/app/qa/messages/" + qaMessageId + "/handoff"));

        MvcResult publishAuditListResult = mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken))
                        .param("actionCode", "PUBLISH")
                        .param("bizType", "KNOWLEDGE_DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode publishAuditRecords = objectMapper.readTree(publishAuditListResult.getResponse().getContentAsString()).at("/data/records");
        Long publishAuditLogId = null;
        for (JsonNode node : publishAuditRecords) {
            if (draftId.equals(node.path("bizId").asLong())) {
                publishAuditLogId = node.path("id").asLong();
                assertThat(node.path("successFlag").asBoolean()).isTrue();
                break;
            }
        }
        assertThat(publishAuditLogId).isNotNull();

        mockMvc.perform(get("/api/v1/admin/audit-logs/{id}", publishAuditLogId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.bizId").value(draftId))
                .andExpect(jsonPath("$.data.requestUri").value("/api/v1/admin/knowledge-drafts/" + draftId + "/publish"))
                .andExpect(jsonPath("$.data.successFlag").value(true));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String loginJson = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.at("/data/token").asText();
    }

    private String bearerToken(String token) {
        return "Bearer " + token;
    }

    private Long createKnowledgeBase(String token, String kbName, String description) throws Exception {
        String createKbJson = """
                {
                  "kbName": "%s",
                  "description": "%s"
                }
                """.formatted(kbName, description);

        MvcResult createKbResult = mockMvc.perform(post("/api/v1/admin/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createKbJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        return readLong(createKbResult, "/data/id");
    }

    private Long findRoleIdByCode(String token, String roleCode) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode node : root.at("/data")) {
            if (roleCode.equals(node.path("roleCode").asText())) {
                return node.path("id").asLong();
            }
        }
        throw new AssertionError("Role not found: " + roleCode);
    }

    private void createUser(String token, String username, String realName, String password, Long roleId) throws Exception {
        String createUserJson = """
                {
                  "username": "%s",
                  "realName": "%s",
                  "password": "%s",
                  "email": "%s@knowflow.local",
                  "phone": "13900000099",
                  "roleIds": [%d]
                }
                """.formatted(username, realName, password, username.replace('.', '_'), roleId);

        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username));
    }

    private Long uploadDocumentAndWaitForParse(String token, Long knowledgeBaseId, String fileName, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/admin/documents/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        Long documentId = readLong(uploadResult, "/data/id");
        Long parseTaskId = waitForLatestParseTaskId(token, documentId);
        waitForParseTaskSuccess(token, parseTaskId);
        return documentId;
    }

    private Long askQuestion(String token, Long knowledgeBaseId, String sessionTitle, String question) throws Exception {
        String createSessionJson = """
                {
                  "knowledgeBaseId": %d,
                  "sessionTitle": "%s"
                }
                """.formatted(knowledgeBaseId, sessionTitle);

        MvcResult createSessionResult = mockMvc.perform(post("/api/v1/app/qa/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long sessionId = readLong(createSessionResult, "/data/id");

        String askJson = """
                {
                  "sessionId": %d,
                  "question": "%s"
                }
                """.formatted(sessionId, question);

        MvcResult askResult = mockMvc.perform(post("/api/v1/app/qa/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long messageId = readLong(askResult, "/data/id");
        waitForQaMessageCompleted(token, messageId);
        return messageId;
    }

    private JsonNode waitForQaMessageStatus(String token, Long messageId, String expectedStatus) throws Exception {
        for (int i = 0; i < 60; i++) {
            JsonNode data = loadQaMessage(token, messageId);
            String status = data.path("answerStatus").asText();
            if (expectedStatus.equals(status)) {
                return data;
            }
            if ("FAILED".equals(status) && !"FAILED".equals(expectedStatus)) {
                throw new AssertionError("QA message failed: " + data.path("answerText").asText());
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("QA message did not reach status " + expectedStatus + " in time. messageId=" + messageId);
    }

    private JsonNode waitForQaMessageCompleted(String token, Long messageId) throws Exception {
        for (int i = 0; i < 60; i++) {
            JsonNode data = loadQaMessage(token, messageId);
            String status = data.path("answerStatus").asText();
            if (!"GENERATING".equals(status)) {
                return data;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("QA message stayed GENERATING for too long. messageId=" + messageId);
    }

    private JsonNode waitForQaMessageSourceCount(String token, Long messageId, int minSourceCount) throws Exception {
        for (int i = 0; i < 60; i++) {
            JsonNode data = loadQaMessage(token, messageId);
            if (data.path("sourceCount").asInt() >= minSourceCount) {
                return data;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("QA message sources were not visible in time. messageId=" + messageId);
    }

    private JsonNode loadQaMessage(String token, Long messageId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/app/qa/messages/{id}", messageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
    }

    private Long waitForLatestParseTaskId(String token, Long documentId) throws Exception {
        return waitForLatestTaskId(token, documentId, "PARSE");
    }

    private Long waitForLatestTaskId(String token, Long documentId, String taskType) throws Exception {
        for (int i = 0; i < 20; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/admin/parse-tasks")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                            .param("documentId", String.valueOf(documentId))
                            .param("taskType", taskType))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode records = root.at("/data/records");
            if (records.isArray() && records.size() > 0) {
                return records.get(0).path("id").asLong();
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Task was not created in time. taskType=" + taskType);
    }

    private void waitForParseTaskSuccess(String token, Long parseTaskId) throws Exception {
        for (int i = 0; i < 40; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/admin/parse-tasks/{id}", parseTaskId)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            String status = root.at("/data/status").asText();
            if ("SUCCESS".equals(status)) {
                return;
            }
            if ("FAILED".equals(status)) {
                throw new AssertionError("Parse task failed: " + root.at("/data/errorMessage").asText());
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Parse task did not finish in time");
    }

    private void waitForParseTaskFailure(String token, Long parseTaskId) throws Exception {
        for (int i = 0; i < 40; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/admin/parse-tasks/{id}", parseTaskId)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            String status = root.at("/data/status").asText();
            if ("FAILED".equals(status)) {
                return;
            }
            if ("SUCCESS".equals(status)) {
                throw new AssertionError("Parse task unexpectedly succeeded");
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Parse task did not fail in time");
    }

    private void waitForParseTaskRetrySuccess(String token, Long parseTaskId, int expectedRetryCount) throws Exception {
        for (int i = 0; i < 40; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/admin/parse-tasks/{id}", parseTaskId)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            String taskStatus = root.at("/data/status").asText();
            int retryCount = root.at("/data/retryCount").asInt();
            if (retryCount >= expectedRetryCount && "SUCCESS".equals(taskStatus)) {
                return;
            }
            if (retryCount >= expectedRetryCount && "FAILED".equals(taskStatus)) {
                throw new AssertionError("Retried parse task failed: " + root.at("/data/errorMessage").asText());
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Retried parse task did not finish in time");
    }

    private void waitForDocumentIndexSuccess(String token, Long documentId) throws Exception {
        for (int i = 0; i < 40; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/admin/documents/{id}", documentId)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            String indexStatus = root.at("/data/indexStatus").asText();
            if ("SUCCESS".equals(indexStatus)) {
                return;
            }
            if ("FAILED".equals(indexStatus)) {
                throw new AssertionError("Document index task failed");
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Document index task did not finish in time");
    }

    private Long readLong(MvcResult result, String pointer) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.at(pointer).asLong();
    }
}
