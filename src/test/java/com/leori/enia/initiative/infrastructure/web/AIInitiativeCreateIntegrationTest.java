package com.leori.enia.initiative.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leori.enia.LeoriEniaApplication;
import com.leori.enia.initiative.application.CreateAIInitiativeUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.support.AopUtils;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LeoriEniaApplication.class)
@AutoConfigureMockMvc
class AIInitiativeCreateIntegrationTest {

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

    @Autowired
    private CreateAIInitiativeUseCase create;

    @Test
    void creates_draft_through_the_production_write_transaction_and_get_reads_it() throws Exception {
        assertTrue(AopUtils.isAopProxy(create));
        Map<String, Object> request = validRequest();
        request.put("name", "  New initiative  ");
        request.put("description", "  Description  ");
        request.put("usesPersonalData", false);
        request.put("impactsRights", false);

        var postResult = postRequest(request)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();
        JsonNode body = json.readTree(postResult.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());
        String location = "/api/v1/ai-initiatives/" + id;

        assertEquals(location, postResult.getResponse().getHeader("Location"));
        assertEquals(10, body.size());
        assertTrue(body.has("rejectionReason"));
        assertTrue(body.get("rejectionReason").isNull());
        assertEquals(request.get("organizationId"), body.get("organizationId").asText());
        assertEquals("New initiative", body.get("name").asText());
        assertEquals("Description", body.get("description").asText());
        assertEquals("DRAFT", body.get("status").asText());
        assertEquals("NOT_ASSESSED", body.get("preliminaryRisk").asText());
        assertEquals(false, body.get("usesPersonalData").asBoolean());
        assertEquals(false, body.get("impactsRights").asBoolean());
        assertTrue(body.hasNonNull("createdAt"));
        assertEquals(0, Instant.parse(body.get("createdAt").asText()).getNano() % 1_000);
        assertEquals("DRAFT", jdbc.queryForObject(
                "select status from ai_initiatives where id = ?", String.class, id));

        var getResult = mvc.perform(get(location))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(body, json.readTree(getResult.getResponse().getContentAsString()));
    }

    @Test
    void accepts_normalized_values_at_both_storage_limits() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("name", "  " + "n".repeat(255) + "  ");
        request.put("description", "  " + "d".repeat(4000) + "  ");

        var result = postRequest(request).andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString())
                .get("id").asText());

        assertEquals(255, jdbc.queryForObject(
                "select length(name) from ai_initiatives where id = ?", Integer.class, id));
        assertEquals(4000, jdbc.queryForObject(
                "select length(description) from ai_initiatives where id = ?", Integer.class, id));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejects_invalid_fields_before_persistence(String field, Object value) throws Exception {
        Map<String, Object> request = validRequest();
        if (value == null) {
            request.remove(field);
        } else {
            request.put(field, value);
        }
        Integer before = jdbc.queryForObject("select count(*) from ai_initiatives", Integer.class);

        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fields." + field).isNotEmpty())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(before, jdbc.queryForObject("select count(*) from ai_initiatives", Integer.class));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidFields() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("organizationId", null),
                org.junit.jupiter.params.provider.Arguments.of("name", "   "),
                org.junit.jupiter.params.provider.Arguments.of("description", "   "),
                org.junit.jupiter.params.provider.Arguments.of("name", "n".repeat(256)),
                org.junit.jupiter.params.provider.Arguments.of("description", "d".repeat(4001)),
                org.junit.jupiter.params.provider.Arguments.of("usesPersonalData", null),
                org.junit.jupiter.params.provider.Arguments.of("impactsRights", null)
        );
    }

    @Test
    void malformed_organization_uuid_has_a_stable_bad_request_response() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("organizationId", "not-a-uuid");

        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("internalFields")
    void rejects_internal_state_fields(String field) throws Exception {
        Map<String, Object> request = validRequest();
        request.put(field, "client-supplied");

        postRequest(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    static Stream<String> internalFields() {
        return Stream.of("id", "status", "preliminaryRisk", "createdAt",
                "version", "revision", "domainEvents", "rejectionReason");
    }

    @Test
    void malformed_json_has_a_stable_bad_request_response() throws Exception {
        mvc.perform(post("/api/v1/ai-initiatives")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private ResultActions postRequest(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/v1/ai-initiatives")
                .contentType("application/json")
                .content(json.writeValueAsString(request)));
    }

    private static Map<String, Object> validRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("organizationId", UUID.randomUUID().toString());
        request.put("name", "New initiative");
        request.put("description", "Description");
        request.put("usesPersonalData", true);
        request.put("impactsRights", false);
        return request;
    }
}
