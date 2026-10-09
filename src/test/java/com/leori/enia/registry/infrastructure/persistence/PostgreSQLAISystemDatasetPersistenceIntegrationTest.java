package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.registry.application.exception.DatasetAlreadyAssociatedWithAISystemException;
import com.leori.enia.registry.application.port.AISystemDatasetRepository;
import com.leori.enia.registry.domain.AISystemDataset;
import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(PostgreSQLAISystemDatasetPersistenceIntegrationTest.PersistenceConfiguration.class)
class PostgreSQLAISystemDatasetPersistenceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-06T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-06T11:00:00Z");
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
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void clearRows() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        jdbc.update("delete from ai_system_datasets");
        jdbc.update("delete from ai_datasets");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void creates_association_and_commits_fk_values() {
        AISystemId systemId = seedSystem();
        DatasetId datasetId = seedDataset(UUID.randomUUID(), "Dataset");
        AISystemDataset association = new AISystemDataset(systemId, datasetId, T1);

        AISystemDataset result = repository.create(association);

        assertSame(association, result);
        assertEquals(1, associationCount());
        assertEquals(systemId.value(), jdbc.queryForObject(
                "select system_id from ai_system_datasets where dataset_id = ?", UUID.class, datasetId.value()));
        assertEquals(datasetId.value(), jdbc.queryForObject(
                "select dataset_id from ai_system_datasets where system_id = ?", UUID.class, systemId.value()));
        assertEquals(T1, jdbc.queryForObject(
                "select associated_at from ai_system_datasets where system_id = ? and dataset_id = ?",
                Timestamp.class,
                systemId.value(),
                datasetId.value()
        ).toInstant());
        assertEquals("12", flyway.info().current().getVersion().toString());
        flyway.validate();
    }

    @Test
    void lists_datasets_for_one_system_in_association_time_then_dataset_id_order() {
        AISystemId systemA = seedSystem(UUID.randomUUID());
        AISystemId systemB = seedSystem(UUID.randomUUID());
        DatasetId d1 = seedDataset(UUID.fromString("00000000-0000-0000-0000-000000000001"), "D1");
        DatasetId d2 = seedDataset(UUID.fromString("00000000-0000-0000-0000-000000000003"), "D2");
        DatasetId d3 = seedDataset(UUID.fromString("00000000-0000-0000-0000-000000000002"), "D3");
        repository.create(new AISystemDataset(systemA, d1, T1));
        repository.create(new AISystemDataset(systemA, d2, T2));
        repository.create(new AISystemDataset(systemA, d3, T2));
        repository.create(new AISystemDataset(systemB, d2, T1));

        List<Dataset> datasets = repository.findDatasetsByAISystemId(systemA);

        assertEquals(List.of(d1, d3, d2), datasets.stream().map(Dataset::id).toList());
        assertEquals(List.of("D1", "D3", "D2"), datasets.stream().map(Dataset::name).toList());
        assertTrue(datasets.stream().allMatch(dataset -> dataset.domainEvents().isEmpty()));
    }

    @Test
    void existing_system_with_zero_associations_returns_empty_list() {
        AISystemId systemId = seedSystem();
        seedDataset(UUID.randomUUID(), "Dataset");

        assertTrue(repository.findDatasetsByAISystemId(systemId).isEmpty());
    }

    @Test
    void duplicate_composite_pk_is_rejected_and_translated() {
        AISystemId systemId = seedSystem();
        DatasetId datasetId = seedDataset(UUID.randomUUID(), "Dataset");
        repository.create(new AISystemDataset(systemId, datasetId, T1));

        DatasetAlreadyAssociatedWithAISystemException exception = assertThrows(
                DatasetAlreadyAssociatedWithAISystemException.class,
                () -> repository.create(new AISystemDataset(systemId, datasetId, T2))
        );

        assertEquals("Dataset already associated with AI system", exception.getMessage());
        assertEquals(1, associationCount());
    }

    @Test
    void missing_system_fk_is_enforced() {
        DatasetId datasetId = seedDataset(UUID.randomUUID(), "Dataset");

        assertThrows(PersistenceException.class,
                () -> repository.create(new AISystemDataset(AISystemId.generate(), datasetId, T1)));

        assertEquals(0, associationCount());
    }

    @Test
    void missing_dataset_fk_is_enforced() {
        AISystemId systemId = seedSystem();

        assertThrows(PersistenceException.class,
                () -> repository.create(new AISystemDataset(systemId, DatasetId.generate(), T1)));

        assertEquals(0, associationCount());
    }

    @Test
    void v12_creates_named_table_and_constraints_without_extra_indexes() {
        assertEquals(Set.of("pk_ai_system_datasets", "fk_ai_system_datasets_system",
                "fk_ai_system_datasets_dataset"), Set.copyOf(jdbc.queryForList("""
                select con.conname
                from pg_constraint con
                    join pg_class rel on rel.oid = con.conrelid
                    join pg_namespace nsp on nsp.oid = rel.relnamespace
                where nsp.nspname = current_schema()
                    and rel.relname = 'ai_system_datasets'
                    and con.contype in ('p', 'f')
                """, String.class)));
        assertEquals(List.of("system_id", "dataset_id"), jdbc.queryForList("""
                select column_name from information_schema.key_column_usage
                where table_schema = current_schema() and table_name = 'ai_system_datasets'
                    and constraint_name = 'pk_ai_system_datasets'
                order by ordinal_position
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from pg_indexes
                where schemaname = current_schema() and tablename = 'ai_system_datasets'
                """, Integer.class));
    }

    private AISystemId seedSystem() {
        return seedSystem(UUID.randomUUID());
    }

    private AISystemId seedSystem(UUID initiativeId) {
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

    private DatasetId seedDataset(UUID id, String name) {
        jdbc.update("""
                insert into ai_datasets (id, name, description, created_at)
                values (?, ?, 'Description', ?)
                """, id, name, Timestamp.from(CREATED_AT));
        return new DatasetId(id);
    }

    private int associationCount() {
        return jdbc.queryForObject("select count(*) from ai_system_datasets", Integer.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RegistryPersistenceConfiguration.class, GovernancePersistenceConfiguration.class,
            AIInitiativePersistenceConfiguration.class})
    static class PersistenceConfiguration {
    }
}
