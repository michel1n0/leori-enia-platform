package com.leori.enia.registry.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leori.enia.LeoriEniaApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LeoriEniaApplication.class)
@AutoConfigureMockMvc
class AISystemDatasetListIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-06T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-06T11:00:00Z");
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
    void lists_associated_datasets_for_ai_system_in_deterministic_order() throws Exception {
        UUID systemA = seedSystem();
        UUID systemB = seedSystem();
        UUID d1 = seedDataset(UUID.fromString("00000000-0000-0000-0000-000000000001"), "D1");
        UUID d2 = seedDataset(UUID.fromString("00000000-0000-0000-0000-000000000003"), "D2");
        UUID d3 = seedDataset(UUID.fromString("00000000-0000-0000-0000-000000000002"), "D3");
        associate(systemA, d1, T1);
        associate(systemA, d2, T2);
        associate(systemA, d3, T2);
        associate(systemB, d2, T1);

        var result = mvc.perform(get("/api/v1/ai-systems/{aiSystemId}/datasets", systemA))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("ETag"))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertEquals(3, body.size());
        assertDataset(body.get(0), d1, "D1");
        assertDataset(body.get(1), d3, "D3");
        assertDataset(body.get(2), d2, "D2");
    }

    @Test
    void existing_system_with_zero_associated_datasets_returns_empty_array() throws Exception {
        UUID systemId = seedSystem();
        seedDataset(UUID.randomUUID(), "Unassociated");

        var result = mvc.perform(get("/api/v1/ai-systems/{aiSystemId}/datasets", systemId))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("[]", result.getResponse().getContentAsString());
    }

    @Test
    void missing_ai_system_returns_not_found() throws Exception {
        mvc.perform(get("/api/v1/ai-systems/{aiSystemId}/datasets", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_SYSTEM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("AI system not found"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformed_ai_system_id_returns_bad_request() throws Exception {
        mvc.perform(get("/api/v1/ai-systems/not-a-uuid/datasets"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AI_SYSTEM_ID"))
                .andExpect(jsonPath("$.message").value("Invalid AI system id"));
    }

    private void assertDataset(JsonNode dataset, UUID id, String name) {
        assertEquals(4, dataset.size());
        assertEquals(id.toString(), dataset.get("id").asText());
        assertEquals(name, dataset.get("name").asText());
        assertEquals("Description", dataset.get("description").asText());
        assertEquals(CREATED_AT.toString(), dataset.get("createdAt").asText());
        assertTrue(!dataset.has("domainEvents"));
        assertTrue(!dataset.has("version"));
        assertTrue(!dataset.has("revision"));
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

    private UUID seedDataset(UUID datasetId, String name) {
        jdbc.update("""
                insert into ai_datasets (id, name, description, created_at)
                values (?, ?, 'Description', ?)
                """, datasetId, name, Timestamp.from(CREATED_AT));
        return datasetId;
    }

    private void associate(UUID systemId, UUID datasetId, Instant associatedAt) {
        jdbc.update("""
                insert into ai_system_datasets (system_id, dataset_id, associated_at)
                values (?, ?, ?)
                """, systemId, datasetId, Timestamp.from(associatedAt));
    }
}
