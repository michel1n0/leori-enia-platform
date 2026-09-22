package com.leori.enia.initiative.infrastructure.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LeoriEniaApplication.class)
@AutoConfigureMockMvc
class AIInitiativeGetIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-21T14:00:00Z");

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
    private AIInitiativeRepository repository;

    @Test
    void get_existing_initiative_returns_only_public_state() throws Exception {
        AIInitiative initiative = AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("HTTP read")
                .description("Stored in PostgreSQL")
                .usesPersonalData(true)
                .impactsRights(false)
                .createdAt(CREATED_AT)
                .build();
        repository.create(initiative);

        mvc.perform(get("/api/v1/ai-initiatives/{id}", initiative.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(initiative.id().value().toString()))
                .andExpect(jsonPath("$.organizationId").value(initiative.organizationId().value().toString()))
                .andExpect(jsonPath("$.name").value("HTTP read"))
                .andExpect(jsonPath("$.description").value("Stored in PostgreSQL"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.preliminaryRisk").value("NOT_ASSESSED"))
                .andExpect(jsonPath("$.usesPersonalData").value(true))
                .andExpect(jsonPath("$.impactsRights").value(false))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.domainEvents").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.initiative").doesNotExist())
                .andExpect(header().string("ETag", "\"ai-initiative:" + initiative.id().value() + ":0\""));
    }

    @Test
    void get_unknown_initiative_returns_stable_not_found_error() throws Exception {
        mvc.perform(get("/api/v1/ai-initiatives/{id}", AIInitiativeId.generate().value()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_INITIATIVE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("AI initiative not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformed_uuid_returns_stable_bad_request_error() throws Exception {
        mvc.perform(get("/api/v1/ai-initiatives/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INITIATIVE_ID"))
                .andExpect(jsonPath("$.message").value("Initiative id must be a UUID"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
