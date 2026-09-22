package com.leori.enia.initiative.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leori.enia.LeoriEniaApplication;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.leori.enia.initiative.domain.RiskLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

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
class AIInitiativeAssessRiskIntegrationTest {

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
    @Autowired private AIInitiativeRepository repository;
    @Autowired private ObjectMapper json;

    @ParameterizedTest
    @EnumSource(value = RiskLevel.class, names = {"LOW", "MEDIUM", "HIGH"})
    void conditional_risk_assessment_returns_saved_risk_and_matching_etag(RiskLevel risk) throws Exception {
        AIInitiative initiative = draft();
        String path = path(initiative.id());
        String initialTag = mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        assertEquals(tag(initiative.id(), 0), initialTag);
        String submittedTag = mvc.perform(post(path + "/submit").header("If-Match", initialTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getHeader("ETag");
        assertEquals(tag(initiative.id(), 1), submittedTag);
        String preRiskTag = mvc.perform(post(path + "/assessment/start").header("If-Match", submittedTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"))
                .andReturn().getResponse().getHeader("ETag");
        assertEquals(tag(initiative.id(), 2), preRiskTag);

        String body = json.writeValueAsString(java.util.Map.of("riskLevel", risk.name()));
        var assessment = mvc.perform(post(path + "/risk-assessment").header("If-Match", preRiskTag)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RISK_ASSESSED"))
                .andExpect(jsonPath("$.preliminaryRisk").value(risk.name()))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.domainEvents").doesNotExist())
                .andReturn();
        String newTag = assessment.getResponse().getHeader("ETag");
        assertNotNull(newTag);
        assertNotEquals(preRiskTag, newTag);
        assertEquals(tag(initiative.id(), 3), newTag);
        assertEquals(3, repository.findById(initiative.id()).orElseThrow().version());
        JsonNode assessed = json.readTree(assessment.getResponse().getContentAsString());
        assertEquals(9, assessed.size());
        var afterGet = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(newTag, afterGet.getResponse().getHeader("ETag"));
        assertEquals(assessed, json.readTree(afterGet.getResponse().getContentAsString()));

        mvc.perform(post(path + "/risk-assessment").header("If-Match", preRiskTag)
                        .contentType("application/json").content(body))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_REVISION_MISMATCH"));
        mvc.perform(post(path + "/risk-assessment").header("If-Match", newTag)
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_TRANSITION"));
        var afterFailures = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(newTag, afterFailures.getResponse().getHeader("ETag"));
        assertEquals(assessed, json.readTree(afterFailures.getResponse().getContentAsString()));
    }

    @Test
    void header_and_resource_errors_reuse_existing_contracts() throws Exception {
        AIInitiative initiative = draft();
        String path = path(initiative.id());
        String body = "{\"riskLevel\":\"HIGH\"}";
        mvc.perform(post(path + "/risk-assessment").contentType("application/json").content(body))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.code").value("IF_MATCH_REQUIRED"));
        mvc.perform(post(path + "/risk-assessment").header("If-Match", "bad")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));
        AIInitiative other = draft();
        String otherTag = mvc.perform(get(path(other.id()))).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(post(path + "/risk-assessment").header("If-Match", otherTag)
                        .contentType("application/json").content(body))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_REVISION_MISMATCH"));
        AIInitiativeId missing = AIInitiativeId.generate();
        mvc.perform(post(path(missing) + "/risk-assessment").header("If-Match", tag(missing, 0))
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_NOT_FOUND"));
        mvc.perform(post("/api/v1/ai-initiatives/invalid/risk-assessment")
                        .header("If-Match", tag(initiative.id(), 0))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_ID"));
        var afterFailures = mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.preliminaryRisk").value("NOT_ASSESSED")).andReturn();
        assertEquals(tag(initiative.id(), 0), afterFailures.getResponse().getHeader("ETag"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            { | MALFORMED_REQUEST
            {} | VALIDATION_ERROR
            {"riskLevel":null} | VALIDATION_ERROR
            {"riskLevel":"CRITICAL"} | MALFORMED_REQUEST
            {"riskLevel":"NOT_ASSESSED"} | VALIDATION_ERROR
            {"riskLevel":"HIGH","unexpected":true} | MALFORMED_REQUEST
            """)
    void invalid_body_is_rejected_without_mutation(String body, String code) throws Exception {
        AIInitiative initiative = draft();
        String path = path(initiative.id());
        var before = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        String currentTag = before.getResponse().getHeader("ETag");

        mvc.perform(post(path + "/risk-assessment").header("If-Match", currentTag)
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));

        var after = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(currentTag, after.getResponse().getHeader("ETag"));
        assertEquals(json.readTree(before.getResponse().getContentAsString()),
                json.readTree(after.getResponse().getContentAsString()));
    }

    @Test
    void malformed_body_is_rejected_before_stale_etag_evaluation() throws Exception {
        AIInitiative initiative = draft();
        String path = path(initiative.id());
        String staleTag = mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        var submitted = mvc.perform(post(path + "/submit").header("If-Match", staleTag))
                .andExpect(status().isOk()).andReturn();

        mvc.perform(post(path + "/risk-assessment").header("If-Match", staleTag)
                        .contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        var after = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(submitted.getResponse().getHeader("ETag"), after.getResponse().getHeader("ETag"));
        assertEquals(json.readTree(submitted.getResponse().getContentAsString()),
                json.readTree(after.getResponse().getContentAsString()));
    }

    private static String tag(AIInitiativeId id, long revision) {
        return "\"ai-initiative:" + id.value() + ":" + revision + "\"";
    }

    private AIInitiative draft() {
        AIInitiative initiative = AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("Conditional risk assessment")
                .description("HTTP preconditions")
                .usesPersonalData(false)
                .impactsRights(false)
                .createdAt(Instant.parse("2026-09-21T14:00:00Z"))
                .build();
        repository.create(initiative);
        return initiative;
    }

    private static String path(AIInitiativeId id) {
        return "/api/v1/ai-initiatives/" + id.value();
    }
}
