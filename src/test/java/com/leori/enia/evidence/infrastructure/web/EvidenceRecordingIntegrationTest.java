package com.leori.enia.evidence.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leori.enia.LeoriEniaApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
        classes = {LeoriEniaApplication.class, EvidenceRecordingIntegrationTest.FixedClockConfiguration.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
class EvidenceRecordingIntegrationTest {

    private static final Instant SYSTEM_CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");
    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00Z");
    private static final Instant CONTROL_CREATED_AT = Instant.parse("2026-10-02T10:15:30Z");
    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T12:30:45Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T13:45:00Z");
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
        jdbc.update("delete from evidence");
        jdbc.update("delete from control_implementations");
        jdbc.update("delete from controls");
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void records_evidence_for_existing_control_implementation() throws Exception {
        UUID implementationId = seedControlImplementation();
        Map<String, Object> request = validRequest(implementationId);
        request.put("description", "  Signed approval memo  ");
        request.put("reference", "  archive://approval.pdf  ");

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());

        assertEquals("/api/v1/evidence/" + id, result.getResponse().getHeader("Location"));
        assertAll(
                () -> assertEquals(5, body.size()),
                () -> assertEquals(implementationId.toString(), body.get("controlImplementationId").asText()),
                () -> assertEquals("Signed approval memo", body.get("description").asText()),
                () -> assertEquals("archive://approval.pdf", body.get("reference").asText()),
                () -> assertEquals(RECORDED_AT.toString(), body.get("recordedAt").asText()),
                () -> assertNoInternalFields(body)
        );
        assertPersistedEvidence(id, implementationId, "Signed approval memo", "archive://approval.pdf");
    }

    @Test
    void returns_201_and_location_header() throws Exception {
        UUID implementationId = seedControlImplementation();

        var result = postRequest(validRequest(implementationId))
                .andExpect(status().isCreated())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith("/api/v1/evidence/"));
        UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void persists_trimmed_description_and_reference() throws Exception {
        UUID implementationId = seedControlImplementation();
        Map<String, Object> request = validRequest(implementationId);
        request.put("description", "  Monitoring report attached.  ");
        request.put("reference", "  evidence-vault:item-456  ");

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());

        assertEquals("Monitoring report attached.", body.get("description").asText());
        assertEquals("evidence-vault:item-456", body.get("reference").asText());
        assertPersistedEvidence(id, implementationId, "Monitoring report attached.", "evidence-vault:item-456");
    }

    @Test
    void rejects_missing_control_implementation() throws Exception {
        postRequest(validRequest(UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTROL_IMPLEMENTATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Control implementation not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, evidenceRowCount());
    }

    @Test
    void rejects_malformed_control_implementation_id_uuid_as_malformed_request() throws Exception {
        UUID implementationId = seedControlImplementation();
        Map<String, Object> request = validRequest(implementationId);
        request.put("controlImplementationId", "not-a-uuid");

        assertMalformedRequest(request);
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejects_validation_errors(String field, Object value) throws Exception {
        UUID implementationId = seedControlImplementation();
        Map<String, Object> request = validRequest(implementationId);
        if (value == Absent.INSTANCE) {
            request.remove(field);
        } else {
            request.put(field, value);
        }

        assertValidationError(request, field);
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("controlImplementationId", Absent.INSTANCE),
                Arguments.of("controlImplementationId", null),
                Arguments.of("description", Absent.INSTANCE),
                Arguments.of("description", null),
                Arguments.of("description", "   "),
                Arguments.of("reference", Absent.INSTANCE),
                Arguments.of("reference", null),
                Arguments.of("reference", "   ")
        );
    }

    @Test
    void rejects_malformed_json_as_malformed_request() throws Exception {
        mvc.perform(post("/api/v1/evidence")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, evidenceRowCount());
    }

    @Test
    void rejects_unknown_json_field_as_malformed_request() throws Exception {
        UUID implementationId = seedControlImplementation();
        Map<String, Object> request = validRequest(implementationId);
        request.put("unexpected", "value");

        assertMalformedRequest(request);
    }

    private void assertValidationError(Map<String, Object> request, String field) throws Exception {
        var result = postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertTrue(body.get("fields").has(field), "Expected validation field: " + field + " in " + body.get("fields"));
        assertEquals(0, evidenceRowCount());
    }

    private void assertMalformedRequest(Map<String, Object> request) throws Exception {
        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, evidenceRowCount());
    }

    private ResultActions postRequest(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/v1/evidence")
                .contentType("application/json")
                .content(json.writeValueAsString(request)));
    }

    private static Map<String, Object> validRequest(UUID controlImplementationId) {
        Map<String, Object> request = new HashMap<>();
        request.put("controlImplementationId", controlImplementationId.toString());
        request.put("description", "Signed approval memo");
        request.put("reference", "archive://approval.pdf");
        return request;
    }

    private UUID seedControlImplementation() {
        UUID systemId = seedSystem();
        UUID assessmentId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        UUID controlId = UUID.randomUUID();
        UUID implementationId = UUID.randomUUID();
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
        jdbc.update("""
                insert into control_implementations
                    (id, control_id, description, implemented_at)
                values (?, ?, 'Evidence package uploaded and reviewed.', ?)
                """, implementationId, controlId, Timestamp.from(IMPLEMENTED_AT));
        return implementationId;
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

    private void assertPersistedEvidence(UUID id, UUID controlImplementationId, String description, String reference) {
        Map<String, Object> row = jdbc.queryForMap("select * from evidence where id = ?", id);
        assertAll(
                () -> assertEquals(controlImplementationId, row.get("control_implementation_id")),
                () -> assertEquals(description, row.get("description")),
                () -> assertEquals(reference, row.get("reference")),
                () -> assertEquals(RECORDED_AT, ((Timestamp) row.get("recorded_at")).toInstant())
        );
    }

    private int evidenceRowCount() {
        return jdbc.queryForObject("select count(*) from evidence", Integer.class);
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
                () -> assertTrue(!body.has("status")),
                () -> assertTrue(!body.has("effectiveness"))
        );
    }

    enum Absent {
        INSTANCE
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock evidenceClock() {
            return Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
        }
    }
}
