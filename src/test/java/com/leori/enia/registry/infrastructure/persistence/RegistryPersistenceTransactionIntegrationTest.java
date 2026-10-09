package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.registry.application.port.AISystemDatasetRepository;
import com.leori.enia.registry.domain.AISystemDataset;
import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringJUnitConfig(RegistryPersistenceTransactionIntegrationTest.PersistenceConfiguration.class)
class RegistryPersistenceTransactionIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T09:00:00Z");
    private static final Instant ASSOCIATED_AT = Instant.parse("2026-10-06T10:00:00Z");
    private static final UUID ORGANIZATION_UUID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private AISystemDatasetRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearRows() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        jdbc.update("delete from ai_system_datasets");
        jdbc.update("delete from ai_datasets");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void create_joins_outer_transaction_and_rolls_back_flushed_association() {
        AISystemId systemId = seedSystem();
        DatasetId datasetId = seedDataset("Dataset");
        AISystemDataset association = new AISystemDataset(systemId, datasetId, ASSOCIATED_AT);

        assertThrows(FailureAfterFlush.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(transaction -> {
                    assertSame(association, repository.create(association));
                    assertEquals(1, associationCount(systemId, datasetId));
                    throw new FailureAfterFlush();
                }));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(0, associationCount(systemId, datasetId));
    }

    @Test
    void find_datasets_by_system_id_returns_committed_associated_dataset() {
        AISystemId systemId = seedSystem();
        DatasetId datasetId = seedDataset("Dataset");
        jdbc.update("""
                insert into ai_system_datasets (system_id, dataset_id, associated_at)
                values (?, ?, ?)
                """, systemId.value(), datasetId.value(), Timestamp.from(ASSOCIATED_AT));

        List<Dataset> result = repository.findDatasetsByAISystemId(systemId);

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(List.of(datasetId), result.stream().map(Dataset::id).toList());
        assertEquals(List.of("Dataset"), result.stream().map(Dataset::name).toList());
        assertEquals(List.of("Description"), result.stream().map(Dataset::description).toList());
        assertEquals(List.of(CREATED_AT), result.stream().map(Dataset::createdAt).toList());
    }

    private AISystemId seedSystem() {
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'APPROVED', 'HIGH', false, false, ?)
                """, initiativeId, ORGANIZATION_UUID, Timestamp.from(CREATED_AT));
        AISystemId systemId = AISystemId.generate();
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'AI System', 'System description', 'REGISTERED', ?)
                """, systemId.value(), ORGANIZATION_UUID, initiativeId, Timestamp.from(CREATED_AT));
        return systemId;
    }

    private DatasetId seedDataset(String name) {
        DatasetId datasetId = DatasetId.generate();
        jdbc.update("""
                insert into ai_datasets (id, name, description, created_at)
                values (?, ?, 'Description', ?)
                """, datasetId.value(), name, Timestamp.from(CREATED_AT));
        return datasetId;
    }

    private int associationCount(AISystemId systemId, DatasetId datasetId) {
        return jdbc.queryForObject("""
                select count(*) from ai_system_datasets
                where system_id = ? and dataset_id = ?
                """, Integer.class, systemId.value(), datasetId.value());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RegistryPersistenceConfiguration.class, GovernancePersistenceConfiguration.class,
            AIInitiativePersistenceConfiguration.class})
    static class PersistenceConfiguration {
    }

    static class FailureAfterFlush extends RuntimeException {
    }
}
