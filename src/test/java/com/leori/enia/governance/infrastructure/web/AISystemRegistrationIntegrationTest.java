package com.leori.enia.governance.infrastructure.web;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
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
@SpringBootTest(classes = LeoriEniaApplication.class)
@AutoConfigureMockMvc
class AISystemRegistrationIntegrationTest {

    private static final Instant SOURCE_CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");
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
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void registers_ai_system_from_approved_initiative() throws Exception {
        UUID sourceInitiativeId = seedInitiative("APPROVED", 4L);
        Map<String, Object> request = validRequest(sourceInitiativeId);
        request.put("name", "  Decision support system  ");
        request.put("description", "  Supports policy decisions  ");

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());

        assertEquals("/api/v1/ai-systems/" + id, result.getResponse().getHeader("Location"));
        assertAll(
                () -> assertEquals(7, body.size()),
                () -> assertEquals(ORGANIZATION_ID.toString(), body.get("organizationId").asText()),
                () -> assertEquals(sourceInitiativeId.toString(), body.get("sourceInitiativeId").asText()),
                () -> assertEquals("Decision support system", body.get("name").asText()),
                () -> assertEquals("Supports policy decisions", body.get("description").asText()),
                () -> assertEquals("REGISTERED", body.get("status").asText()),
                () -> assertTrue(body.hasNonNull("createdAt")),
                () -> assertTrue(Instant.parse(body.get("createdAt").asText()).compareTo(SOURCE_CREATED_AT) > 0),
                () -> assertNoInternalFields(body)
        );
        assertPersistedSystem(id, sourceInitiativeId, "Decision support system", "Supports policy decisions");
    }

    @Test
    void persists_organization_derived_from_source_initiative() throws Exception {
        UUID sourceInitiativeId = seedInitiative("APPROVED", 0L);

        UUID id = register(validRequest(sourceInitiativeId));

        assertEquals(ORGANIZATION_ID, jdbc.queryForObject(
                "select organization_id from ai_systems where id = ?", UUID.class, id));
        assertEquals(sourceInitiativeId, jdbc.queryForObject(
                "select source_initiative_id from ai_systems where id = ?", UUID.class, id));
    }

    @Test
    void rejects_missing_source_initiative() throws Exception {
        Map<String, Object> request = validRequest(UUID.randomUUID());

        postRequest(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_NOT_FOUND"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, systemRowCount());
    }

    @Test
    void rejects_source_initiative_that_is_not_approved() throws Exception {
        UUID sourceInitiativeId = seedInitiative("DRAFT", 0L);

        postRequest(validRequest(sourceInitiativeId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_NOT_APPROVED_FOR_SYSTEM_REGISTRATION"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, systemRowCount());
    }

    @Test
    void rejects_duplicate_registration_and_preserves_single_row() throws Exception {
        UUID sourceInitiativeId = seedInitiative("APPROVED", 2L);
        UUID originalId = register(validRequest(sourceInitiativeId));

        postRequest(validRequest(sourceInitiativeId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_SYSTEM_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(1, systemRowCount());
        assertEquals(originalId, jdbc.queryForObject("select id from ai_systems", UUID.class));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejects_required_field_validation_errors(String field, Object value) throws Exception {
        UUID sourceInitiativeId = seedInitiative("APPROVED", 0L);
        Map<String, Object> request = validRequest(sourceInitiativeId);
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

        assertEquals(0, systemRowCount());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("sourceInitiativeId", Absent.INSTANCE),
                Arguments.of("sourceInitiativeId", null),
                Arguments.of("name", Absent.INSTANCE),
                Arguments.of("name", null),
                Arguments.of("name", "   "),
                Arguments.of("description", Absent.INSTANCE),
                Arguments.of("description", null),
                Arguments.of("description", "   ")
        );
    }

    @Test
    void rejects_invalid_uuid_as_malformed_request() throws Exception {
        Map<String, Object> request = validRequest(seedInitiative("APPROVED", 0L));
        request.put("sourceInitiativeId", "not-a-uuid");

        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, systemRowCount());
    }

    @Test
    void rejects_malformed_json_as_malformed_request() throws Exception {
        mvc.perform(post("/api/v1/ai-systems")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, systemRowCount());
    }

    @Test
    void rejects_unknown_properties_as_malformed_request() throws Exception {
        Map<String, Object> request = validRequest(seedInitiative("APPROVED", 0L));
        request.put("status", "REGISTERED");

        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, systemRowCount());
    }

    @Test
    void response_has_no_etag_or_internal_fields() throws Exception {
        UUID sourceInitiativeId = seedInitiative("APPROVED", 0L);

        var result = postRequest(validRequest(sourceInitiativeId))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.domainEvents").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andReturn();

        assertNoInternalFields(json.readTree(result.getResponse().getContentAsString()));
    }

    @Test
    void registration_does_not_modify_source_initiative_or_its_version() throws Exception {
        UUID sourceInitiativeId = seedInitiative("APPROVED", 9L);
        Map<String, Object> before = sourceInitiative(sourceInitiativeId);

        register(validRequest(sourceInitiativeId));

        assertEquals(before, sourceInitiative(sourceInitiativeId));
    }

    private UUID register(Map<String, Object> request) throws Exception {
        var result = postRequest(request).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private ResultActions postRequest(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/v1/ai-systems")
                .contentType("application/json")
                .content(json.writeValueAsString(request)));
    }

    private static Map<String, Object> validRequest(UUID sourceInitiativeId) {
        Map<String, Object> request = new HashMap<>();
        request.put("sourceInitiativeId", sourceInitiativeId.toString());
        request.put("name", "AI system");
        request.put("description", "AI system description");
        return request;
    }

    private UUID seedInitiative(String status, long version) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at, version, rejection_reason)
                values (?, ?, 'Source initiative', 'Source description', ?, ?, true, false, ?, ?, null)
                """, id, ORGANIZATION_ID, status, preliminaryRiskFor(status),
                Timestamp.from(SOURCE_CREATED_AT), version);
        return id;
    }

    private void assertPersistedSystem(
            UUID id,
            UUID sourceInitiativeId,
            String name,
            String description
    ) {
        Map<String, Object> row = jdbc.queryForMap("select * from ai_systems where id = ?", id);
        assertAll(
                () -> assertEquals(ORGANIZATION_ID, row.get("organization_id")),
                () -> assertEquals(sourceInitiativeId, row.get("source_initiative_id")),
                () -> assertEquals(name, row.get("name")),
                () -> assertEquals(description, row.get("description")),
                () -> assertEquals("REGISTERED", row.get("status")),
                () -> assertNotNull(row.get("created_at"))
        );
    }

    private Map<String, Object> sourceInitiative(UUID id) {
        return jdbc.queryForMap("select * from ai_initiatives where id = ?", id);
    }

    private static String preliminaryRiskFor(String status) {
        return "DRAFT".equals(status) ? "NOT_ASSESSED" : "HIGH";
    }

    private int systemRowCount() {
        return jdbc.queryForObject("select count(*) from ai_systems", Integer.class);
    }

    private static void assertNoInternalFields(JsonNode body) {
        assertAll(
                () -> assertTrue(body.get("id").isTextual()),
                () -> assertTrue(body.get("organizationId").isTextual()),
                () -> assertTrue(body.get("sourceInitiativeId").isTextual()),
                () -> assertTrue(body.get("name").isTextual()),
                () -> assertTrue(body.get("description").isTextual()),
                () -> assertTrue(body.get("status").isTextual()),
                () -> assertTrue(body.get("createdAt").isTextual()),
                () -> assertEquals(7, body.size()),
                () -> assertTrue(!body.has("domainEvents")),
                () -> assertTrue(!body.has("version")),
                () -> assertTrue(!body.has("revision"))
        );
    }

    private enum Absent {
        INSTANCE
    }
}
