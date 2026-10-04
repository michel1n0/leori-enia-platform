package com.leori.enia.risk.infrastructure.web;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
        classes = {LeoriEniaApplication.class, RiskAssessmentRecordingIntegrationTest.FixedClockConfiguration.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
class RiskAssessmentRecordingIntegrationTest {

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
    void records_risk_assessment_for_existing_system() throws Exception {
        UUID systemId = seedSystem();
        Map<String, Object> request = validRequest(systemId);
        request.put("purpose", "  Governance approval  ");
        request.put("deploymentContext", "  Public sector deployment  ");
        request.put("findings", new ArrayList<>(List.of(
                finding("  Bias risk  ", "MEDIUM", "HIGH"),
                finding("Privacy risk", "LOW", "MEDIUM"),
                finding("  Bias risk  ", "MEDIUM", "HIGH")
        )));

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());

        assertEquals("/api/v1/risk-assessments/" + id, result.getResponse().getHeader("Location"));
        assertAll(
                () -> assertEquals(6, body.size()),
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
        assertPersistedAssessment(id, systemId, "Governance approval", "Public sector deployment");
        assertPersistedFindings(id, List.of(
                expectedFinding("Bias risk", "MEDIUM", "HIGH"),
                expectedFinding("Privacy risk", "LOW", "MEDIUM"),
                expectedFinding("Bias risk", "MEDIUM", "HIGH")
        ));
    }

    @Test
    void returns_201_and_location_header() throws Exception {
        UUID systemId = seedSystem();

        var result = postRequest(validRequest(systemId))
                .andExpect(status().isCreated())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith("/api/v1/risk-assessments/"));
        UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void response_has_exactly_six_fields_and_no_internal_fields() throws Exception {
        UUID systemId = seedSystem();

        var result = postRequest(validRequest(systemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.domainEvents").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertNoInternalFields(body);
    }

    @Test
    void persists_root_and_ordered_duplicate_findings() throws Exception {
        UUID systemId = seedSystem();

        var result = postRequest(validRequest(systemId))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());

        assertEquals(1, assessmentRowCount());
        assertEquals(3, findingRowCount());
        assertPersistedAssessment(id, systemId, "Governance approval", "Public sector deployment");
        assertPersistedFindings(id, List.of(
                expectedFinding("Bias risk", "MEDIUM", "HIGH"),
                expectedFinding("Privacy risk", "LOW", "MEDIUM"),
                expectedFinding("Bias risk", "MEDIUM", "HIGH")
        ));
    }

    @Test
    void rejects_missing_system() throws Exception {
        Map<String, Object> request = validRequest(UUID.randomUUID());

        postRequest(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_SYSTEM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("AI system not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, assessmentRowCount());
        assertEquals(0, findingRowCount());
    }

    @ParameterizedTest
    @MethodSource("invalidRootFields")
    void rejects_root_validation_errors(String field, Object value) throws Exception {
        UUID systemId = seedSystem();
        Map<String, Object> request = validRequest(systemId);
        if (value == Absent.INSTANCE) {
            request.remove(field);
        } else {
            request.put(field, value);
        }

        assertValidationError(request, field);
        assertEquals(0, assessmentRowCount());
        assertEquals(0, findingRowCount());
    }

    static Stream<Arguments> invalidRootFields() {
        return Stream.of(
                Arguments.of("systemId", Absent.INSTANCE),
                Arguments.of("systemId", null),
                Arguments.of("purpose", Absent.INSTANCE),
                Arguments.of("purpose", null),
                Arguments.of("purpose", "   "),
                Arguments.of("deploymentContext", Absent.INSTANCE),
                Arguments.of("deploymentContext", null),
                Arguments.of("deploymentContext", "   "),
                Arguments.of("findings", Absent.INSTANCE),
                Arguments.of("findings", null),
                Arguments.of("findings", List.of())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidNestedFields")
    void rejects_nested_finding_validation_errors(String expectedField, Map<String, Object> finding) throws Exception {
        UUID systemId = seedSystem();
        Map<String, Object> request = validRequest(systemId);
        request.put("findings", Collections.singletonList(finding));

        assertValidationError(request, expectedField);
        assertEquals(0, assessmentRowCount());
        assertEquals(0, findingRowCount());
    }

    static Stream<Arguments> invalidNestedFields() {
        return Stream.of(
                Arguments.of("findings[0]", null),
                Arguments.of("findings[0].description", findingWithout("description")),
                Arguments.of("findings[0].description", finding("description", null)),
                Arguments.of("findings[0].description", finding("description", "   ")),
                Arguments.of("findings[0].likelihood", finding("likelihood", null)),
                Arguments.of("findings[0].impactMagnitude", finding("impactMagnitude", null))
        );
    }

    @Test
    void rejects_invalid_uuid_as_malformed_request() throws Exception {
        Map<String, Object> request = validRequest(UUID.randomUUID());
        request.put("systemId", "not-a-uuid");

        assertMalformedRequest(request);
    }

    @Test
    void rejects_invalid_enum_as_malformed_request() throws Exception {
        Map<String, Object> request = validRequest(seedSystem());
        request.put("findings", List.of(finding("Bias risk", "CRITICAL", "HIGH")));

        assertMalformedRequest(request);
    }

    @Test
    void rejects_malformed_json_as_malformed_request() throws Exception {
        mvc.perform(post("/api/v1/risk-assessments")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, assessmentRowCount());
        assertEquals(0, findingRowCount());
    }

    @Test
    void rejects_unknown_root_properties_as_malformed_request() throws Exception {
        Map<String, Object> request = validRequest(seedSystem());
        request.put("overallRiskLevel", "HIGH");

        assertMalformedRequest(request);
    }

    @Test
    void rejects_unknown_nested_properties_as_malformed_request() throws Exception {
        Map<String, Object> nested = finding("Bias risk", "MEDIUM", "HIGH");
        nested.put("score", 9);
        Map<String, Object> request = validRequest(seedSystem());
        request.put("findings", List.of(nested));

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
    }

    private void assertMalformedRequest(Map<String, Object> request) throws Exception {
        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, assessmentRowCount());
        assertEquals(0, findingRowCount());
    }

    private ResultActions postRequest(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/v1/risk-assessments")
                .contentType("application/json")
                .content(json.writeValueAsString(request)));
    }

    private static Map<String, Object> validRequest(UUID systemId) {
        Map<String, Object> request = new HashMap<>();
        request.put("systemId", systemId.toString());
        request.put("purpose", "Governance approval");
        request.put("deploymentContext", "Public sector deployment");
        request.put("findings", new ArrayList<>(List.of(
                finding("Bias risk", "MEDIUM", "HIGH"),
                finding("Privacy risk", "LOW", "MEDIUM"),
                finding("Bias risk", "MEDIUM", "HIGH")
        )));
        return request;
    }

    private static Map<String, Object> finding(String description, String likelihood, String impactMagnitude) {
        Map<String, Object> finding = new HashMap<>();
        finding.put("description", description);
        finding.put("likelihood", likelihood);
        finding.put("impactMagnitude", impactMagnitude);
        return finding;
    }

    private static Map<String, Object> finding(String field, Object value) {
        Map<String, Object> finding = finding("Bias risk", "MEDIUM", "HIGH");
        finding.put(field, value);
        return finding;
    }

    private static Map<String, Object> findingWithout(String field) {
        Map<String, Object> finding = finding("Bias risk", "MEDIUM", "HIGH");
        finding.remove(field);
        return finding;
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

    private void assertPersistedAssessment(
            UUID id,
            UUID systemId,
            String purpose,
            String deploymentContext
    ) {
        Map<String, Object> row = jdbc.queryForMap("select * from risk_assessments where id = ?", id);
        assertAll(
                () -> assertEquals(systemId, row.get("system_id")),
                () -> assertEquals(purpose, row.get("purpose")),
                () -> assertEquals(deploymentContext, row.get("deployment_context")),
                () -> assertEquals(ASSESSED_AT, ((Timestamp) row.get("assessed_at")).toInstant())
        );
    }

    private void assertPersistedFindings(UUID assessmentId, List<Map<String, String>> expected) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select position, description, likelihood, impact_magnitude
                from risk_assessment_findings
                where risk_assessment_id = ?
                order by position
                """, assessmentId);

        assertEquals(expected.size(), rows.size());
        for (int i = 0; i < expected.size(); i++) {
            Map<String, Object> row = rows.get(i);
            Map<String, String> expectedFinding = expected.get(i);
            int expectedPosition = i;
            int position = ((Number) row.get("position")).intValue();
            assertAll(
                    () -> assertEquals(expectedPosition, position),
                    () -> assertEquals(expectedFinding.get("description"), row.get("description")),
                    () -> assertEquals(expectedFinding.get("likelihood"), row.get("likelihood")),
                    () -> assertEquals(expectedFinding.get("impactMagnitude"), row.get("impact_magnitude"))
            );
        }
    }

    private static Map<String, String> expectedFinding(
            String description,
            String likelihood,
            String impactMagnitude
    ) {
        Map<String, String> finding = new HashMap<>();
        finding.put("description", description);
        finding.put("likelihood", likelihood);
        finding.put("impactMagnitude", impactMagnitude);
        return finding;
    }

    private int assessmentRowCount() {
        return jdbc.queryForObject("select count(*) from risk_assessments", Integer.class);
    }

    private int findingRowCount() {
        return jdbc.queryForObject("select count(*) from risk_assessment_findings", Integer.class);
    }

    private static void assertFinding(
            JsonNode finding,
            String description,
            String likelihood,
            String impactMagnitude
    ) {
        assertAll(
                () -> assertEquals(3, finding.size()),
                () -> assertEquals(description, finding.get("description").asText()),
                () -> assertEquals(likelihood, finding.get("likelihood").asText()),
                () -> assertEquals(impactMagnitude, finding.get("impactMagnitude").asText())
        );
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

    private enum Absent {
        INSTANCE
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock riskClock() {
            return Clock.fixed(ASSESSED_AT, ZoneOffset.UTC);
        }
    }
}
