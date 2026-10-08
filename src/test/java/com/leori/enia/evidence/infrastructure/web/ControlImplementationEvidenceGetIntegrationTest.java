package com.leori.enia.evidence.infrastructure.web;

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
class ControlImplementationEvidenceGetIntegrationTest {

    private static final Instant SYSTEM_CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");
    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00Z");
    private static final Instant CONTROL_CREATED_AT = Instant.parse("2026-10-02T10:15:30Z");
    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T12:30:45Z");
    private static final Instant FIRST_RECORDED_AT = Instant.parse("2026-10-04T13:45:00Z");
    private static final Instant SECOND_RECORDED_AT = Instant.parse("2026-10-04T14:45:00Z");
    private static final UUID ORGANIZATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

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
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void lists_evidence_for_control_implementation_with_exact_public_response_in_deterministic_order()
            throws Exception {
        UUID implementationId = seedControlImplementation();
        UUID otherImplementationId = seedControlImplementation();
        UUID firstEvidenceId = UUID.fromString("91000000-0000-0000-0000-000000000001");
        UUID secondEvidenceId = UUID.fromString("91000000-0000-0000-0000-000000000003");
        UUID thirdEvidenceId = UUID.fromString("91000000-0000-0000-0000-000000000002");
        insertEvidence(secondEvidenceId, implementationId, "Second monitoring report", "archive://a2.pdf",
                SECOND_RECORDED_AT);
        insertEvidence(UUID.fromString("91000000-0000-0000-0000-000000000004"), otherImplementationId,
                "Other implementation evidence", "archive://b1.pdf", FIRST_RECORDED_AT);
        insertEvidence(thirdEvidenceId, implementationId, "First monitoring report", "archive://a3.pdf",
                SECOND_RECORDED_AT);
        insertEvidence(firstEvidenceId, implementationId, "Signed approval memo", "archive://a1.pdf",
                FIRST_RECORDED_AT);

        var result = mvc.perform(get("/api/v1/control-implementations/{controlImplementationId}/evidence",
                        implementationId))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(header().doesNotExist("Location"))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertAll(
                () -> assertTrue(body.isArray()),
                () -> assertEquals(3, body.size()),
                () -> assertEvidence(body.get(0), firstEvidenceId, implementationId,
                        "Signed approval memo", "archive://a1.pdf", FIRST_RECORDED_AT),
                () -> assertEvidence(body.get(1), thirdEvidenceId, implementationId,
                        "First monitoring report", "archive://a3.pdf", SECOND_RECORDED_AT),
                () -> assertEvidence(body.get(2), secondEvidenceId, implementationId,
                        "Second monitoring report", "archive://a2.pdf", SECOND_RECORDED_AT)
        );
    }

    @Test
    void existing_control_implementation_with_zero_evidence_returns_empty_array() throws Exception {
        UUID implementationId = seedControlImplementation();

        var result = mvc.perform(get("/api/v1/control-implementations/{controlImplementationId}/evidence",
                        implementationId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertTrue(body.isArray());
        assertEquals(0, body.size());
    }

    @Test
    void missing_control_implementation_returns_not_found() throws Exception {
        mvc.perform(get("/api/v1/control-implementations/{controlImplementationId}/evidence", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTROL_IMPLEMENTATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Control implementation not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformed_control_implementation_uuid_returns_bad_request() throws Exception {
        mvc.perform(get("/api/v1/control-implementations/not-a-uuid/evidence"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONTROL_IMPLEMENTATION_ID"))
                .andExpect(jsonPath("$.message").value("Invalid control implementation id"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private void insertEvidence(
            UUID evidenceId,
            UUID implementationId,
            String description,
            String reference,
            Instant recordedAt
    ) {
        jdbc.update("""
                insert into evidence
                    (id, control_implementation_id, description, reference, recorded_at)
                values (?, ?, ?, ?, ?)
                """, evidenceId, implementationId, description, reference, Timestamp.from(recordedAt));
    }

    private UUID seedControlImplementation() {
        UUID controlId = seedControl();
        UUID implementationId = UUID.randomUUID();
        jdbc.update("""
                insert into control_implementations
                    (id, control_id, description, implemented_at)
                values (?, ?, 'Evidence package attached.', ?)
                """, implementationId, controlId, Timestamp.from(IMPLEMENTED_AT));
        return implementationId;
    }

    private UUID seedControl() {
        UUID systemId = seedSystem();
        UUID assessmentId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        UUID controlId = UUID.randomUUID();
        jdbc.update("""
                insert into risk_assessments
                    (id, system_id, purpose, deployment_context, assessed_at)
                values (?, ?, 'Governance approval', 'Public sector deployment', ?)
                """, assessmentId, systemId, Timestamp.from(ASSESSED_AT));
        jdbc.update("""
                insert into risk_assessment_findings
                    (id, risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, ?, 0, 'Bias risk', 'MEDIUM', 'HIGH')
                """, findingId, assessmentId);
        jdbc.update("""
                insert into controls
                    (id, risk_assessment_id, risk_finding_id, name, description, created_at)
                values (?, ?, ?, 'Human review gate', 'Require documented human approval before deployment.', ?)
                """, controlId, assessmentId, findingId, Timestamp.from(CONTROL_CREATED_AT));
        return controlId;
    }

    private UUID seedSystem() {
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at, version, rejection_reason)
                values (?, ?, 'Source initiative', 'Source description', 'APPROVED', 'HIGH',
                        false, false, ?, 0, null)
                """, initiativeId, ORGANIZATION_ID, Timestamp.from(SYSTEM_CREATED_AT));
        UUID systemId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'AI System', 'System description', 'REGISTERED', ?)
                """, systemId, ORGANIZATION_ID, initiativeId, Timestamp.from(SYSTEM_CREATED_AT));
        return systemId;
    }

    private static void assertEvidence(
            JsonNode node,
            UUID evidenceId,
            UUID implementationId,
            String description,
            String reference,
            Instant recordedAt
    ) {
        assertAll(
                () -> assertEquals(5, node.size()),
                () -> assertEquals(evidenceId.toString(), node.get("id").asText()),
                () -> assertEquals(implementationId.toString(), node.get("controlImplementationId").asText()),
                () -> assertEquals(description, node.get("description").asText()),
                () -> assertEquals(reference, node.get("reference").asText()),
                () -> assertEquals(recordedAt.toString(), node.get("recordedAt").asText()),
                () -> assertNoInternalFields(node)
        );
    }

    private static void assertNoInternalFields(JsonNode body) {
        assertAll(
                () -> assertTrue(body.get("id").isTextual()),
                () -> assertTrue(body.get("controlImplementationId").isTextual()),
                () -> assertTrue(body.get("description").isTextual()),
                () -> assertTrue(body.get("reference").isTextual()),
                () -> assertTrue(body.get("recordedAt").isTextual()),
                () -> assertTrue(!body.has("domainEvents")),
                () -> assertTrue(!body.has("version")),
                () -> assertTrue(!body.has("revision")),
                () -> assertTrue(!body.has("status"))
        );
    }
}
