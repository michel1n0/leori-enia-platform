package com.leori.enia.initiative.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leori.enia.LeoriEniaApplication;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

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
class AIInitiativeSubmitIntegrationTest {

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
    @Autowired private AIInitiativeRepository repository;
    @Autowired private ObjectMapper json;

    @Test
    void conditional_submit_returns_actual_new_etag_and_matching_get() throws Exception {
        AIInitiative initiative = draft();
        String path = path(initiative.id());
        var initialGet = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        String originalTag = initialGet.getResponse().getHeader("ETag");
        assertNotNull(originalTag);
        assertEquals(9, json.readTree(initialGet.getResponse().getContentAsString()).size());

        var submit = mvc.perform(post(path + "/submit").header("If-Match", originalTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.domainEvents").doesNotExist())
                .andReturn();
        String newTag = submit.getResponse().getHeader("ETag");
        assertNotNull(newTag);
        assertNotEquals(originalTag, newTag);
        assertEquals(1, repository.findById(initiative.id()).orElseThrow().version());

        var afterGet = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertEquals(newTag, afterGet.getResponse().getHeader("ETag"));
        JsonNode submitted = json.readTree(submit.getResponse().getContentAsString());
        assertEquals(submitted, json.readTree(afterGet.getResponse().getContentAsString()));

        mvc.perform(post(path + "/submit").header("If-Match", originalTag))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_REVISION_MISMATCH"));
        mvc.perform(post(path + "/submit").header("If-Match", newTag))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_TRANSITION"));
    }

    @Test
    void header_errors_and_not_found_have_stable_responses() throws Exception {
        AIInitiative initiative = draft();
        String path = path(initiative.id());
        String tag = mvc.perform(get(path)).andReturn().getResponse().getHeader("ETag");

        mvc.perform(post(path + "/submit"))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.code").value("IF_MATCH_REQUIRED"));
        for (String invalid : new String[]{"bad", "W/" + tag, "*", tag + ", " + tag}) {
            mvc.perform(post(path + "/submit").header("If-Match", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));
        }
        mvc.perform(post(path + "/submit").header("If-Match", tag, tag))
                .andExpect(status().isBadRequest());
        AIInitiative other = draft();
        String otherTag = mvc.perform(get(path(other.id()))).andReturn().getResponse().getHeader("ETag");
        mvc.perform(post(path + "/submit").header("If-Match", otherTag))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(post(path(AIInitiativeId.generate()) + "/submit").header("If-Match", tag))
                .andExpect(status().isNotFound());
        String missing = path(AIInitiativeId.generate());
        String syntacticallyValid = "\"ai-initiative:" + missing.substring(missing.lastIndexOf('/') + 1) + ":0\"";
        mvc.perform(post(missing + "/submit").header("If-Match", syntacticallyValid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_NOT_FOUND"));
        mvc.perform(post("/api/v1/ai-initiatives/invalid/submit").header("If-Match", tag))
                .andExpect(status().isBadRequest());
    }

    private AIInitiative draft() {
        AIInitiative initiative = AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("Conditional submit")
                .description("HTTP preconditions")
                .usesPersonalData(false)
                .impactsRights(false)
                .createdAt(Instant.parse("2026-09-21T14:00:00Z"))
                .build();
        repository.create(initiative);
        return initiative;
    }

    private static String path(AIInitiativeId id) {
        return "/api/v1/ai-initiatives/" + id.value();
    }
}
