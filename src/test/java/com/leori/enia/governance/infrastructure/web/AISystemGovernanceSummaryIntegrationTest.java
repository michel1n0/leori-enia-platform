package com.leori.enia.governance.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leori.enia.LeoriEniaApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LeoriEniaApplication.class)
@AutoConfigureMockMvc
class AISystemGovernanceSummaryIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-05T14:00:00Z");
    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearRows() {
        jdbc.update("delete from evidence");
        jdbc.update("delete from control_implementations");
        jdbc.update("delete from controls");
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_models");
        jdbc.update("delete from ai_system_datasets");
        jdbc.update("delete from ai_datasets");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void gets_governance_summary_with_exact_metrics() throws Exception {
        UUID systemId = seedSystem();
        seedModel(systemId);
        associateDataset(systemId, seedDataset("Dataset A"));
        associateDataset(systemId, seedDataset("Dataset B"));
        UUID assessment = seedAssessment(systemId);
        UUID controlledFinding = seedFinding(assessment, 0);
        seedFinding(assessment, 1);
        UUID control = seedControl(assessment, controlledFinding);
        UUID firstImplementation = seedImplementation(control);
        UUID secondImplementation = seedImplementation(control);
        seedEvidence(firstImplementation);
        seedEvidence(firstImplementation);

        var result = mvc.perform(get("/api/v1/ai-systems/{id}/governance-summary", systemId))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertAll(
                () -> assertEquals(12, body.size()),
                () -> assertEquals(systemId.toString(), body.get("aiSystemId").asText()),
                () -> assertEquals(1, body.get("riskAssessmentCount").asLong()),
                () -> assertEquals(2, body.get("findingCount").asLong()),
                () -> assertEquals(1, body.get("controlCount").asLong()),
                () -> assertEquals(1, body.get("implementedControlCount").asLong()),
                () -> assertEquals(2, body.get("controlImplementationCount").asLong()),
                () -> assertEquals(2, body.get("evidenceCount").asLong()),
                () -> assertEquals(1, body.get("findingsWithoutControls").asLong()),
                () -> assertEquals(0, body.get("controlsWithoutImplementation").asLong()),
                () -> assertEquals(1, body.get("implementationsWithoutEvidence").asLong()),
                () -> assertEquals(1, body.get("registeredModelCount").asLong()),
                () -> assertEquals(2, body.get("datasetCount").asLong()),
                () -> assertNoInternalFields(body)
        );
        assertEquals(0, jdbc.queryForObject("select count(*) from evidence where control_implementation_id = ?",
                Integer.class, secondImplementation));
    }

    @Test
    void zero_related_data_returns_all_counters_zero() throws Exception {
        UUID systemId = seedSystem();

        var result = mvc.perform(get("/api/v1/ai-systems/{id}/governance-summary", systemId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertAll(
                () -> assertEquals(systemId.toString(), body.get("aiSystemId").asText()),
                () -> assertEquals(0, body.get("riskAssessmentCount").asLong()),
                () -> assertEquals(0, body.get("findingCount").asLong()),
                () -> assertEquals(0, body.get("controlCount").asLong()),
                () -> assertEquals(0, body.get("implementedControlCount").asLong()),
                () -> assertEquals(0, body.get("controlImplementationCount").asLong()),
                () -> assertEquals(0, body.get("evidenceCount").asLong()),
                () -> assertEquals(0, body.get("findingsWithoutControls").asLong()),
                () -> assertEquals(0, body.get("controlsWithoutImplementation").asLong()),
                () -> assertEquals(0, body.get("implementationsWithoutEvidence").asLong()),
                () -> assertEquals(0, body.get("registeredModelCount").asLong()),
                () -> assertEquals(0, body.get("datasetCount").asLong())
        );
    }

    @Test
    void missing_system_returns_not_found() throws Exception {
        mvc.perform(get("/api/v1/ai-systems/{id}/governance-summary", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_SYSTEM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("AI system not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformed_uuid_returns_bad_request() throws Exception {
        mvc.perform(get("/api/v1/ai-systems/not-a-uuid/governance-summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AI_SYSTEM_ID"))
                .andExpect(jsonPath("$.message").value("Invalid AI system id"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private UUID seedSystem() {
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'DRAFT', 'NOT_ASSESSED', false, false, ?)
                """, initiativeId, ORGANIZATION_ID, Timestamp.from(CREATED_AT));
        UUID systemId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'System', 'Desc', 'REGISTERED', ?)
                """, systemId, ORGANIZATION_ID, initiativeId, Timestamp.from(CREATED_AT));
        return systemId;
    }

    private void seedModel(UUID systemId) {
        jdbc.update("""
                insert into ai_models (id, system_id, name, description, provider, created_at)
                values (?, ?, 'Model', 'Description', 'Provider', ?)
                """, UUID.randomUUID(), systemId, Timestamp.from(CREATED_AT));
    }

    private UUID seedDataset(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into ai_datasets (id, name, description, created_at)
                values (?, ?, 'Dataset description', ?)
                """, id, name, Timestamp.from(CREATED_AT));
        return id;
    }

    private void associateDataset(UUID systemId, UUID datasetId) {
        jdbc.update("""
                insert into ai_system_datasets (system_id, dataset_id, associated_at)
                values (?, ?, ?)
                """, systemId, datasetId, Timestamp.from(CREATED_AT));
    }

    private UUID seedAssessment(UUID systemId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into risk_assessments (id, system_id, purpose, deployment_context, assessed_at)
                values (?, ?, 'Governance approval', 'Public sector deployment', ?)
                """, id, systemId, Timestamp.from(CREATED_AT));
        return id;
    }

    private UUID seedFinding(UUID assessmentId, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into risk_assessment_findings
                    (id, risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, ?, ?, 'Bias risk', 'MEDIUM', 'HIGH')
                """, id, assessmentId, position);
        return id;
    }

    private UUID seedControl(UUID assessmentId, UUID findingId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into controls (id, risk_assessment_id, risk_finding_id, name, description, created_at)
                values (?, ?, ?, 'Human review gate', 'Require documented human approval.', ?)
                """, id, assessmentId, findingId, Timestamp.from(CREATED_AT));
        return id;
    }

    private UUID seedImplementation(UUID controlId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into control_implementations (id, control_id, description, implemented_at)
                values (?, ?, 'Evidence package uploaded and reviewed.', ?)
                """, id, controlId, Timestamp.from(CREATED_AT));
        return id;
    }

    private void seedEvidence(UUID implementationId) {
        jdbc.update("""
                insert into evidence (id, control_implementation_id, description, reference, recorded_at)
                values (?, ?, 'Signed approval minutes', 'evidence-vault:item', ?)
                """, UUID.randomUUID(), implementationId, Timestamp.from(CREATED_AT));
    }

    private static void assertNoInternalFields(JsonNode body) {
        assertAll(
                () -> assertTrue(!body.has("domainEvents")),
                () -> assertTrue(!body.has("version")),
                () -> assertTrue(!body.has("revision")),
                () -> assertTrue(!body.has("etag"))
        );
    }
}
