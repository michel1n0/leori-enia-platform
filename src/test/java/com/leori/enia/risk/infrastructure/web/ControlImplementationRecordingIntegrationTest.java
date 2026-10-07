package com.leori.enia.risk.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leori.enia.LeoriEniaApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        classes = {LeoriEniaApplication.class, ControlImplementationRecordingIntegrationTest.FixedClockConfiguration.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
class ControlImplementationRecordingIntegrationTest {

    private static final Instant SYSTEM_CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");
    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00Z");
    private static final Instant CONTROL_CREATED_AT = Instant.parse("2026-10-02T10:15:30Z");
    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T12:30:45Z");
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
        jdbc.update("delete from control_implementations");
        jdbc.update("delete from controls");
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void records_control_implementation_for_existing_control() throws Exception {
        UUID controlId = seedControl();
        Map<String, Object> request = validRequest(controlId);
        request.put("description", "  Operating procedure published and evidence attached.  ");

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());

        assertEquals("/api/v1/control-implementations/" + id, result.getResponse().getHeader("Location"));
        assertAll(
                () -> assertEquals(4, body.size()),
                () -> assertEquals(controlId.toString(), body.get("controlId").asText()),
                () -> assertEquals("Operating procedure published and evidence attached.", body.get("description").asText()),
                () -> assertEquals(IMPLEMENTED_AT.toString(), body.get("implementedAt").asText()),
                () -> assertNoInternalFields(body)
        );
        assertPersistedImplementation(
                id,
                controlId,
                "Operating procedure published and evidence attached."
        );
    }

    @Test
    void returns_201_and_location_header() throws Exception {
        UUID controlId = seedControl();

        var result = postRequest(validRequest(controlId))
                .andExpect(status().isCreated())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith("/api/v1/control-implementations/"));
        UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void persists_trimmed_description() throws Exception {
        UUID controlId = seedControl();
        Map<String, Object> request = validRequest(controlId);
        request.put("description", "  Control owner completed rollout.  ");

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());

        assertEquals("Control owner completed rollout.", body.get("description").asText());
        assertPersistedImplementation(id, controlId, "Control owner completed rollout.");
    }

    @Test
    void rejects_missing_control() throws Exception {
        postRequest(validRequest(UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTROL_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Control not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, implementationRowCount());
    }

    @Test
    void rejects_blank_description() throws Exception {
        UUID controlId = seedControl();
        Map<String, Object> request = validRequest(controlId);
        request.put("description", "   ");

        assertValidationError(request, "description");
    }

    @Test
    void rejects_missing_description() throws Exception {
        UUID controlId = seedControl();
        Map<String, Object> request = validRequest(controlId);
        request.remove("description");

        assertValidationError(request, "description");
    }

    @Test
    void rejects_missing_control_id() throws Exception {
        UUID controlId = seedControl();
        Map<String, Object> request = validRequest(controlId);
        request.remove("controlId");

        assertValidationError(request, "controlId");
    }

    @Test
    void rejects_invalid_control_id_uuid_as_malformed_request() throws Exception {
        UUID controlId = seedControl();
        Map<String, Object> request = validRequest(controlId);
        request.put("controlId", "not-a-uuid");

        assertMalformedRequest(request);
    }

    @Test
    void rejects_malformed_json_as_malformed_request() throws Exception {
        mvc.perform(post("/api/v1/control-implementations")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, implementationRowCount());
    }

    @Test
    void rejects_unknown_json_field_as_malformed_request() throws Exception {
        UUID controlId = seedControl();
        Map<String, Object> request = validRequest(controlId);
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
        assertEquals(0, implementationRowCount());
    }

    private void assertMalformedRequest(Map<String, Object> request) throws Exception {
        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, implementationRowCount());
    }

    private ResultActions postRequest(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/v1/control-implementations")
                .contentType("application/json")
                .content(json.writeValueAsString(request)));
    }

    private static Map<String, Object> validRequest(UUID controlId) {
        Map<String, Object> request = new HashMap<>();
        request.put("controlId", controlId.toString());
        request.put("description", "Operating procedure published.");
        return request;
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

    private void assertPersistedImplementation(UUID id, UUID controlId, String description) {
        Map<String, Object> row = jdbc.queryForMap("select * from control_implementations where id = ?", id);
        assertAll(
                () -> assertEquals(controlId, row.get("control_id")),
                () -> assertEquals(description, row.get("description")),
                () -> assertEquals(IMPLEMENTED_AT, ((Timestamp) row.get("implemented_at")).toInstant())
        );
    }

    private int implementationRowCount() {
        return jdbc.queryForObject("select count(*) from control_implementations", Integer.class);
    }

    private static void assertNoInternalFields(JsonNode body) {
        assertAll(
                () -> assertTrue(body.get("id").isTextual()),
                () -> assertTrue(body.get("controlId").isTextual()),
                () -> assertTrue(body.get("description").isTextual()),
                () -> assertTrue(body.get("implementedAt").isTextual()),
                () -> assertTrue(!body.has("domainEvents")),
                () -> assertTrue(!body.has("version")),
                () -> assertTrue(!body.has("revision")),
                () -> assertTrue(!body.has("status")),
                () -> assertTrue(!body.has("effectiveness"))
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock riskClock() {
            return Clock.fixed(IMPLEMENTED_AT, ZoneOffset.UTC);
        }
    }
}
