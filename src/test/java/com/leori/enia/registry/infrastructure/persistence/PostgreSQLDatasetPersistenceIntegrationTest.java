package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.registry.application.port.DatasetRepository;
import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;
import com.leori.enia.registry.domain.event.DatasetRegistered;
import jakarta.persistence.EntityManagerFactory;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(PostgreSQLDatasetPersistenceIntegrationTest.PersistenceConfiguration.class)
class PostgreSQLDatasetPersistenceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-28T14:00:00.123456Z");
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
    private DatasetRepository repository;

    @Autowired
    private DatasetPersistenceMapper mapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void clearRows() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        jdbc.update("delete from ai_datasets");
    }

    @Test
    void creates_and_commits_all_fields_preserving_input_events_but_not_replaying_them_on_reload() {
        Dataset input = dataset(DatasetId.generate());
        var events = input.domainEvents();

        Dataset result = repository.create(input);

        assertSame(input, result);
        assertEquals(1, events.size());
        assertInstanceOf(DatasetRegistered.class, events.getFirst());
        assertEquals(events, input.domainEvents());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, rowCount());
        jdbc.queryForObject("select id, name, description, created_at from ai_datasets where id = ?",
                (row, rowNumber) -> {
                    assertAll(
                            () -> assertEquals(input.id().value(), row.getObject("id", UUID.class)),
                            () -> assertEquals(input.name(), row.getString("name")),
                            () -> assertEquals(input.description(), row.getString("description")),
                            () -> assertEquals(CREATED_AT, row.getTimestamp("created_at").toInstant())
                    );
                    return true;
                }, input.id().value());
        Dataset restored = reload(input.id());
        assertState(input, restored);
        assertTrue(restored.domainEvents().isEmpty());
        assertEquals("12", flyway.info().current().getVersion().toString());
        flyway.validate();
    }

    @Test
    void rejects_duplicate_id_without_overwriting() {
        Dataset original = repository.create(dataset(DatasetId.generate()));
        Dataset duplicateId = dataset(original.id());

        assertThrows(PersistenceException.class, () -> repository.create(duplicateId));

        assertEquals(1, rowCount());
        assertState(original, reload(original.id()));
    }

    @Test
    void persists_long_text_without_arbitrary_limits() {
        Dataset input = Dataset.builder()
                .id(DatasetId.generate())
                .name("n".repeat(300))
                .description("d".repeat(5000))
                .createdAt(CREATED_AT)
                .build();

        repository.create(input);

        assertState(input, reload(input.id()));
    }

    @Test
    void find_by_id_returns_committed_dataset_without_replaying_events() {
        Dataset original = repository.create(dataset(DatasetId.generate()));

        var result = repository.findById(original.id());

        assertTrue(result.isPresent());
        assertState(original, result.orElseThrow());
        assertTrue(result.orElseThrow().domainEvents().isEmpty());
    }

    @Test
    void find_by_id_returns_empty_when_missing() {
        assertTrue(repository.findById(DatasetId.generate()).isEmpty());
    }

    @Test
    void joins_outer_transaction_and_rolls_back_an_already_flushed_insert() {
        Dataset input = dataset(DatasetId.generate());

        assertThrows(FailureAfterFlush.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(transaction -> {
                    assertSame(input, repository.create(input));
                    assertEquals(1, rowCount());
                    throw new FailureAfterFlush();
                }));

        assertEquals(0, rowCount());
        assertEquals(1, input.domainEvents().size());
    }

    @Test
    void upgrades_v5_to_v6_preserving_existing_rows_and_creating_dataset_structure() {
        String schema = "dataset_upgrade";
        Flyway baseline = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).target("5").load();
        baseline.migrate();
        assertEquals("5", baseline.info().current().getVersion().toString());

        // These existing aggregates are migration fixtures, not Dataset relationships.
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into dataset_upgrade.ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'DRAFT', 'NOT_ASSESSED', false, false, ?)
                """, initiativeId, ORGANIZATION_UUID, Timestamp.from(CREATED_AT));
        UUID systemId = UUID.randomUUID();
        jdbc.update("""
                insert into dataset_upgrade.ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'System', 'Desc', 'REGISTERED', ?)
                """, systemId, ORGANIZATION_UUID, initiativeId, Timestamp.from(CREATED_AT));
        jdbc.update("""
                insert into dataset_upgrade.ai_models (id, system_id, name, description, provider, created_at)
                values (?, ?, 'Model', 'Description', 'Provider', ?)
                """, UUID.randomUUID(), systemId, Timestamp.from(CREATED_AT));
        var initiativesBefore = jdbc.queryForList("select * from dataset_upgrade.ai_initiatives order by id");
        var systemsBefore = jdbc.queryForList("select * from dataset_upgrade.ai_systems order by id");
        var modelsBefore = jdbc.queryForList("select * from dataset_upgrade.ai_models order by id");

        Flyway upgrade = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).target("6").load();

        assertEquals(1, upgrade.migrate().migrationsExecuted);
        assertEquals("6", upgrade.info().current().getVersion().toString());
        upgrade.validate();
        assertEquals(initiativesBefore, jdbc.queryForList("select * from dataset_upgrade.ai_initiatives order by id"));
        assertEquals(systemsBefore, jdbc.queryForList("select * from dataset_upgrade.ai_systems order by id"));
        assertEquals(modelsBefore, jdbc.queryForList("select * from dataset_upgrade.ai_models order by id"));
        assertEquals(0, jdbc.queryForObject("select count(*) from dataset_upgrade.ai_datasets", Integer.class));
        Set<String> columns = Set.copyOf(jdbc.queryForList("""
                select column_name || ':' || data_type || ':' || is_nullable
                from information_schema.columns
                where table_schema = 'dataset_upgrade' and table_name = 'ai_datasets'
                """, String.class));
        assertEquals(Set.of("id:uuid:NO", "name:text:NO", "description:text:NO",
                "created_at:timestamp with time zone:NO"), columns);
        assertEquals(List.of("pk_ai_datasets"), jdbc.queryForList("""
                select constraint_name from information_schema.table_constraints
                where table_schema = 'dataset_upgrade' and table_name = 'ai_datasets'
                    and constraint_type = 'PRIMARY KEY'
                """, String.class));
        assertEquals(List.of("id"), jdbc.queryForList("""
                select column_name from information_schema.key_column_usage
                where table_schema = 'dataset_upgrade' and table_name = 'ai_datasets'
                    and constraint_name = 'pk_ai_datasets'
                """, String.class));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_schema = 'dataset_upgrade' and table_name = 'ai_datasets'
                    and constraint_type in ('FOREIGN KEY', 'UNIQUE')
                """, Integer.class));
    }

    private Dataset dataset(DatasetId id) {
        return Dataset.builder()
                .id(id)
                .name("Dataset")
                .description("Description")
                .createdAt(CREATED_AT)
                .build();
    }

    private int rowCount() {
        return jdbc.queryForObject("select count(*) from ai_datasets", Integer.class);
    }

    private Dataset reload(DatasetId id) {
        // Independent context and transaction ensure this is a database read, not a cached entity.
        var entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            var entity = entityManager.find(DatasetJpaEntity.class, id.value());
            assertNotNull(entity);
            Dataset restored = mapper.toDomain(entity);
            entityManager.getTransaction().commit();
            return restored;
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    private void assertState(Dataset expected, Dataset actual) {
        assertAll(
                () -> assertEquals(expected.id(), actual.id()),
                () -> assertEquals(expected.name(), actual.name()),
                () -> assertEquals(expected.description(), actual.description()),
                () -> assertEquals(expected.createdAt(), actual.createdAt())
        );
    }

    static class FailureAfterFlush extends RuntimeException {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RegistryPersistenceConfiguration.class, GovernancePersistenceConfiguration.class,
            AIInitiativePersistenceConfiguration.class})
    static class PersistenceConfiguration {
    }
}
