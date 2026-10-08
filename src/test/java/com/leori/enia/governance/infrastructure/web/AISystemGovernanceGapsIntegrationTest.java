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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
class AISystemGovernanceGapsIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T10:00:00.123456Z");
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
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void gets_governance_gaps_with_exact_public_fields_and_order() throws Exception {
        UUID systemId = seedSystem();
        UUID assessment = seedAssessment(systemId, CREATED_AT.plusSeconds(1));
        UUID findingA = seedFinding(assessment, 0, "Finding A has no controls", "HIGH", "MEDIUM");
        UUID findingB = seedFinding(assessment, 1, "Finding B has no implementation", "MEDIUM", "HIGH");
        UUID findingC = seedFinding(assessment, 2, "Finding C has implementation without evidence", "LOW", "HIGH");
        UUID controlB = seedControl(assessment, findingB, "Control B1", "Control B1 has no implementation",
                CREATED_AT.plusSeconds(2));
        UUID controlC = seedControl(assessment, findingC, "Control C1", "Control C1 is implemented",
                CREATED_AT.plusSeconds(3));
        UUID implementationC = seedImplementation(controlC, "Implementation C1.1 has no evidence",
                CREATED_AT.plusSeconds(4));

        var result = mvc.perform(get("/api/v1/ai-systems/{id}/governance-gaps", systemId))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        JsonNode finding = body.get("findingsWithoutControls").get(0);
        JsonNode control = body.get("controlsWithoutImplementation").get(0);
        JsonNode implementation = body.get("implementationsWithoutEvidence").get(0);
        assertAll(
                () -> assertEquals(List.of(
                        "aiSystemId",
                        "findingsWithoutControls",
                        "controlsWithoutImplementation",
                        "implementationsWithoutEvidence"), fieldNames(body)),
                () -> assertEquals(systemId.toString(), body.get("aiSystemId").asText()),
                () -> assertEquals(1, body.get("findingsWithoutControls").size()),
                () -> assertEquals(1, body.get("controlsWithoutImplementation").size()),
                () -> assertEquals(1, body.get("implementationsWithoutEvidence").size()),
                () -> assertEquals(List.of("riskAssessmentId", "riskFindingId", "description", "likelihood",
                        "impactMagnitude"), fieldNames(finding)),
                () -> assertEquals(assessment.toString(), finding.get("riskAssessmentId").asText()),
                () -> assertEquals(findingA.toString(), finding.get("riskFindingId").asText()),
                () -> assertEquals("Finding A has no controls", finding.get("description").asText()),
                () -> assertEquals("HIGH", finding.get("likelihood").asText()),
                () -> assertEquals("MEDIUM", finding.get("impactMagnitude").asText()),
                () -> assertEquals(List.of("riskAssessmentId", "riskFindingId", "controlId", "name", "description"),
                        fieldNames(control)),
                () -> assertEquals(assessment.toString(), control.get("riskAssessmentId").asText()),
                () -> assertEquals(findingB.toString(), control.get("riskFindingId").asText()),
                () -> assertEquals(controlB.toString(), control.get("controlId").asText()),
                () -> assertEquals("Control B1", control.get("name").asText()),
                () -> assertEquals("Control B1 has no implementation", control.get("description").asText()),
                () -> assertEquals(List.of("controlId", "controlImplementationId", "description", "implementedAt"),
                        fieldNames(implementation)),
                () -> assertEquals(controlC.toString(), implementation.get("controlId").asText()),
                () -> assertEquals(implementationC.toString(), implementation.get("controlImplementationId").asText()),
                () -> assertEquals("Implementation C1.1 has no evidence", implementation.get("description").asText()),
                () -> assertEquals(CREATED_AT.plusSeconds(4).toString(), implementation.get("implementedAt").asText()),
                () -> assertNoInternalFields(body),
                () -> assertNoInternalFields(finding),
                () -> assertNoInternalFields(control),
                () -> assertNoInternalFields(implementation)
        );
    }

    @Test
    void no_gaps_return_empty_arrays() throws Exception {
        UUID systemId = seedSystem();

        var result = mvc.perform(get("/api/v1/ai-systems/{id}/governance-gaps", systemId))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertAll(
                () -> assertEquals(systemId.toString(), body.get("aiSystemId").asText()),
                () -> assertTrue(body.get("findingsWithoutControls").isArray()),
                () -> assertEquals(0, body.get("findingsWithoutControls").size()),
                () -> assertTrue(body.get("controlsWithoutImplementation").isArray()),
                () -> assertEquals(0, body.get("controlsWithoutImplementation").size()),
                () -> assertTrue(body.get("implementationsWithoutEvidence").isArray()),
                () -> assertEquals(0, body.get("implementationsWithoutEvidence").size())
        );
    }

    @Test
    void missing_system_returns_not_found() throws Exception {
        mvc.perform(get("/api/v1/ai-systems/{id}/governance-gaps", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_SYSTEM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("AI system not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformed_uuid_returns_bad_request() throws Exception {
        mvc.perform(get("/api/v1/ai-systems/not-a-uuid/governance-gaps"))
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

    private UUID seedAssessment(UUID systemId, Instant assessedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into risk_assessments (id, system_id, purpose, deployment_context, assessed_at)
                values (?, ?, 'Governance approval', 'Public sector deployment', ?)
                """, id, systemId, Timestamp.from(assessedAt));
        return id;
    }

    private UUID seedFinding(UUID assessmentId, int position, String description, String likelihood, String impact) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into risk_assessment_findings
                    (id, risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, ?, ?, ?, ?, ?)
                """, id, assessmentId, position, description, likelihood, impact);
        return id;
    }

    private UUID seedControl(UUID assessmentId, UUID findingId, String name, String description, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into controls (id, risk_assessment_id, risk_finding_id, name, description, created_at)
                values (?, ?, ?, ?, ?, ?)
                """, id, assessmentId, findingId, name, description, Timestamp.from(createdAt));
        return id;
    }

    private UUID seedImplementation(UUID controlId, String description, Instant implementedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into control_implementations (id, control_id, description, implemented_at)
                values (?, ?, ?, ?)
                """, id, controlId, description, Timestamp.from(implementedAt));
        return id;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            names.add(iterator.next());
        }
        return names;
    }

    private static void assertNoInternalFields(JsonNode body) {
        assertAll(
                () -> assertTrue(!body.has("domainEvents")),
                () -> assertTrue(!body.has("version")),
                () -> assertTrue(!body.has("revision")),
                () -> assertTrue(!body.has("etag")),
                () -> assertTrue(!body.has("registeredModelCount")),
                () -> assertTrue(!body.has("evidenceCount"))
        );
    }
}
