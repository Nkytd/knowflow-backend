package com.knowflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.knowledge.entity.KnowledgeBaseEntity;
import com.knowflow.knowledge.mapper.KnowledgeBaseMapper;
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
class SecurityBoundaryIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Test
    void shouldRejectAnonymousApiAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/knowledge-bases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(get("/api/v1/app/knowledge-bases/options"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void shouldRejectRoleEscalationAcrossPlatformAndTenantBoundaries() throws Exception {
        String tenantAdminToken = loginAndGetToken("tenant.admin", "Tenant@123");
        String knowledgeOperatorToken = loginAndGetToken("knowledge.operator", "Tenant@123");

        mockMvc.perform(get("/api/v1/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tenantAdminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/v1/admin/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(knowledgeOperatorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tenantAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "tenant.super.role.denied",
                                  "realName": "Tenant Super Role Denied",
                                  "password": "Tenant@123",
                                  "email": "tenant.super.role.denied@knowflow.local",
                                  "phone": "13900000088",
                                  "roleIds": [1]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void shouldHideCrossTenantKnowledgeBaseData() throws Exception {
        String tenantAdminToken = loginAndGetToken("tenant.admin", "Tenant@123");
        KnowledgeBaseEntity otherTenantKnowledgeBase = createOtherTenantKnowledgeBase();

        mockMvc.perform(get("/api/v1/admin/knowledge-bases/{id}", otherTenantKnowledgeBase.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tenantAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401));

        MvcResult pageResult = mockMvc.perform(get("/api/v1/admin/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tenantAdminToken))
                        .param("keyword", otherTenantKnowledgeBase.getKbName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode records = objectMapper.readTree(pageResult.getResponse().getContentAsString()).at("/data/records");
        assertThat(records).isEmpty();
    }

    private KnowledgeBaseEntity createOtherTenantKnowledgeBase() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setTenantId(2L);
        entity.setKbCode(CodeGenerator.knowledgeBaseCode());
        entity.setKbName("Other Tenant Security Boundary KB " + System.nanoTime());
        entity.setDescription("This knowledge base must not be visible to tenant 1 users.");
        entity.setStatus("ENABLED");
        entity.setDocCount(0);
        knowledgeBaseMapper.insert(entity);
        return entity;
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

    private String bearerToken(String token) {
        return "Bearer " + token;
    }
}
