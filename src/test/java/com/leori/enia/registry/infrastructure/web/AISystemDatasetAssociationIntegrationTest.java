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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
        classes = {LeoriEniaApplication.class, AISystemDatasetAssociationIntegrationTest.FixedClockConfiguration.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
class AISystemDatasetAssociationIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T09:00:00Z");
    private static final Instant ASSOCIATED_AT = Instant.parse("2026-10-06T10:15:30Z");
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
        jdbc.update("delete from ai_system_datasets");
        jdbc.update("delete from ai_datasets");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void associates_existing_dataset_with_ai_system() throws Exception {
        UUID systemId = seedSystem();
        UUID datasetId = seedDataset("Dataset");
        String uri = "/api/v1/ai-systems/" + systemId + "/datasets/" + datasetId;

        var result = mvc.perform(post(uri))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", uri))
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertEquals(3, body.size());
        assertEquals(systemId.toString(), body.get("aiSystemId").asText());
        assertEquals(datasetId.toString(), body.get("datasetId").asText());
        assertEquals(ASSOCIATED_AT.toString(), body.get("associatedAt").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from ai_system_datasets", Integer.class));
    }

    @Test
    void missing_ai_system_returns_not_found() throws Exception {
        UUID datasetId = seedDataset("Dataset");

        mvc.perform(post("/api/v1/ai-systems/{aiSystemId}/datasets/{datasetId}", UUID.randomUUID(), datasetId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_SYSTEM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("AI system not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void missing_dataset_returns_not_found() throws Exception {
        UUID systemId = seedSystem();

        mvc.perform(post("/api/v1/ai-systems/{aiSystemId}/datasets/{datasetId}", systemId, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Dataset not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void duplicate_association_returns_conflict_without_sql_details() throws Exception {
        UUID systemId = seedSystem();
        UUID datasetId = seedDataset("Dataset");
        mvc.perform(post("/api/v1/ai-systems/{aiSystemId}/datasets/{datasetId}", systemId, datasetId))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/ai-systems/{aiSystemId}/datasets/{datasetId}", systemId, datasetId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATASET_ALREADY_ASSOCIATED_WITH_AI_SYSTEM"))
                .andExpect(jsonPath("$.message").value("Dataset already associated with AI system"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals(1, jdbc.queryForObject("select count(*) from ai_system_datasets", Integer.class));
    }

    @Test
    void malformed_ai_system_id_returns_bad_request() throws Exception {
        mvc.perform(post("/api/v1/ai-systems/not-a-uuid/datasets/{datasetId}", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AI_SYSTEM_ID"))
                .andExpect(jsonPath("$.message").value("Invalid AI system id"));
    }

    @Test
    void malformed_dataset_id_returns_bad_request() throws Exception {
        mvc.perform(post("/api/v1/ai-systems/{aiSystemId}/datasets/not-a-uuid", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATASET_ID"))
                .andExpect(jsonPath("$.message").value("Invalid dataset id"));
    }

    private UUID seedSystem() {
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at, version, rejection_reason)
                values (?, ?, 'Source initiative', 'Source description', 'APPROVED', 'HIGH', false, false, ?, 0, null)
                """, initiativeId, ORGANIZATION_ID, Timestamp.from(CREATED_AT));
        UUID systemId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'AI System', 'System description', 'REGISTERED', ?)
                """, systemId, ORGANIZATION_ID, initiativeId, Timestamp.from(CREATED_AT));
        return systemId;
    }

    private UUID seedDataset(String name) {
        UUID datasetId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_datasets (id, name, description, created_at)
                values (?, ?, 'Description', ?)
                """, datasetId, name, Timestamp.from(CREATED_AT));
        return datasetId;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock registryClock() {
            return Clock.fixed(ASSOCIATED_AT, ZoneOffset.UTC);
        }
    }
}
