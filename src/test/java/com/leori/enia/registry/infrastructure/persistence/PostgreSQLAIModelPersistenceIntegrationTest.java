package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.registry.application.port.AIModelRepository;
import com.leori.enia.registry.domain.AIModel;
import com.leori.enia.registry.domain.AIModelId;
import com.leori.enia.registry.domain.event.AIModelRegistered;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
@SpringJUnitConfig(PostgreSQLAIModelPersistenceIntegrationTest.PersistenceConfiguration.class)
class PostgreSQLAIModelPersistenceIntegrationTest {

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
    private AIModelRepository repository;

    @Autowired
    private AIModelPersistenceMapper mapper;

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
        jdbc.update("delete from ai_models");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void creates_and_commits_all_fields_preserving_input_events_but_not_replaying_them_on_reload() {
        AISystemId systemId = seedSystem();
        AIModel input = model(AIModelId.generate(), systemId);
        var events = input.domainEvents();

        AIModel result = repository.create(input);

        assertSame(input, result);
        assertEquals(1, events.size());
        assertInstanceOf(AIModelRegistered.class, events.getFirst());
        assertEquals(events, input.domainEvents());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, rowCount());
        AIModel restored = reload(input.id());
        assertState(input, restored);
        assertTrue(restored.domainEvents().isEmpty());
        assertEquals("11", flyway.info().current().getVersion().toString());
        flyway.validate();
    }

    @Test
    void rejects_duplicate_id_without_overwriting() {
        AISystemId systemId = seedSystem();
        AIModel original = repository.create(model(AIModelId.generate(), systemId));
        AIModel duplicateId = model(original.id(), systemId);

        assertThrows(PersistenceException.class, () -> repository.create(duplicateId));

        assertEquals(1, rowCount());
        assertState(original, reload(original.id()));
    }

    @Test
    void rejects_missing_system_id_via_fk_constraint() {
        AIModel model = model(AIModelId.generate(), AISystemId.generate());

        assertThrows(PersistenceException.class, () -> repository.create(model));

        assertEquals(0, rowCount());
    }

    @Test
    void prevents_deleting_a_referenced_system_without_cascading() {
        AISystemId systemId = seedSystem();
        AIModel original = repository.create(model(AIModelId.generate(), systemId));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from ai_systems where id = ?", systemId.value()));

        assertEquals(1, jdbc.queryForObject("select count(*) from ai_systems", Integer.class));
        assertState(original, reload(original.id()));
    }

    @Test
    void persists_long_text_without_arbitrary_limits() {
        AISystemId systemId = seedSystem();
        AIModel input = AIModel.builder()
                .id(AIModelId.generate())
                .systemId(systemId)
                .name("n".repeat(300))
                .description("d".repeat(5000))
                .provider("p".repeat(200))
                .createdAt(CREATED_AT)
                .build();

        repository.create(input);

        assertState(input, reload(input.id()));
    }

    @Test
    void find_by_id_returns_committed_model_without_replaying_events() {
        AISystemId systemId = seedSystem();
        AIModel original = repository.create(model(AIModelId.generate(), systemId));

        var result = repository.findById(original.id());

        assertTrue(result.isPresent());
        assertState(original, result.orElseThrow());
        assertTrue(result.orElseThrow().domainEvents().isEmpty());
    }

    @Test
    void find_by_id_returns_empty_when_missing() {
        assertTrue(repository.findById(AIModelId.generate()).isEmpty());
    }

    @Test
    void joins_outer_transaction_and_rolls_back_an_already_flushed_insert() {
        AISystemId systemId = seedSystem();
        AIModel input = model(AIModelId.generate(), systemId);

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
    void upgrades_v4_to_v5_preserving_system_rows_and_creating_named_constraints() {
        String schema = "model_upgrade";
        Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).target("4").load().migrate();

        // Seed an initiative and a system in the isolated schema.
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into model_upgrade.ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'DRAFT', 'NOT_ASSESSED', false, false, ?)
                """, initiativeId, ORGANIZATION_UUID, Timestamp.from(CREATED_AT));
        UUID systemId = UUID.randomUUID();
        jdbc.update("""
                insert into model_upgrade.ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'System', 'Desc', 'REGISTERED', ?)
                """, systemId, ORGANIZATION_UUID, initiativeId, Timestamp.from(CREATED_AT));
        var systemsBefore = jdbc.queryForList("select * from model_upgrade.ai_systems order by id");

        Flyway upgrade = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).target("5").load();

        assertEquals(1, upgrade.migrate().migrationsExecuted);
        assertEquals("5", upgrade.info().current().getVersion().toString());
        upgrade.validate();
        assertEquals(systemsBefore, jdbc.queryForList("select * from model_upgrade.ai_systems order by id"));
        assertEquals(0, jdbc.queryForObject("select count(*) from model_upgrade.ai_models", Integer.class));
        Set<String> constraints = Set.copyOf(jdbc.queryForList("""
                select constraint_name from information_schema.table_constraints
                where table_schema = 'model_upgrade' and table_name = 'ai_models'
                """, String.class));
        assertTrue(constraints.containsAll(Set.of("pk_ai_models", "fk_ai_models_system")));
    }

    private AISystemId seedSystem() {
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'DRAFT', 'NOT_ASSESSED', false, false, ?)
                """, initiativeId, ORGANIZATION_UUID, Timestamp.from(CREATED_AT));
        AISystemId systemId = AISystemId.generate();
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'System', 'Desc', 'REGISTERED', ?)
                """, systemId.value(), ORGANIZATION_UUID, initiativeId, Timestamp.from(CREATED_AT));
        return systemId;
    }

    private AIModel model(AIModelId id, AISystemId systemId) {
        return AIModel.builder()
                .id(id)
                .systemId(systemId)
                .name("Model")
                .description("Description")
                .provider("OpenAI")
                .createdAt(CREATED_AT)
                .build();
    }

    private int rowCount() {
        return jdbc.queryForObject("select count(*) from ai_models", Integer.class);
    }

    private AIModel reload(AIModelId id) {
        // Independent context and transaction ensure this is a database read, not a cached entity.
        var entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            var entity = entityManager.find(AIModelJpaEntity.class, id.value());
            assertNotNull(entity);
            AIModel restored = mapper.toDomain(entity);
            entityManager.getTransaction().commit();
            return restored;
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    private void assertState(AIModel expected, AIModel actual) {
        assertAll(
                () -> assertEquals(expected.id(), actual.id()),
                () -> assertEquals(expected.systemId(), actual.systemId()),
                () -> assertEquals(expected.name(), actual.name()),
                () -> assertEquals(expected.description(), actual.description()),
                () -> assertEquals(expected.provider(), actual.provider()),
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
