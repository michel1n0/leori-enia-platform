package com.leori.enia.registry.infrastructure.web;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
        classes = {LeoriEniaApplication.class, DatasetGetIntegrationTest.FixedClockConfiguration.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
class DatasetGetIntegrationTest {

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

    @Test
    void gets_registered_dataset_from_post_location() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("name", "  Training data  ");
        request.put("description", "  Curated observations  ");
        var postResult = mvc.perform(post("/api/v1/datasets")
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String location = postResult.getResponse().getHeader("Location");

        var getResult = mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();

        JsonNode body = json.readTree(getResult.getResponse().getContentAsString());
        assertAll(
                () -> assertEquals(4, body.size()),
                () -> assertEquals(location.substring(location.lastIndexOf('/') + 1), body.get("id").asText()),
                () -> assertEquals("Training data", body.get("name").asText()),
                () -> assertEquals("Curated observations", body.get("description").asText()),
                () -> assertEquals(REGISTERED_AT.toString(), body.get("createdAt").asText()),
                () -> assertNoInternalFields(body)
        );
    }

    @Test
    void get_does_not_mutate_persisted_data() throws Exception {
        var postResult = mvc.perform(post("/api/v1/datasets")
                        .contentType("application/json")
                        .content(json.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        String location = postResult.getResponse().getHeader("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        Map<String, Object> before = datasetRow(id);

        mvc.perform(get(location))
                .andExpect(status().isOk());

        assertEquals(before, datasetRow(id));
    }

    @Test
    void missing_dataset_returns_not_found() throws Exception {
        mvc.perform(get("/api/v1/datasets/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Dataset not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void invalid_uuid_returns_bad_request() throws Exception {
        mvc.perform(get("/api/v1/datasets/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATASET_ID"))
                .andExpect(jsonPath("$.message").value("Invalid dataset id"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private static Map<String, Object> validRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Training data");
        request.put("description", "Curated observations");
        return request;
    }

    private Map<String, Object> datasetRow(UUID id) {
        return jdbc.queryForMap("select id, name, description, created_at from ai_datasets where id = ?", id);
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

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock registryClock() {
            return Clock.fixed(REGISTERED_AT, ZoneOffset.UTC);
        }
    }
}
