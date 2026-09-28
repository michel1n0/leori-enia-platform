package com.leori.enia.governance.infrastructure.web;

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

import java.sql.Timestamp;
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
        classes = {LeoriEniaApplication.class, AISystemGetIntegrationTest.FixedClockConfiguration.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
class AISystemGetIntegrationTest {

    private static final Instant SOURCE_CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-25T15:00:00Z");
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
    void gets_registered_ai_system_from_post_location() throws Exception {
        UUID sourceInitiativeId = seedApprovedInitiative();
        Map<String, Object> request = validRequest(sourceInitiativeId);
        request.put("name", "  Decision support system  ");
        request.put("description", "  Supports policy decisions  ");
        var postResult = mvc.perform(post("/api/v1/ai-systems")
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
                () -> assertEquals(7, body.size()),
                () -> assertEquals(location.substring(location.lastIndexOf('/') + 1), body.get("id").asText()),
                () -> assertEquals(ORGANIZATION_ID.toString(), body.get("organizationId").asText()),
                () -> assertEquals(sourceInitiativeId.toString(), body.get("sourceInitiativeId").asText()),
                () -> assertEquals("Decision support system", body.get("name").asText()),
                () -> assertEquals("Supports policy decisions", body.get("description").asText()),
                () -> assertEquals("REGISTERED", body.get("status").asText()),
                () -> assertEquals(REGISTERED_AT.toString(), body.get("createdAt").asText()),
                () -> assertNoInternalFields(body)
        );
    }

    @Test
    void missing_system_returns_not_found() throws Exception {
        mvc.perform(get("/api/v1/ai-systems/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_SYSTEM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("AI system not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void invalid_uuid_returns_bad_request() throws Exception {
        mvc.perform(get("/api/v1/ai-systems/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AI_SYSTEM_ID"))
                .andExpect(jsonPath("$.message").value("Invalid AI system id"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private static Map<String, Object> validRequest(UUID sourceInitiativeId) {
        Map<String, Object> request = new HashMap<>();
        request.put("sourceInitiativeId", sourceInitiativeId.toString());
        request.put("name", "AI system");
        request.put("description", "AI system description");
        return request;
    }

    private UUID seedApprovedInitiative() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at, version, rejection_reason)
                values (?, ?, 'Source initiative', 'Source description', 'APPROVED', 'HIGH', true, false, ?, 0, null)
                """, id, ORGANIZATION_ID, Timestamp.from(SOURCE_CREATED_AT));
        return id;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock governanceClock() {
            return Clock.fixed(REGISTERED_AT, ZoneOffset.UTC);
        }
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
                () -> assertTrue(!body.has("domainEvents")),
                () -> assertTrue(!body.has("version")),
                () -> assertTrue(!body.has("revision"))
        );
    }
}
