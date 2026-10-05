package com.leori.enia.risk.infrastructure.web;

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
import java.util.List;
import java.util.Map;
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
class RiskAssessmentGetIntegrationTest {

    private static final Instant SYSTEM_CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");
    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00Z");
    private static final UUID ORGANIZATION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

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
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void gets_existing_risk_assessment_with_exact_public_response_without_mutating_persistence() throws Exception {
        UUID systemId = seedSystem();
        UUID assessmentId = seedRiskAssessment(systemId);
        int assessmentsBefore = assessmentRowCount();
        int findingsBefore = findingRowCount();

        var result = mvc.perform(get("/api/v1/risk-assessments/{id}", assessmentId))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertAll(
                () -> assertEquals(6, body.size()),
                () -> assertEquals(assessmentId.toString(), body.get("id").asText()),
                () -> assertEquals(systemId.toString(), body.get("systemId").asText()),
                () -> assertEquals("Governance approval", body.get("purpose").asText()),
                () -> assertEquals("Public sector deployment", body.get("deploymentContext").asText()),
                () -> assertEquals(3, body.get("findings").size()),
                () -> assertFinding(body.get("findings").get(0), "Bias risk", "MEDIUM", "HIGH"),
                () -> assertFinding(body.get("findings").get(1), "Privacy risk", "LOW", "MEDIUM"),
                () -> assertFinding(body.get("findings").get(2), "Bias risk", "MEDIUM", "HIGH"),
                () -> assertEquals(ASSESSED_AT.toString(), body.get("assessedAt").asText()),
                () -> assertNoInternalFields(body)
        );
        assertEquals(assessmentsBefore, assessmentRowCount());
        assertEquals(findingsBefore, findingRowCount());
        assertPersistedFindings(assessmentId, List.of(
                expectedFinding("Bias risk", "MEDIUM", "HIGH"),
                expectedFinding("Privacy risk", "LOW", "MEDIUM"),
                expectedFinding("Bias risk", "MEDIUM", "HIGH")
        ));
    }

    @Test
    void missing_valid_uuid_returns_not_found() throws Exception {
        mvc.perform(get("/api/v1/risk-assessments/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RISK_ASSESSMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Risk assessment not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void invalid_uuid_returns_bad_request() throws Exception {
        mvc.perform(get("/api/v1/risk-assessments/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RISK_ASSESSMENT_ID"))
                .andExpect(jsonPath("$.message").value("Invalid risk assessment id"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private UUID seedSystem() {
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'APPROVED', 'HIGH', false, false, ?)
                """, initiativeId, ORGANIZATION_ID, Timestamp.from(SYSTEM_CREATED_AT));
        UUID systemId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'AI System', 'System description', 'REGISTERED', ?)
                """, systemId, ORGANIZATION_ID, initiativeId, Timestamp.from(SYSTEM_CREATED_AT));
        return systemId;
    }

    private UUID seedRiskAssessment(UUID systemId) {
        UUID assessmentId = UUID.randomUUID();
        jdbc.update("""
                insert into risk_assessments (id, system_id, purpose, deployment_context, assessed_at)
                values (?, ?, 'Governance approval', 'Public sector deployment', ?)
                """, assessmentId, systemId, Timestamp.from(ASSESSED_AT));
        jdbc.update("""
                insert into risk_assessment_findings
                    (risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, 0, 'Bias risk', 'MEDIUM', 'HIGH')
                """, assessmentId);
        jdbc.update("""
                insert into risk_assessment_findings
                    (risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, 1, 'Privacy risk', 'LOW', 'MEDIUM')
                """, assessmentId);
        jdbc.update("""
                insert into risk_assessment_findings
                    (risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, 2, 'Bias risk', 'MEDIUM', 'HIGH')
                """, assessmentId);
        return assessmentId;
    }

    private int assessmentRowCount() {
        return jdbc.queryForObject("select count(*) from risk_assessments", Integer.class);
    }

    private int findingRowCount() {
        return jdbc.queryForObject("select count(*) from risk_assessment_findings", Integer.class);
    }

    private void assertPersistedFindings(UUID assessmentId, List<Map<String, String>> expected) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select description, likelihood, impact_magnitude
                from risk_assessment_findings
                where risk_assessment_id = ?
                order by position
                """, assessmentId);
        assertEquals(expected.size(), rows.size());
        for (int index = 0; index < expected.size(); index++) {
            Map<String, String> finding = expected.get(index);
            Map<String, Object> row = rows.get(index);
            assertEquals(finding.get("description"), row.get("description"));
            assertEquals(finding.get("likelihood"), row.get("likelihood"));
            assertEquals(finding.get("impactMagnitude"), row.get("impact_magnitude"));
        }
    }

    private static Map<String, String> expectedFinding(String description, String likelihood, String impactMagnitude) {
        return Map.of(
                "description", description,
                "likelihood", likelihood,
                "impactMagnitude", impactMagnitude
        );
    }

    private static void assertFinding(JsonNode node, String description, String likelihood, String impactMagnitude) {
        assertEquals(description, node.get("description").asText());
        assertEquals(likelihood, node.get("likelihood").asText());
        assertEquals(impactMagnitude, node.get("impactMagnitude").asText());
    }

    private static void assertNoInternalFields(JsonNode body) {
        assertAll(
                () -> assertTrue(body.get("id").isTextual()),
                () -> assertTrue(body.get("systemId").isTextual()),
                () -> assertTrue(body.get("purpose").isTextual()),
                () -> assertTrue(body.get("deploymentContext").isTextual()),
                () -> assertTrue(body.get("findings").isArray()),
                () -> assertTrue(body.get("assessedAt").isTextual()),
                () -> assertEquals(6, body.size()),
                () -> assertTrue(!body.has("domainEvents")),
                () -> assertTrue(!body.has("version")),
                () -> assertTrue(!body.has("revision"))
        );
    }
}
