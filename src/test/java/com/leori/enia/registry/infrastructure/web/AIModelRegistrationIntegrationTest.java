package com.leori.enia.registry.infrastructure.web;

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
        classes = {LeoriEniaApplication.class, AIModelRegistrationIntegrationTest.FixedClockConfiguration.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
class AIModelRegistrationIntegrationTest {

    private static final Instant SYSTEM_CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-29T10:00:00Z");
    private static final UUID ORGANIZATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

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
        jdbc.update("delete from ai_models");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    // ---------------------------------------------------------------------------
    // Successful registration
    // ---------------------------------------------------------------------------

    @Test
    void registers_ai_model_for_existing_system() throws Exception {
        UUID systemId = seedSystem();
        Map<String, Object> request = validRequest(systemId);
        request.put("name", "  Vision Model  ");
        request.put("description", "  Object detection  ");
        request.put("provider", "  OpenAI  ");

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());

        assertEquals("/api/v1/ai-models/" + id, result.getResponse().getHeader("Location"));
        assertAll(
                () -> assertEquals(6, body.size()),
                () -> assertEquals(systemId.toString(), body.get("systemId").asText()),
                () -> assertEquals("Vision Model", body.get("name").asText()),
                () -> assertEquals("Object detection", body.get("description").asText()),
                () -> assertEquals("OpenAI", body.get("provider").asText()),
                () -> assertEquals(REGISTERED_AT.toString(), body.get("createdAt").asText()),
                () -> assertNoInternalFields(body)
        );
        assertPersistedModel(id, systemId, "Vision Model", "Object detection", "OpenAI");
    }

    @Test
    void returns_201_and_location_header() throws Exception {
        UUID systemId = seedSystem();

        var result = postRequest(validRequest(systemId))
                .andExpect(status().isCreated())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith("/api/v1/ai-models/"));
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
    void trims_whitespace_from_text_fields() throws Exception {
        UUID systemId = seedSystem();
        Map<String, Object> request = validRequest(systemId);
        request.put("name", "  Padded Name  ");
        request.put("description", "  Padded Description  ");
        request.put("provider", "  Padded Provider  ");

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());

        assertEquals("Padded Name", body.get("name").asText());
        assertEquals("Padded Description", body.get("description").asText());
        assertEquals("Padded Provider", body.get("provider").asText());
    }

    @Test
    void persists_ai_model_row() throws Exception {
        UUID systemId = seedSystem();

        var result = postRequest(validRequest(systemId))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(
                json.readTree(result.getResponse().getContentAsString()).get("id").asText());

        assertEquals(1, modelRowCount());
        assertEquals(systemId, jdbc.queryForObject(
                "select system_id from ai_models where id = ?", UUID.class, id));
    }

    // ---------------------------------------------------------------------------
    // Missing AI system
    // ---------------------------------------------------------------------------

    @Test
    void rejects_missing_system() throws Exception {
        Map<String, Object> request = validRequest(UUID.randomUUID());

        postRequest(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_SYSTEM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("AI system not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, modelRowCount());
    }

    // ---------------------------------------------------------------------------
    // Bean validation failures
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejects_required_field_validation_errors(String field, Object value) throws Exception {
        UUID systemId = seedSystem();
        Map<String, Object> request = validRequest(systemId);
        if (value == Absent.INSTANCE) {
            request.remove(field);
        } else {
            request.put(field, value);
        }

        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fields." + field).isNotEmpty())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, modelRowCount());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("systemId", Absent.INSTANCE),
                Arguments.of("systemId", null),
                Arguments.of("name", Absent.INSTANCE),
                Arguments.of("name", null),
                Arguments.of("name", "   "),
                Arguments.of("description", Absent.INSTANCE),
                Arguments.of("description", null),
                Arguments.of("description", "   "),
                Arguments.of("provider", Absent.INSTANCE),
                Arguments.of("provider", null),
                Arguments.of("provider", "   ")
        );
    }

    // ---------------------------------------------------------------------------
    // Malformed input
    // ---------------------------------------------------------------------------

    @Test
    void rejects_invalid_uuid_as_malformed_request() throws Exception {
        Map<String, Object> request = validRequest(UUID.randomUUID());
        request.put("systemId", "not-a-uuid");

        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, modelRowCount());
    }

    @Test
    void rejects_malformed_json_as_malformed_request() throws Exception {
        mvc.perform(post("/api/v1/ai-models")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, modelRowCount());
    }

    @Test
    void rejects_unknown_properties_as_malformed_request() throws Exception {
        UUID systemId = seedSystem();
        Map<String, Object> request = validRequest(systemId);
        request.put("status", "ACTIVE");

        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, modelRowCount());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private UUID register(Map<String, Object> request) throws Exception {
        var result = postRequest(request).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private ResultActions postRequest(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/v1/ai-models")
                .contentType("application/json")
                .content(json.writeValueAsString(request)));
    }

    private static Map<String, Object> validRequest(UUID systemId) {
        Map<String, Object> request = new HashMap<>();
        request.put("systemId", systemId.toString());
        request.put("name", "Vision Model");
        request.put("description", "Object detection model");
        request.put("provider", "OpenAI");
        return request;
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

    private void assertPersistedModel(
            UUID id,
            UUID systemId,
            String name,
            String description,
            String provider
    ) {
        Map<String, Object> row = jdbc.queryForMap("select * from ai_models where id = ?", id);
        assertAll(
                () -> assertEquals(systemId, row.get("system_id")),
                () -> assertEquals(name, row.get("name")),
                () -> assertEquals(description, row.get("description")),
                () -> assertEquals(provider, row.get("provider")),
                () -> assertNotNull(row.get("created_at"))
        );
    }

    private int modelRowCount() {
        return jdbc.queryForObject("select count(*) from ai_models", Integer.class);
    }

    private static void assertNoInternalFields(JsonNode body) {
        assertAll(
                () -> assertTrue(body.get("id").isTextual()),
                () -> assertTrue(body.get("systemId").isTextual()),
                () -> assertTrue(body.get("name").isTextual()),
                () -> assertTrue(body.get("description").isTextual()),
                () -> assertTrue(body.get("provider").isTextual()),
                () -> assertTrue(body.get("createdAt").isTextual()),
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
        Clock registryClock() {
            return Clock.fixed(REGISTERED_AT, ZoneOffset.UTC);
        }
    }
}
