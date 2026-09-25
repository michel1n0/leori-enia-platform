package com.leori.enia.initiative.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leori.enia.LeoriEniaApplication;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiativeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LeoriEniaApplication.class)
@AutoConfigureMockMvc
class AIInitiativeRejectIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private AIInitiativeRepository repository;

    private static final String BODY = "{\"reason\":\"  Residual risk unacceptable  \"}";

    @Test
    void conditional_rejection_preserves_high_risk_and_returns_the_saved_etag() throws Exception {
        String path = createDraft();
        var initial = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        String id = json.readTree(initial.getResponse().getContentAsString()).get("id").asText();
        String initialTag = initial.getResponse().getHeader("ETag");
        assertEquals(tag(id, 0), initialTag);

        String submittedTag = mvc.perform(post(path + "/submit").header("If-Match", initialTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.rejectionReason").hasJsonPath())
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getHeader("ETag");
        assertEquals(tag(id, 1), submittedTag);

        String assessmentTag = mvc.perform(post(path + "/assessment/start").header("If-Match", submittedTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"))
                .andExpect(jsonPath("$.rejectionReason").hasJsonPath())
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getHeader("ETag");
        assertEquals(tag(id, 2), assessmentTag);

        String preRejectionTag = mvc.perform(post(path + "/risk-assessment").header("If-Match", assessmentTag)
                        .contentType("application/json").content("{\"riskLevel\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RISK_ASSESSED"))
                .andExpect(jsonPath("$.rejectionReason").hasJsonPath())
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getHeader("ETag");
        assertEquals(tag(id, 3), preRejectionTag);

        var rejection = mvc.perform(post(path + "/reject").contentType("application/json").content(BODY)
                        .header("If-Match", preRejectionTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").hasJsonPath())
                .andExpect(jsonPath("$.rejectionReason").value("Residual risk unacceptable"))
                .andExpect(jsonPath("$.preliminaryRisk").value("HIGH"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.domainEvents").doesNotExist())
                .andReturn();
        String rejectedTag = rejection.getResponse().getHeader("ETag");
        assertNotNull(rejectedTag);
        assertNotEquals(preRejectionTag, rejectedTag);
        assertEquals(tag(id, 4), rejectedTag);
        JsonNode rejected = json.readTree(rejection.getResponse().getContentAsString());
        assertEquals(10, rejected.size());
        var afterGet = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(rejectedTag, afterGet.getResponse().getHeader("ETag"));
        assertEquals(rejected, json.readTree(afterGet.getResponse().getContentAsString()));

        mvc.perform(post(path + "/reject").contentType("application/json").content("{\"reason\":\"Replacement reason\"}")
                        .header("If-Match", preRejectionTag))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_REVISION_MISMATCH"));
        mvc.perform(post(path + "/reject").contentType("application/json").content("{\"reason\":\"Replacement reason\"}")
                        .header("If-Match", rejectedTag))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_TRANSITION"));

        mvc.perform(post(path + "/approve").header("If-Match", rejectedTag))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_TRANSITION"));

        var stored = repository.findById(new AIInitiativeId(UUID.fromString(id))).orElseThrow();
        assertEquals("Residual risk unacceptable", stored.initiative().rejectionReason());
        assertEquals(4, stored.version());

        var afterFailures = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(rejectedTag, afterFailures.getResponse().getHeader("ETag"));
        assertEquals(rejected, json.readTree(afterFailures.getResponse().getContentAsString()));
    }

    @Test
    void header_and_resource_errors_reuse_existing_contracts() throws Exception {
        String path = createDraft();
        var before = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        String currentTag = before.getResponse().getHeader("ETag");

        mvc.perform(post(path + "/reject").contentType("application/json").content(BODY))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.code").value("IF_MATCH_REQUIRED"));
        for (String invalid : new String[]{"bad", "W/" + currentTag, "*", currentTag + ", " + currentTag}) {
            mvc.perform(post(path + "/reject").contentType("application/json").content(BODY)
                            .header("If-Match", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));
        }

        String otherPath = createDraft();
        String otherTag = mvc.perform(get(otherPath)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(post(path + "/reject").contentType("application/json").content(BODY)
                        .header("If-Match", otherTag))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_REVISION_MISMATCH"));

        String missingId = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/ai-initiatives/" + missingId + "/reject").contentType("application/json").content(BODY)
                        .header("If-Match", tag(missingId, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_NOT_FOUND"));
        mvc.perform(post("/api/v1/ai-initiatives/invalid/reject").contentType("application/json").content(BODY)
                        .header("If-Match", currentTag))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_ID"));
        mvc.perform(post(path + "/reject").contentType("application/json").content(BODY)
                        .header("If-Match", currentTag))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_TRANSITION"));

        var after = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(currentTag, after.getResponse().getHeader("ETag"));
        assertEquals(json.readTree(before.getResponse().getContentAsString()),
                json.readTree(after.getResponse().getContentAsString()));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalid_body_precedes_missing_if_match_and_preserves_state(String body, String code) throws Exception {
        String path = createDraft();
        var before = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        String currentTag = before.getResponse().getHeader("ETag");

        mvc.perform(post(path + "/reject").header("If-Match", currentTag)
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
        mvc.perform(post(path + "/reject").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));

        var after = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(currentTag, after.getResponse().getHeader("ETag"));
        assertEquals(json.readTree(before.getResponse().getContentAsString()),
                json.readTree(after.getResponse().getContentAsString()));
    }

    static Stream<Arguments> invalidBodies() throws Exception {
        ObjectMapper json = new ObjectMapper();
        Stream<Arguments> malformed = Stream.of(
                Arguments.of("", "MALFORMED_REQUEST"),
                Arguments.of("{", "MALFORMED_REQUEST"),
                Arguments.of("null", "MALFORMED_REQUEST"),
                Arguments.of("{}", "VALIDATION_ERROR"),
                Arguments.of("{\"reason\":null}", "VALIDATION_ERROR"),
                Arguments.of("{\"reason\":\"Valid reason\",\"unexpected\":true}", "MALFORMED_REQUEST"));
        var blankBodies = new java.util.ArrayList<Arguments>();
        for (String reason : new String[]{"", "   ", "\t\n", "\u0000", "\u2003", "\u0000 \u2003\t"}) {
            blankBodies.add(Arguments.of(json.writeValueAsString(Map.of("reason", reason)), "VALIDATION_ERROR"));
        }
        return Stream.concat(malformed, blankBodies.stream());
    }

    @Test
    void invalid_uuid_precedes_body_validation_and_missing_if_match() throws Exception {
        mvc.perform(post("/api/v1/ai-initiatives/invalid/reject")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_ID"));
    }

    private String createDraft() throws Exception {
        var created = mvc.perform(post("/api/v1/ai-initiatives")
                        .contentType("application/json").content("""
                                {
                                  "organizationId": "%s",
                                  "name": "Conditional rejection",
                                  "description": "HTTP rejection preconditions",
                                  "usesPersonalData": true,
                                  "impactsRights": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.rejectionReason").hasJsonPath())
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        assertNotNull(location);
        return location;
    }

    private static String tag(String id, long revision) {
        return "\"ai-initiative:" + id + ":" + revision + "\"";
    }
}
