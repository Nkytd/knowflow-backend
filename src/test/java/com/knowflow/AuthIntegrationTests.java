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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldLoginAsTenantAdminAndReadCurrentUser() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("tenant.admin"))
                .andExpect(jsonPath("$.data.tenantId").value(1));
    }

    @Test
    void shouldAllowTenantAdminToListRolesAndCreateUser() throws Exception {
        String token = loginAndGetToken("tenant.admin", "Tenant@123");

        MvcResult roleResult = mockMvc.perform(get("/api/v1/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].roleCode").exists())
                .andReturn();

        JsonNode roleRoot = objectMapper.readTree(roleResult.getResponse().getContentAsString());
        Long tenantAdminRoleId = roleRoot.at("/data/0/id").asLong();

        String createUserJson = """
                {
                  "username": "tenant.user01",
                  "realName": "Tenant User 01",
                  "password": "User@123",
                  "email": "tenant.user01@knowflow.local",
                  "phone": "13100000001",
                  "roleIds": [%d]
                }
                """.formatted(tenantAdminRoleId);

        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("tenant.user01"))
                .andExpect(jsonPath("$.data.roleCodes[0]").exists());
    }

    @Test
    void shouldServeConsolePagesWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/dashboard.html"));

        mockMvc.perform(get("/workbench"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/workbench.html"));

        mockMvc.perform(get("/assistant"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/workbench.html"));

        mockMvc.perform(get("/console/workbench.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/console/workbench-user.css")));

        mockMvc.perform(get("/console/dashboard.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"dashboardApp\"")));

        mockMvc.perform(get("/admin/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/knowledge-bases.html"));

        mockMvc.perform(get("/console/knowledge-bases.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/console/knowledge-bases.js")));

        mockMvc.perform(get("/admin/documents"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/documents.html"));

        mockMvc.perform(get("/console/documents.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/console/documents.js")));

        mockMvc.perform(get("/admin/parse-tasks"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/parse-tasks.html"));

        mockMvc.perform(get("/console/parse-tasks.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/console/parse-tasks.js")));

        mockMvc.perform(get("/admin/tickets"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/tickets.html"));

        mockMvc.perform(get("/console/tickets.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/console/tickets.js")));

        mockMvc.perform(get("/admin/knowledge-drafts"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/knowledge-drafts.html"));

        mockMvc.perform(get("/console/knowledge-drafts.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/console/knowledge-drafts.js")));

        mockMvc.perform(get("/admin/qa-records"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/qa-records.html"));

        mockMvc.perform(get("/console/qa-records.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/console/qa-records.js")));

        mockMvc.perform(get("/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/audit-logs.html"));

        mockMvc.perform(get("/console/audit-logs.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/console/audit-logs.js")));

        mockMvc.perform(get("/admin/ops-health"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/ops-health.html"));

        mockMvc.perform(get("/console/ops-health.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/console/ops-health.js")));
        mockMvc.perform(get("/admin/dead-letters"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/dead-letters.html"));

        mockMvc.perform(get("/console/dead-letters.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/console/dead-letters.js")));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String loginJson = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andReturn();

        JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return root.at("/data/token").asText();
    }
}


