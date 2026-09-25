package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.exception.AISystemAlreadyRegisteredException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.domain.event.AISystemRegistered;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.organization.domain.OrganizationId;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(PostgreSQLAISystemPersistenceIntegrationTest.PersistenceConfiguration.class)
class PostgreSQLAISystemPersistenceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-25T14:00:00.123456Z");
    private static final OrganizationId ORGANIZATION_ID =
            new OrganizationId(UUID.fromString("10000000-0000-0000-0000-000000000001"));

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
    private AISystemRepository repository;

    @Autowired
    private AISystemPersistenceMapper mapper;

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
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void creates_and_commits_all_fields_preserving_input_events_but_not_replaying_them_on_reload() {
        AISystem input = system(AISystemId.generate(), seedSource());
        var events = input.domainEvents();

        AISystem result = repository.create(input);

        assertSame(input, result);
        assertEquals(1, events.size());
        assertInstanceOf(AISystemRegistered.class, events.getFirst());
        assertEquals(events, input.domainEvents());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, rowCount());
        AISystem restored = reload(input.id());
        assertState(input, restored);
        assertTrue(restored.domainEvents().isEmpty());
        assertEquals("4", flyway.info().current().getVersion().toString());
        flyway.validate();
    }

    @Test
    void rejects_duplicate_source_and_preserves_original_row() {
        AIInitiativeId source = seedSource();
        AISystem original = repository.create(system(AISystemId.generate(), source));

        var duplicate = assertThrows(AISystemAlreadyRegisteredException.class,
                () -> repository.create(system(AISystemId.generate(), source)));

        assertEquals(source, duplicate.sourceInitiativeId());
        assertNotNull(duplicate.getCause());
        assertEquals(1, rowCount());
        assertState(original, reload(original.id()));
    }

    @Test
    void rejects_duplicate_id_without_overwriting_or_reporting_a_source_duplicate() {
        AISystem original = repository.create(system(AISystemId.generate(), seedSource()));
        AISystem duplicateId = system(original.id(), seedSource());

        assertThrows(PersistenceException.class, () -> repository.create(duplicateId));

        assertEquals(1, rowCount());
        assertState(original, reload(original.id()));
    }

    @Test
    void rejects_missing_source_without_reporting_a_source_duplicate() {
        AISystem system = system(AISystemId.generate(), AIInitiativeId.generate());

        assertThrows(PersistenceException.class, () -> repository.create(system));

        assertEquals(0, rowCount());
    }

    @Test
    void prevents_deleting_a_referenced_source_without_cascading() {
        AISystem original = repository.create(system(AISystemId.generate(), seedSource()));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from ai_initiatives where id = ?", original.sourceInitiativeId().value()));

        assertEquals(1, jdbc.queryForObject("select count(*) from ai_initiatives", Integer.class));
        assertState(original, reload(original.id()));
    }

    @Test
    void persists_long_text_without_arbitrary_limits() {
        AISystem input = AISystem.builder().id(AISystemId.generate()).organizationId(ORGANIZATION_ID)
                .sourceInitiativeId(seedSource()).name("n".repeat(300)).description("d".repeat(5000))
                .createdAt(CREATED_AT).build();

        repository.create(input);

        assertState(input, reload(input.id()));
    }

    @Test
    void joins_outer_transaction_and_rolls_back_an_already_flushed_insert() {
        AISystem input = system(AISystemId.generate(), seedSource());

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
    void status_check_rejects_unsupported_stored_state() {
        AISystem original = repository.create(system(AISystemId.generate(), seedSource()));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update ai_systems set status = 'ACTIVE' where id = ?", original.id().value()));

        assertState(original, reload(original.id()));
    }

    @Test
    void concurrent_inserts_for_one_source_have_one_committed_winner() throws Exception {
        AIInitiativeId source = seedSource();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> createConcurrently(system(AISystemId.generate(), source), ready, start));
            var second = workers.submit(() -> createConcurrently(system(AISystemId.generate(), source), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both transactions must reach the insert boundary");
            start.countDown();

            var outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter(AISystem.class::isInstance).count());
            assertEquals(1, outcomes.stream().filter(AISystemAlreadyRegisteredException.class::isInstance).count());
            assertEquals(1, rowCount());
            AISystem winner = (AISystem) outcomes.stream().filter(AISystem.class::isInstance).findFirst().orElseThrow();
            assertState(winner, reload(winner.id()));
            var duplicate = (AISystemAlreadyRegisteredException) outcomes.stream()
                    .filter(AISystemAlreadyRegisteredException.class::isInstance).findFirst().orElseThrow();
            assertEquals(source, duplicate.sourceInitiativeId());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void upgrades_v3_to_v4_preserving_initiative_rows_and_creating_named_constraints() {
        String schema = "system_upgrade";
        Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).target("3").load().migrate();
        String[] statuses = {"DRAFT", "APPROVED", "REJECTED"};
        for (int i = 0; i < statuses.length; i++) {
            jdbc.update("""
                    insert into system_upgrade.ai_initiatives
                        (id, organization_id, name, description, status, preliminary_risk,
                         uses_personal_data, impacts_rights, created_at, version, rejection_reason)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), ORGANIZATION_ID.value(), "Historical " + statuses[i], "Description",
                    statuses[i], i == 0 ? "NOT_ASSESSED" : "HIGH", true, false, Timestamp.from(CREATED_AT),
                    7L + i, i == 2 ? "Historical rejection" : null);
        }
        var before = jdbc.queryForList("select * from system_upgrade.ai_initiatives order by id");

        Flyway upgrade = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).target("4").load();

        assertEquals(1, upgrade.migrate().migrationsExecuted);
        assertEquals("4", upgrade.info().current().getVersion().toString());
        upgrade.validate();
        assertEquals(before, jdbc.queryForList("select * from system_upgrade.ai_initiatives order by id"));
        assertEquals(0, jdbc.queryForObject("select count(*) from system_upgrade.ai_systems", Integer.class));
        Set<String> constraints = Set.copyOf(jdbc.queryForList("""
                select constraint_name from information_schema.table_constraints
                where table_schema = 'system_upgrade' and table_name = 'ai_systems'
                """, String.class));
        assertTrue(constraints.containsAll(Set.of("pk_ai_systems", "ck_ai_systems_status",
                "uk_ai_systems_source_initiative", "fk_ai_systems_source_initiative")));
    }

    private Object createConcurrently(AISystem system, CountDownLatch ready, CountDownLatch start) {
        try {
            return new TransactionTemplate(transactionManager).execute(transaction -> {
                ready.countDown();
                try {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return repository.create(system);
            });
        } catch (AISystemAlreadyRegisteredException duplicate) {
            // The transaction has rolled back before its outcome is collected.
            return duplicate;
        }
    }

    private AIInitiativeId seedSource() {
        AIInitiativeId id = AIInitiativeId.generate();
        // DRAFT is intentional: persistence enforces provenance, not registration eligibility.
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'DRAFT', 'NOT_ASSESSED', false, false, ?)
                """, id.value(), ORGANIZATION_ID.value(), Timestamp.from(CREATED_AT));
        return id;
    }

    private AISystem system(AISystemId id, AIInitiativeId source) {
        return AISystem.builder().id(id).organizationId(ORGANIZATION_ID).sourceInitiativeId(source)
                .name("System").description("Description").createdAt(CREATED_AT).build();
    }

    private int rowCount() {
        return jdbc.queryForObject("select count(*) from ai_systems", Integer.class);
    }

    private AISystem reload(AISystemId id) {
        // Independent context and transaction ensure this is a database read, not a cached entity.
        var entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            var entity = entityManager.find(AISystemJpaEntity.class, id.value());
            assertNotNull(entity);
            AISystem restored = mapper.toDomain(entity);
            entityManager.getTransaction().commit();
            return restored;
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    private void assertState(AISystem expected, AISystem actual) {
        assertAll(
                () -> assertEquals(expected.id(), actual.id()),
                () -> assertEquals(expected.organizationId(), actual.organizationId()),
                () -> assertEquals(expected.sourceInitiativeId(), actual.sourceInitiativeId()),
                () -> assertEquals(expected.name(), actual.name()),
                () -> assertEquals(expected.description(), actual.description()),
                () -> assertEquals(expected.status(), actual.status()),
                () -> assertEquals(expected.createdAt(), actual.createdAt())
        );
    }

    static class FailureAfterFlush extends RuntimeException {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({GovernancePersistenceConfiguration.class, AIInitiativePersistenceConfiguration.class})
    static class PersistenceConfiguration {
    }
}
