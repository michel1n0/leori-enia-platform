package com.leori.enia.initiative.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leori.enia.LeoriEniaApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LeoriEniaApplication.class)
@AutoConfigureMockMvc
class AIInitiativeApproveIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void conditional_approval_preserves_high_risk_and_returns_the_saved_etag() throws Exception {
        String path = createDraft();
        var initial = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        String id = json.readTree(initial.getResponse().getContentAsString()).get("id").asText();
        String initialTag = initial.getResponse().getHeader("ETag");
        assertEquals(tag(id, 0), initialTag);

        String submittedTag = mvc.perform(post(path + "/submit").header("If-Match", initialTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getHeader("ETag");
        assertEquals(tag(id, 1), submittedTag);

        String assessmentTag = mvc.perform(post(path + "/assessment/start").header("If-Match", submittedTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"))
                .andReturn().getResponse().getHeader("ETag");
        assertEquals(tag(id, 2), assessmentTag);

        String preApprovalTag = mvc.perform(post(path + "/risk-assessment").header("If-Match", assessmentTag)
                        .contentType("application/json").content("{\"riskLevel\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RISK_ASSESSED"))
                .andReturn().getResponse().getHeader("ETag");
        assertEquals(tag(id, 3), preApprovalTag);

        var approval = mvc.perform(post(path + "/approve").header("If-Match", preApprovalTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.preliminaryRisk").value("HIGH"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.domainEvents").doesNotExist())
                .andReturn();
        String approvedTag = approval.getResponse().getHeader("ETag");
        assertNotNull(approvedTag);
        assertNotEquals(preApprovalTag, approvedTag);
        assertEquals(tag(id, 4), approvedTag);
        JsonNode approved = json.readTree(approval.getResponse().getContentAsString());
        assertEquals(9, approved.size());
        var afterGet = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(approvedTag, afterGet.getResponse().getHeader("ETag"));
        assertEquals(approved, json.readTree(afterGet.getResponse().getContentAsString()));

        mvc.perform(post(path + "/approve").header("If-Match", preApprovalTag))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_REVISION_MISMATCH"));
        mvc.perform(post(path + "/approve").header("If-Match", approvedTag))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_TRANSITION"));

        var afterFailures = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(approvedTag, afterFailures.getResponse().getHeader("ETag"));
        assertEquals(approved, json.readTree(afterFailures.getResponse().getContentAsString()));
    }

    @Test
    void header_and_resource_errors_reuse_existing_contracts() throws Exception {
        String path = createDraft();
        var before = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        String currentTag = before.getResponse().getHeader("ETag");

        mvc.perform(post(path + "/approve"))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.code").value("IF_MATCH_REQUIRED"));
        mvc.perform(post(path + "/approve").header("If-Match", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));

        String otherPath = createDraft();
        String otherTag = mvc.perform(get(otherPath)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(post(path + "/approve").header("If-Match", otherTag))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_REVISION_MISMATCH"));

        String missingId = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/ai-initiatives/" + missingId + "/approve")
                        .header("If-Match", tag(missingId, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_NOT_FOUND"));
        mvc.perform(post("/api/v1/ai-initiatives/invalid/approve").header("If-Match", currentTag))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_ID"));
        mvc.perform(post(path + "/approve").header("If-Match", currentTag))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_TRANSITION"));

        var after = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(currentTag, after.getResponse().getHeader("ETag"));
        assertEquals(json.readTree(before.getResponse().getContentAsString()),
                json.readTree(after.getResponse().getContentAsString()));
    }

    private String createDraft() throws Exception {
        var created = mvc.perform(post("/api/v1/ai-initiatives")
                        .contentType("application/json").content("""
                                {
                                  "organizationId": "%s",
                                  "name": "Conditional approval",
                                  "description": "HTTP approval preconditions",
                                  "usesPersonalData": true,
                                  "impactsRights": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        assertNotNull(location);
        return location;
    }

    private static String tag(String id, long revision) {
        return "\"ai-initiative:" + id + ":" + revision + "\"";
    }
}
