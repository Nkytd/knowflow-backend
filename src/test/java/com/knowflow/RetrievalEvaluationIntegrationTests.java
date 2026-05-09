package com.knowflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "knowflow.storage.local.base-path=./target/test-uploads")
class RetrievalEvaluationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRunRetrievalEvaluationAndReturnQualityMetrics() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Retrieval Evaluation KB", "Retrieval evaluation integration test");

        String worldModel = "\u4E16\u754C\u6A21\u578B";
        Long worldModelDocumentId = uploadDocumentAndWaitForParse(
                token,
                knowledgeBaseId,
                "world-model-eval.txt",
                worldModel + "\u662F\u667A\u80FD\u4F53\u7528\u4E8E\u8868\u793A\u5916\u90E8\u73AF\u5883\u89C4\u5F8B\u7684\u5185\u90E8\u6A21\u578B\uFF0C"
                        + "\u53EF\u4EE5\u5E2E\u52A9\u9884\u6D4B\u672A\u6765\u72B6\u6001\u5E76\u8BC4\u4F30\u52A8\u4F5C\u540E\u679C\u3002"
        );
        waitForDocumentIndexSuccess(token, worldModelDocumentId);

        Long wifiDocumentId = uploadDocumentAndWaitForParse(
                token,
                knowledgeBaseId,
                "wifi-eval.txt",
                "Office Wi-Fi onboarding guide covers SSID setup, password rotation, and captive portal login."
        );
        waitForDocumentIndexSuccess(token, wifiDocumentId);

        Long successCaseId = createEvalCase(
                token,
                knowledgeBaseId,
                "World model should hit expected document",
                "\u4EC0\u4E48\u662F" + worldModel,
                "SUCCESS",
                worldModelDocumentId,
                """
                        ["%s", "%s"]
                        """.formatted(worldModel, "\u9884\u6D4B\u672A\u6765\u72B6\u6001")
        );
        Long noHitCaseId = createEvalCase(
                token,
                knowledgeBaseId,
                "Out of scope finance question should no-hit",
                "How do I submit a Mars travel reimbursement request?",
                "NO_HIT",
                null,
                "[]"
        );

        MvcResult runResult = mockMvc.perform(post("/api/v1/admin/retrieval-evaluations/runs")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "caseIds": [%d, %d],
                                  "topK": 5
                                }
                                """.formatted(knowledgeBaseId, successCaseId, noHitCaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCases").value(2))
                .andExpect(jsonPath("$.data.passedCases").value(2))
                .andExpect(jsonPath("$.data.failedCases").value(0))
                .andExpect(jsonPath("$.data.passRate").value(1.0D))
                .andExpect(jsonPath("$.data.recallAtK").value(1.0D))
                .andExpect(jsonPath("$.data.top1HitRate").value(1.0D))
                .andExpect(jsonPath("$.data.noHitAccuracy").value(1.0D))
                .andExpect(jsonPath("$.data.results").isArray())
                .andReturn();

        JsonNode runData = readData(runResult);
        Long runId = runData.path("id").asLong();
        assertThat(runData.path("runNo").asText()).startsWith("QAE");
        assertThat(runData.path("avgTopScore").asDouble()).isGreaterThan(0D);
        assertThat(runData.path("results").size()).isEqualTo(2);
        assertThat(runData.path("results").get(0).path("hitRank").asInt()).isEqualTo(1);
        assertThat(runData.path("results").get(0).path("queryVariants")).isNotEmpty();
        assertThat(runData.path("results").get(1).path("actualStatus").asText()).isEqualTo("NO_HIT");

        mockMvc.perform(get("/api/v1/admin/retrieval-evaluations/runs/{id}/results", runId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].passed").value(true))
                .andExpect(jsonPath("$.data[0].hits[0].documentName").value("world-model-eval.txt"));
    }

    private Long createEvalCase(String token,
                                Long knowledgeBaseId,
                                String caseName,
                                String question,
                                String expectedStatus,
                                Long expectedDocumentId,
                                String expectedKeywordsJson) throws Exception {
        String documentField = expectedDocumentId == null ? "" : "\"expectedDocumentId\": %d,".formatted(expectedDocumentId);
        MvcResult result = mockMvc.perform(post("/api/v1/admin/retrieval-evaluations/cases")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "caseName": "%s",
                                  "questionText": "%s",
                                  "expectedStatus": "%s",
                                  %s
                                  "expectedKeywords": %s,
                                  "topK": 5,
                                  "enabled": true
                                }
                                """.formatted(knowledgeBaseId, caseName, question, expectedStatus, documentField, expectedKeywordsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readLong(result, "/data/id");
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).at("/data/token").asText();
    }

    private Long createKnowledgeBase(String token, String name, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kbName": "%s",
                                  "description": "%s",
                                  "visibility": "PRIVATE"
                                }
                                """.formatted(name, description)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readLong(result, "/data/id");
    }

    private Long uploadDocumentAndWaitForParse(String token, Long knowledgeBaseId, String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8)
        );
        MvcResult result = mockMvc.perform(multipart("/api/v1/admin/documents/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        Long documentId = readLong(result, "/data/id");
        Long parseTaskId = waitForLatestTaskId(token, documentId, "PARSE");
        waitForTaskSuccess(token, parseTaskId);
        return documentId;
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
            JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).at("/data/records");
            if (records.isArray() && records.size() > 0) {
                return records.get(0).path("id").asLong();
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Task was not created in time. type=" + taskType);
    }

    private void waitForTaskSuccess(String token, Long taskId) throws Exception {
        for (int i = 0; i < 40; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/admin/parse-tasks/{id}", taskId)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
            String status = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).at("/data/status").asText();
            if ("SUCCESS".equals(status)) {
                return;
            }
            if ("FAILED".equals(status)) {
                throw new AssertionError("Task failed unexpectedly");
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Task did not finish in time");
    }

    private void waitForDocumentIndexSuccess(String token, Long documentId) throws Exception {
        for (int i = 0; i < 40; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/admin/documents/{id}", documentId)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
            String indexStatus = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).at("/data/indexStatus").asText();
            if ("SUCCESS".equals(indexStatus)) {
                return;
            }
            if ("FAILED".equals(indexStatus)) {
                throw new AssertionError("Document index failed unexpectedly");
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Document index did not finish in time");
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).at("/data");
    }

    private Long readLong(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).at(pointer).asLong();
    }

    private String bearerToken(String token) {
        return "Bearer " + token;
    }
}
