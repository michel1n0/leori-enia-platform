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
        classes = {LeoriEniaApplication.class, DatasetRegistrationIntegrationTest.FixedClockConfiguration.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
class DatasetRegistrationIntegrationTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-29T10:00:00Z");

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
        jdbc.update("delete from ai_datasets");
    }

    // ---------------------------------------------------------------------------
    // Successful registration
    // ---------------------------------------------------------------------------

    @Test
    void registers_dataset() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("name", "  Training data  ");
        request.put("description", "  Curated observations  ");

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());

        assertEquals("/api/v1/datasets/" + id, result.getResponse().getHeader("Location"));
        assertAll(
                () -> assertEquals(4, body.size()),
                () -> assertEquals("Training data", body.get("name").asText()),
                () -> assertEquals("Curated observations", body.get("description").asText()),
                () -> assertEquals(REGISTERED_AT.toString(), body.get("createdAt").asText()),
                () -> assertNoInternalFields(body)
        );
        assertPersistedDataset(id, "Training data", "Curated observations");
    }

    @Test
    void returns_201_and_location_header() throws Exception {
        var result = postRequest(validRequest())
                .andExpect(status().isCreated())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith("/api/v1/datasets/"));
        UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void response_has_exactly_four_fields_and_no_internal_fields() throws Exception {
        var result = postRequest(validRequest())
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
        Map<String, Object> request = validRequest();
        request.put("name", "  Padded Name  ");
        request.put("description", "  Padded Description  ");

        var result = postRequest(request)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());

        assertEquals("Padded Name", body.get("name").asText());
        assertEquals("Padded Description", body.get("description").asText());
    }

    @Test
    void persists_dataset_row_and_commits_registration() throws Exception {
        var result = postRequest(validRequest())
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(
                json.readTree(result.getResponse().getContentAsString()).get("id").asText());

        assertEquals(1, datasetRowCount());
        assertPersistedDataset(id, "Training data", "Curated observations");
    }

    // ---------------------------------------------------------------------------
    // Bean validation failures
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejects_required_field_validation_errors(String field, Object value) throws Exception {
        Map<String, Object> request = validRequest();
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

        assertEquals(0, datasetRowCount());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("name", Absent.INSTANCE),
                Arguments.of("name", null),
                Arguments.of("name", "   "),
                Arguments.of("description", Absent.INSTANCE),
                Arguments.of("description", null),
                Arguments.of("description", "   ")
        );
    }

    // ---------------------------------------------------------------------------
    // Malformed input
    // ---------------------------------------------------------------------------

    @Test
    void rejects_malformed_json_as_malformed_request() throws Exception {
        mvc.perform(post("/api/v1/datasets")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, datasetRowCount());
    }

    @Test
    void rejects_unknown_properties_as_malformed_request() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("status", "REGISTERED");

        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(0, datasetRowCount());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private ResultActions postRequest(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/v1/datasets")
                .contentType("application/json")
                .content(json.writeValueAsString(request)));
    }

    private static Map<String, Object> validRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Training data");
        request.put("description", "Curated observations");
        return request;
    }

    private void assertPersistedDataset(
            UUID id,
            String name,
            String description
    ) {
        assertAll(
                () -> assertEquals(name, jdbc.queryForObject(
                        "select name from ai_datasets where id = ?", String.class, id)),
                () -> assertEquals(description, jdbc.queryForObject(
                        "select description from ai_datasets where id = ?", String.class, id)),
                () -> assertEquals(REGISTERED_AT, jdbc.queryForObject(
                        "select created_at from ai_datasets where id = ?", Timestamp.class, id).toInstant())
        );
    }

    private int datasetRowCount() {
        return jdbc.queryForObject("select count(*) from ai_datasets", Integer.class);
    }

    private static void assertNoInternalFields(JsonNode body) {
        assertAll(
                () -> assertTrue(body.get("id").isTextual()),
                () -> assertTrue(body.get("name").isTextual()),
                () -> assertTrue(body.get("description").isTextual()),
                () -> assertTrue(body.get("createdAt").isTextual()),
                () -> assertEquals(4, body.size()),
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
