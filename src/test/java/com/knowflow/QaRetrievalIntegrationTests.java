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
class QaRetrievalIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldAnswerQuestionFromIndexedKnowledge() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Retrieval QA KB", "Retrieval quality integration test");

        String content = "\uFEFF" + """
                VPN login troubleshooting guide:
                1. Restart the desktop VPN client.
                2. Check whether local DNS can resolve the company gateway domain.
                3. Re-import the latest security certificate if the client reports a trust error.
                """;
        Long documentId = uploadDocumentAndWaitForParse(token, knowledgeBaseId, "vpn-guide.txt", content);
        waitForDocumentIndexSuccess(token, documentId);

        Long sessionId = createSession(token, knowledgeBaseId, "VPN Session");
        MvcResult askResult = askQuestion(token, sessionId, "How do I re-import the latest security certificate for VPN login?");

        JsonNode data = waitForQaMessageFinal(token, readLong(askResult, "/data/id"));
        assertThat(data.path("answerStatus").asText()).isEqualTo("SUCCESS");
        assertThat(data.path("sourceCount").asInt()).isGreaterThan(0);
        assertThat(data.path("needHumanHandoff").asBoolean()).isFalse();
        assertThat(data.path("answerText").asText()).containsIgnoringCase("security certificate");
        assertThat(data.path("answerText").asText()).contains("vpn-guide.txt");
        assertThat(data.path("answerText").asText()).doesNotContain("\uFEFF");

        JsonNode sources = data.path("sources");
        assertThat(sources.isArray()).isTrue();
        assertThat(sources).isNotEmpty();
        assertThat(sources.get(0).path("documentName").asText()).isEqualTo("vpn-guide.txt");
        assertThat(sources.get(0).path("recallStrategy").asText()).isNotBlank();
        assertThat(sources.get(0).path("recallScore").asDouble()).isGreaterThan(0.20D);
        assertThat(sources.get(0).path("snippetText").asText()).startsWith("VPN login troubleshooting guide:");
    }

    @Test
    void shouldRetrieveChineseConceptWhenQuestionHasQuestionPrefix() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Chinese Concept KB", "Chinese concept retrieval test");

        String worldModel = "\u4E16\u754C\u6A21\u578B";
        String content = worldModel + "\u662F\u4E00\u79CD\u8BA9\u667A\u80FD\u4F53\u5728\u5185\u90E8\u8868\u793A\u5916\u90E8\u73AF\u5883\u52A8\u6001\u89C4\u5F8B\u7684\u6A21\u578B\u3002"
                + "\u5B83\u53EF\u4EE5\u5E2E\u52A9\u667A\u80FD\u4F53\u9884\u6D4B\u672A\u6765\u72B6\u6001\u3001\u8BC4\u4F30\u52A8\u4F5C\u540E\u679C\uFF0C\u5E76\u5728\u4E0D\u76F4\u63A5\u4E0E\u771F\u5B9E\u73AF\u5883\u4EA4\u4E92\u7684\u60C5\u51B5\u4E0B\u8FDB\u884C\u89C4\u5212\u3002";
        Long documentId = uploadDocumentAndWaitForParse(token, knowledgeBaseId, "world-model.txt", content);
        waitForDocumentIndexSuccess(token, documentId);

        Long sessionId = createSession(token, knowledgeBaseId, "World Model Session");
        MvcResult askResult = askQuestion(token, sessionId, "\u4EC0\u4E48\u662F" + worldModel);

        JsonNode data = waitForQaMessageFinal(token, readLong(askResult, "/data/id"));
        assertThat(data.path("answerStatus").asText()).isEqualTo("SUCCESS");
        assertThat(data.path("sourceCount").asInt()).isGreaterThan(0);
        assertThat(data.path("needHumanHandoff").asBoolean()).isFalse();
        assertThat(data.path("answerText").asText()).contains(worldModel);
        assertThat(data.path("sources").get(0).path("documentName").asText()).isEqualTo("world-model.txt");
        assertThat(data.path("sources").get(0).path("recallScore").asDouble()).isGreaterThan(0.20D);

        Long messageId = data.path("id").asLong();
        MvcResult debugResult = mockMvc.perform(get("/api/v1/admin/qa-records/{id}/retrieval-debug", messageId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.qaMessageId").value(messageId))
                .andExpect(jsonPath("$.data.queryVariants").isArray())
                .andExpect(jsonPath("$.data.chunks[0].documentName").value("world-model.txt"))
                .andExpect(jsonPath("$.data.chunks[0].lexicalScore").exists())
                .andExpect(jsonPath("$.data.chunks[0].vectorScore").exists())
                .andExpect(jsonPath("$.data.chunks[0].recallScore").exists())
                .andExpect(jsonPath("$.data.chunks[0].recallStrategy").exists())
                .andReturn();
        JsonNode debugData = readData(debugResult);
        assertThat(debugData.path("queryVariants")).isNotEmpty();
        assertThat(debugData.path("queryVariants").toString()).contains(worldModel);
    }

    @Test
    void shouldHandoffWhenQuestionIsOutOfKnowledgeScope() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "No Hit KB", "Out of scope QA integration test");

        String content = "The office Wi-Fi onboarding guide only covers SSID setup, password rotation, and captive portal login.";
        Long documentId = uploadDocumentAndWaitForParse(token, knowledgeBaseId, "wifi-guide.txt", content);
        waitForDocumentIndexSuccess(token, documentId);

        Long sessionId = createSession(token, knowledgeBaseId, "Finance Session");
        MvcResult askResult = askQuestion(token, sessionId, "How do I submit a Mars travel reimbursement request?");

        JsonNode data = waitForQaMessageFinal(token, readLong(askResult, "/data/id"));
        assertThat(data.path("answerStatus").asText()).isEqualTo("NO_HIT");
        assertThat(data.path("needHumanHandoff").asBoolean()).isTrue();
        assertThat(data.path("sources")).isEmpty();
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

    private Long createSession(String token, Long knowledgeBaseId, String sessionTitle) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/app/qa/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "sessionTitle": "%s"
                                }
                                """.formatted(knowledgeBaseId, sessionTitle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readLong(result, "/data/id");
    }

    private MvcResult askQuestion(String token, Long sessionId, String question) throws Exception {
        return mockMvc.perform(post("/api/v1/app/qa/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "question": "%s"
                                }
                                """.formatted(sessionId, question)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
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


    private JsonNode waitForQaMessageFinal(String token, Long messageId) throws Exception {
        for (int i = 0; i < 50; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/app/qa/messages/{id}", messageId)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
            JsonNode data = readData(result);
            String answerStatus = data.path("answerStatus").asText();
            if (!"GENERATING".equals(answerStatus)) {
                return data;
            }
            Thread.sleep(120L);
        }
        throw new AssertionError("QA message did not finish in time. messageId=" + messageId);
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