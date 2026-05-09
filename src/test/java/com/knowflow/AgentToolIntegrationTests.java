package com.knowflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentToolIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldExposeAndExecuteAgentToolsForKnowledgeAndTicketFlow() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        Long knowledgeBaseId = createKnowledgeBase(token, "Agent Tool KB", "Knowledge base used by agent tool tests");

        MvcResult toolsResult = mockMvc.perform(get("/api/v1/app/agent/tools")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode tools = objectMapper.readTree(toolsResult.getResponse().getContentAsString()).at("/data");
        assertThat(tools).anyMatch(node -> "QUERY_TICKET_PROGRESS".equals(node.path("toolCode").asText()));
        assertThat(tools).anyMatch(node -> "CREATE_TICKET_FROM_QA".equals(node.path("toolCode").asText()));
        assertThat(tools).anyMatch(node -> "LIST_KNOWLEDGE_BASES".equals(node.path("toolCode").asText()));

        MvcResult knowledgeBaseToolResult = executeTool(token, """
                {
                  "toolCode": "LIST_KNOWLEDGE_BASES",
                  "arguments": {}
                }
                """);
        JsonNode knowledgeBaseResult = objectMapper.readTree(knowledgeBaseToolResult.getResponse().getContentAsString()).at("/data/result");
        assertThat(knowledgeBaseResult).anyMatch(node -> knowledgeBaseId.equals(node.path("id").asLong())
                && "Agent Tool KB".equals(node.path("kbName").asText()));

        Long sessionId = createQaSession(token, knowledgeBaseId);
        Long qaMessageId = createQaMessage(token, sessionId);

        MvcResult createTicketResult = executeTool(token, """
                {
                  "toolCode": "CREATE_TICKET_FROM_QA",
                  "arguments": {
                    "qaMessageId": %d,
                    "title": "Agent handoff ticket",
                    "content": "The agent should create a support ticket from this low-confidence answer.",
                    "priority": "HIGH"
                  }
                }
                """.formatted(qaMessageId));
        JsonNode createdTicket = objectMapper.readTree(createTicketResult.getResponse().getContentAsString()).at("/data/result");
        Long ticketId = createdTicket.path("id").asLong();
        assertThat(ticketId).isPositive();
        assertThat(createdTicket.path("ticketNo").asText()).isNotBlank();
        assertThat(createdTicket.path("slaPolicy").asText()).isEqualTo("P2-8H");
        assertThat(createdTicket.path("slaStatus").asText()).isEqualTo("ON_TRACK");

        MvcResult queryTicketResult = executeTool(token, """
                {
                  "toolCode": "QUERY_TICKET_PROGRESS",
                  "arguments": {
                    "ticketId": %d
                  }
                }
                """.formatted(ticketId));
        JsonNode queriedTicket = objectMapper.readTree(queryTicketResult.getResponse().getContentAsString()).at("/data/result");
        assertThat(queriedTicket.path("id").asLong()).isEqualTo(ticketId);
        assertThat(queriedTicket.path("slaDueAt").asText()).isNotBlank();
        assertThat(queriedTicket.path("slaRemainingMinutes").asLong()).isPositive();

        resolveTicket(token, ticketId, "Use the agent tool handoff workflow, verify the source answer, and publish the resulting FAQ after review.");
        MvcResult suggestDraftResult = executeTool(token, """
                {
                  "toolCode": "SUGGEST_KNOWLEDGE_DRAFT_FROM_TICKET",
                  "arguments": {
                    "ticketId": %d,
                    "knowledgeBaseId": %d
                  }
                }
                """.formatted(ticketId, knowledgeBaseId));
        JsonNode suggestion = objectMapper.readTree(suggestDraftResult.getResponse().getContentAsString()).at("/data/result");
        assertThat(suggestion.path("ticketId").asLong()).isEqualTo(ticketId);
        assertThat(suggestion.path("knowledgeBaseId").asLong()).isEqualTo(knowledgeBaseId);
        assertThat(suggestion.path("draftType").asText()).isEqualTo("FAQ");
        assertThat(suggestion.path("title").asText()).contains("Agent handoff ticket");
        assertThat(suggestion.path("answerText").asText()).contains("agent tool handoff workflow");
        assertThat(suggestion.path("confidence").asDouble()).isGreaterThan(0.7);
        assertThat(suggestion.path("createDraftArguments").path("ticketId").asLong()).isEqualTo(ticketId);
    }

    private MvcResult executeTool(String token, String requestJson) throws Exception {
        return mockMvc.perform(post("/api/v1/app/agent/tools/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true))
                .andReturn();
    }

    private Long createKnowledgeBase(String token, String kbName, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kbName": "%s",
                                  "description": "%s"
                                }
                                """.formatted(kbName, description)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readLong(result, "/data/id");
    }

    private Long createQaSession(String token, Long knowledgeBaseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/app/qa/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": %d,
                                  "sessionTitle": "Agent tool test session"
                                }
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readLong(result, "/data/id");
    }

    private Long createQaMessage(String token, Long sessionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/app/qa/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "question": "How should an agent create a ticket from an unresolved question?"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readLong(result, "/data/id");
    }

    private void resolveTicket(String token, Long ticketId, String solution) throws Exception {
        mockMvc.perform(post("/api/v1/admin/tickets/{id}/resolve", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "solution": "%s"
                                }
                                """.formatted(solution)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    }

    private Long readLong(MvcResult result, String pointer) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer).asLong();
    }

    private String bearerToken(String token) {
        return "Bearer " + token;
    }
}
