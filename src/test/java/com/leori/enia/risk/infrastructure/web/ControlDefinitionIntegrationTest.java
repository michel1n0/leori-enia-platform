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
        classes = {LeoriEniaApplication.class, ControlDefinitionIntegrationTest.FixedClockConfiguration.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
class ControlDefinitionIntegrationTest {

    private static final Instant SYSTEM_CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");
    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00Z");
    private static final Instant CONTROL_CREATED_AT = Instant.parse("2026-10-02T10:15:30Z");
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
        jdbc.update("delete from controls");
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void defines_control_for_existing_risk_finding() throws Exception {
        SeededRiskAssessment assessment = seedRiskAssessment();
        Map<String, Object> request = validRequest(assessment.assessmentId(), assessment.findingId());
        request.put("name", "  Human review gate  ");
        request.put("description", "  Require documented human approval before deployment.  ");

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());

        assertEquals("/api/v1/controls/" + id, result.getResponse().getHeader("Location"));
        assertAll(
                () -> assertEquals(6, body.size()),
                () -> assertEquals(assessment.assessmentId().toString(), body.get("riskAssessmentId").asText()),
                () -> assertEquals(assessment.findingId().toString(), body.get("riskFindingId").asText()),
                () -> assertEquals("Human review gate", body.get("name").asText()),
                () -> assertEquals("Require documented human approval before deployment.", body.get("description").asText()),
                () -> assertEquals(CONTROL_CREATED_AT.toString(), body.get("createdAt").asText()),
                () -> assertNoInternalFields(body)
        );
        assertPersistedControl(
                id,
                assessment.assessmentId(),
                assessment.findingId(),
                "Human review gate",
                "Require documented human approval before deployment."
        );
    }

    @Test
    void returns_201_and_location_header() throws Exception {
        SeededRiskAssessment assessment = seedRiskAssessment();

        var result = postRequest(validRequest(assessment.assessmentId(), assessment.findingId()))
                .andExpect(status().isCreated())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith("/api/v1/controls/"));
        UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void rejects_missing_risk_assessment() throws Exception {
        SeededRiskAssessment assessment = seedRiskAssessment();
        Map<String, Object> request = validRequest(UUID.randomUUID(), assessment.findingId());

        postRequest(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RISK_ASSESSMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Risk assessment not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, controlRowCount());
    }

    @Test
    void rejects_risk_finding_that_does_not_belong_to_assessment() throws Exception {
        SeededRiskAssessment assessment = seedRiskAssessment();
        SeededRiskAssessment otherAssessment = seedRiskAssessment();

        postRequest(validRequest(assessment.assessmentId(), otherAssessment.findingId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RISK_FINDING_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Risk finding not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, controlRowCount());
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejects_validation_errors(String field, Object value) throws Exception {
        SeededRiskAssessment assessment = seedRiskAssessment();
        Map<String, Object> request = validRequest(assessment.assessmentId(), assessment.findingId());
        if (value == Absent.INSTANCE) {
            request.remove(field);
        } else {
            request.put(field, value);
        }

        var result = postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertTrue(body.get("fields").has(field), "Expected validation field: " + field + " in " + body.get("fields"));
        assertEquals(0, controlRowCount());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("riskAssessmentId", Absent.INSTANCE),
                Arguments.of("riskAssessmentId", null),
                Arguments.of("riskFindingId", Absent.INSTANCE),
                Arguments.of("riskFindingId", null),
                Arguments.of("name", Absent.INSTANCE),
                Arguments.of("name", null),
                Arguments.of("name", "   "),
                Arguments.of("description", Absent.INSTANCE),
                Arguments.of("description", null),
                Arguments.of("description", "   ")
        );
    }

    @Test
    void rejects_malformed_json_as_malformed_request() throws Exception {
        mvc.perform(post("/api/v1/controls")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, controlRowCount());
    }

    @ParameterizedTest
    @MethodSource("malformedRequests")
    void rejects_malformed_requests(String field, Object value) throws Exception {
        SeededRiskAssessment assessment = seedRiskAssessment();
        Map<String, Object> request = validRequest(assessment.assessmentId(), assessment.findingId());
        request.put(field, value);

        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, controlRowCount());
    }

    static Stream<Arguments> malformedRequests() {
        return Stream.of(
                Arguments.of("controlId", UUID.randomUUID().toString()),
                Arguments.of("riskAssessmentId", "not-a-uuid"),
                Arguments.of("riskFindingId", "not-a-uuid")
        );
    }

    private ResultActions postRequest(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/v1/controls")
                .contentType("application/json")
                .content(json.writeValueAsString(request)));
    }

    private static Map<String, Object> validRequest(UUID assessmentId, UUID findingId) {
        Map<String, Object> request = new HashMap<>();
        request.put("riskAssessmentId", assessmentId.toString());
        request.put("riskFindingId", findingId.toString());
        request.put("name", "Human review gate");
        request.put("description", "Require documented human approval before deployment.");
        return request;
    }

    private SeededRiskAssessment seedRiskAssessment() {
        UUID systemId = seedSystem();
        UUID assessmentId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
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
        return new SeededRiskAssessment(assessmentId, findingId);
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

    private void assertPersistedControl(
            UUID id,
            UUID riskAssessmentId,
            UUID riskFindingId,
            String name,
            String description
    ) {
        Map<String, Object> row = jdbc.queryForMap("select * from controls where id = ?", id);
        assertAll(
                () -> assertEquals(riskAssessmentId, row.get("risk_assessment_id")),
                () -> assertEquals(riskFindingId, row.get("risk_finding_id")),
                () -> assertEquals(name, row.get("name")),
                () -> assertEquals(description, row.get("description")),
                () -> assertEquals(CONTROL_CREATED_AT, ((Timestamp) row.get("created_at")).toInstant())
        );
    }

    private int controlRowCount() {
        return jdbc.queryForObject("select count(*) from controls", Integer.class);
    }

    private static void assertNoInternalFields(JsonNode body) {
        assertAll(
                () -> assertTrue(body.get("id").isTextual()),
                () -> assertTrue(body.get("riskAssessmentId").isTextual()),
                () -> assertTrue(body.get("riskFindingId").isTextual()),
                () -> assertTrue(body.get("name").isTextual()),
                () -> assertTrue(body.get("description").isTextual()),
                () -> assertTrue(body.get("createdAt").isTextual()),
                () -> assertTrue(!body.has("domainEvents")),
                () -> assertTrue(!body.has("version")),
                () -> assertTrue(!body.has("revision"))
        );
    }

    private record SeededRiskAssessment(UUID assessmentId, UUID findingId) {
    }

    private enum Absent {
        INSTANCE
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock riskClock() {
            return Clock.fixed(CONTROL_CREATED_AT, ZoneOffset.UTC);
        }
    }
}
