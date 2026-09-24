package com.leori.enia.initiative.infrastructure.persistence;

import com.leori.enia.initiative.application.ApproveAIInitiativeCommand;
import com.leori.enia.initiative.application.ApproveAIInitiativeUseCase;
import com.leori.enia.initiative.application.AssessRiskAIInitiativeCommand;
import com.leori.enia.initiative.application.AssessRiskAIInitiativeUseCase;
import com.leori.enia.initiative.application.RejectAIInitiativeCommand;
import com.leori.enia.initiative.application.RejectAIInitiativeUseCase;
import com.leori.enia.initiative.application.StartAssessmentAIInitiativeCommand;
import com.leori.enia.initiative.application.StartAssessmentAIInitiativeUseCase;
import com.leori.enia.initiative.application.SubmitAIInitiativeCommand;
import com.leori.enia.initiative.application.SubmitAIInitiativeUseCase;
import com.leori.enia.initiative.application.ExpectedRevision;
import com.leori.enia.initiative.application.exception.AIInitiativeRevisionMismatchException;
import jakarta.persistence.EntityManager;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.application.port.SavedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.initiative.infrastructure.configuration.AIInitiativeApplicationConfiguration;
import com.leori.enia.organization.domain.OrganizationId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(PostgreSQLAIInitiativeOptimisticLockingIntegrationTest.TestConfiguration.class)
class PostgreSQLAIInitiativeOptimisticLockingIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-18T14:00:00.123456Z");

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
    private JpaAIInitiativeRepositoryAdapter adapter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApproveAIInitiativeUseCase approve;

    @Autowired
    private SubmitAIInitiativeUseCase submit;

    @Autowired
    private StartAssessmentAIInitiativeUseCase startAssessment;

    @Autowired
    private AssessRiskAIInitiativeUseCase assessRisk;

    @Autowired
    private RejectAIInitiativeUseCase reject;

    @Autowired
    private PausingRepository pausingRepository;

    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        transactions = new TransactionTemplate(transactionManager);
        jdbc.update("delete from ai_initiatives");
        pausingRepository.reset();
    }

    @Test
    void should_reject_a_stale_detached_copy_after_the_winner_commits() {
        AIInitiative initial = createRiskAssessedInitiative();
        transactions.execute(status -> adapter.create(initial));

        // Each read ends its own transaction/persistence context. Both copies
        // retain the original version after those contexts have closed.
        LoadedAIInitiative a = load(initial.id());
        LoadedAIInitiative b = load(initial.id());
        assertNotSame(a.initiative(), b.initiative());
        assertEquals(0L, a.version());
        assertEquals(a.version(), b.version());

        a.initiative().approve(NOW);
        b.initiative().reject("Residual risk unacceptable", NOW);
        transactions.execute(status -> adapter.save(a));
        assertWinner(initial.id(), 1L);

        ObjectOptimisticLockingFailureException conflict = assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> transactions.execute(status -> adapter.save(b)));

        System.out.println("Detached stale-write exception: " + conflict.getClass().getName());
        assertEquals(initial.id().value(), conflict.getIdentifier());
        assertWinner(initial.id(), 1L);
    }

    @Test
    void should_preserve_advancing_versions_across_successive_use_cases() {
        AIInitiative initial = createDraftInitiative();
        transactions.execute(status -> adapter.create(initial));

        submit.execute(new SubmitAIInitiativeCommand(initial.id(), new ExpectedRevision(initial.id(), 0)));
        assertEquals(1L, load(initial.id()).version());
        startAssessment.execute(new StartAssessmentAIInitiativeCommand(
                initial.id(), new ExpectedRevision(initial.id(), 1)));
        assertEquals(2L, load(initial.id()).version());
        assessRisk.execute(new AssessRiskAIInitiativeCommand(
                initial.id(), RiskLevel.HIGH, new ExpectedRevision(initial.id(), 2)));
        assertEquals(3L, load(initial.id()).version());
        var approved = approve.execute(new ApproveAIInitiativeCommand(
                initial.id(), new ExpectedRevision(initial.id(), 3)));
        assertEquals(4L, approved.revision());

        assertWinner(initial.id(), 4L);
    }

    @Test
    void saved_result_uses_the_actual_postgresql_hibernate_version() {
        AIInitiative initial = createDraftInitiative();
        transactions.execute(status -> adapter.create(initial));
        assertEquals(0L, load(initial.id()).version());

        LoadedAIInitiative first = load(initial.id());
        first.initiative().submit(NOW);
        SavedAIInitiative afterSubmit = transactions.execute(status -> adapter.save(first));
        assertEquals(1L, afterSubmit.version());
        assertEquals(1L, load(initial.id()).version());

        LoadedAIInitiative second = load(initial.id());
        second.initiative().startAssessment();
        SavedAIInitiative afterAssessment = transactions.execute(status -> adapter.save(second));
        assertEquals(2L, afterAssessment.version());
        assertEquals(2L, load(initial.id()).version());
    }

    @Test
    void stale_client_revision_is_rejected_before_domain_mutation() {
        AIInitiative initial = createDraftInitiative();
        transactions.execute(status -> adapter.create(initial));
        submit.execute(new SubmitAIInitiativeCommand(initial.id(), new ExpectedRevision(initial.id(), 0)));

        assertThrows(AIInitiativeRevisionMismatchException.class,
                () -> submit.execute(new SubmitAIInitiativeCommand(initial.id(), new ExpectedRevision(initial.id(), 0))));
        assertEquals(InitiativeStatus.SUBMITTED, load(initial.id()).initiative().status());
        assertEquals(1L, load(initial.id()).version());
    }

    @Test
    void two_valid_submit_preconditions_still_have_one_optimistic_winner() throws Exception {
        AIInitiative initial = createDraftInitiative();
        transactions.execute(status -> adapter.create(initial));
        pausingRepository.pauseNextLoad.set(true);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var loser = executor.submit(() -> submit.execute(
                    new SubmitAIInitiativeCommand(initial.id(), new ExpectedRevision(initial.id(), 0))));
            try {
                assertTrue(pausingRepository.loaded.await(15, TimeUnit.SECONDS));
                assertEquals(InitiativeStatus.SUBMITTED, submit.execute(
                        new SubmitAIInitiativeCommand(initial.id(), new ExpectedRevision(initial.id(), 0)))
                        .details().status());
            } finally {
                pausingRepository.resume.countDown();
            }
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> loser.get(15, TimeUnit.SECONDS));
            AIInitiativeRevisionMismatchException translated = assertInstanceOf(
                    AIInitiativeRevisionMismatchException.class, failure.getCause());
            assertInstanceOf(ObjectOptimisticLockingFailureException.class, translated.getCause());
            assertEquals(InitiativeStatus.SUBMITTED, load(initial.id()).initiative().status());
            assertEquals(1L, load(initial.id()).version());
        }
    }

    @Test
    void commit_time_conflict_is_translated_outside_the_transaction_interceptor() throws Exception {
        AIInitiative initial = createRiskAssessedInitiative();
        transactions.execute(status -> adapter.create(initial));
        pausingRepository.pauseNextDeferredSave.set(true);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var loser = executor.submit(() -> reject.execute(
                    new RejectAIInitiativeCommand(initial.id(), "Residual risk unacceptable")));
            try {
                assertTrue(pausingRepository.deferredSaveReached.await(15, TimeUnit.SECONDS));
                assertEquals(InitiativeStatus.APPROVED,
                        approve.execute(new ApproveAIInitiativeCommand(
                                initial.id(), new ExpectedRevision(initial.id(), 0))).details().status());
            } finally {
                pausingRepository.resumeDeferredSave.countDown();
            }
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> loser.get(15, TimeUnit.SECONDS));
            AIInitiativeRevisionMismatchException translated = assertInstanceOf(
                    AIInitiativeRevisionMismatchException.class, failure.getCause());
            assertInstanceOf(ObjectOptimisticLockingFailureException.class, translated.getCause());
            assertWinner(initial.id(), 1L);
        }
    }

    @Test
    void should_reject_a_stale_production_use_case_at_commit() throws Exception {
        AIInitiative initial = createRiskAssessedInitiative();
        transactions.execute(status -> adapter.create(initial));
        pausingRepository.pauseNextLoad.set(true);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var staleExecution = executor.submit(() -> reject.execute(
                    new RejectAIInitiativeCommand(initial.id(), "Residual risk unacceptable")));
            try {
                assertTrue(pausingRepository.loaded.await(15, TimeUnit.SECONDS), "B must load first");
                // B's application transaction is still open with version 0.
                var winner = approve.execute(new ApproveAIInitiativeCommand(
                        initial.id(), new ExpectedRevision(initial.id(), 0)));
                assertEquals(InitiativeStatus.APPROVED, winner.details().status());
                assertWinner(initial.id(), 1L);
            } finally {
                pausingRepository.resume.countDown();
            }

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> staleExecution.get(15, TimeUnit.SECONDS));
            AIInitiativeRevisionMismatchException conflict = assertInstanceOf(
                    AIInitiativeRevisionMismatchException.class, failure.getCause());
            assertInstanceOf(ObjectOptimisticLockingFailureException.class, conflict.getCause());
            assertWinner(initial.id(), 1L);
        }
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void should_not_overwrite_an_existing_id_through_create() {
        AIInitiative initial = createRiskAssessedInitiative();
        transactions.execute(status -> adapter.create(initial));
        initial.approve(NOW);

        assertThrows(DataIntegrityViolationException.class,
                () -> transactions.execute(status -> adapter.create(initial)));

        LoadedAIInitiative stored = load(initial.id());
        assertEquals(InitiativeStatus.RISK_ASSESSED, stored.initiative().status());
        assertEquals(0L, stored.version());
    }

    @Test
    void should_not_reinsert_a_deleted_loaded_initiative() {
        AIInitiative initial = createRiskAssessedInitiative();
        transactions.execute(status -> adapter.create(initial));
        LoadedAIInitiative loaded = load(initial.id());
        jdbc.update("delete from ai_initiatives where id = ?", initial.id().value());
        loaded.initiative().approve(NOW);

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> transactions.execute(status -> adapter.save(loaded)));

        assertTrue(transactions.execute(status -> adapter.findById(initial.id())).isEmpty());
        assertEquals(0, jdbc.queryForObject("select count(*) from ai_initiatives", Integer.class));
    }

    @Test
    void should_migrate_existing_v1_rows_without_changing_their_state() {
        // An independent schema represents a database populated before V2.
        Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas("upgrade_test").defaultSchema("upgrade_test").target("1").load().migrate();
        AIInitiative initial = createRiskAssessedInitiative();
        jdbc.update("""
                insert into upgrade_test.ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, initial.id().value(), initial.organizationId().value(), initial.name(),
                initial.description(), initial.status().name(), initial.preliminaryRisk().name(),
                initial.usesPersonalData(), initial.impactsRights(), java.sql.Timestamp.from(initial.createdAt()));
        Map<String, Object> before = jdbc.queryForMap("select * from upgrade_test.ai_initiatives");

        var migration = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas("upgrade_test").defaultSchema("upgrade_test").load().migrate();

        assertEquals(1, migration.migrationsExecuted);
        Map<String, Object> after = new HashMap<>(jdbc.queryForMap("select * from upgrade_test.ai_initiatives"));
        assertEquals(0L, after.remove("version"));
        assertEquals(before, after);
    }

    private LoadedAIInitiative load(AIInitiativeId id) {
        return transactions.execute(status -> adapter.findById(id).orElseThrow());
    }

    private void assertWinner(AIInitiativeId id, long version) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        LoadedAIInitiative stored = load(id);
        assertEquals(InitiativeStatus.APPROVED, stored.initiative().status());
        assertEquals(RiskLevel.HIGH, stored.initiative().preliminaryRisk());
        assertEquals(version, stored.version());
        assertEquals("APPROVED", jdbc.queryForObject(
                "select status from ai_initiatives where id = ?", String.class, id.value()));
        assertEquals(version, jdbc.queryForObject(
                "select version from ai_initiatives where id = ?", Long.class, id.value()));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from ai_initiatives where id = ?", Integer.class, id.value()));
    }

    private AIInitiative createRiskAssessedInitiative() {
        AIInitiative initiative = createDraftInitiative();
        initiative.submit(NOW);
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.HIGH, NOW);
        return initiative;
    }

    private AIInitiative createDraftInitiative() {
        return AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("AI initiative")
                .description("Optimistic locking verification")
                .usesPersonalData(true)
                .impactsRights(false)
                .createdAt(NOW)
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(AIInitiativeApplicationConfiguration.class)
    static class TestConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        PausingRepository pausingRepository(JpaAIInitiativeRepositoryAdapter adapter,
                                             EntityManager entityManager,
                                             AIInitiativePersistenceMapper mapper) {
            return new PausingRepository(adapter, entityManager, mapper);
        }
    }

    /** Coordinates the race without changing production transactions or Domain. */
    static class PausingRepository implements AIInitiativeRepository {
        private final AIInitiativeRepository delegate;
        private final EntityManager entityManager;
        private final AIInitiativePersistenceMapper mapper;
        private final AtomicBoolean pauseNextLoad = new AtomicBoolean();
        private final AtomicBoolean pauseNextDeferredSave = new AtomicBoolean();
        private CountDownLatch loaded;
        private CountDownLatch resume;
        private CountDownLatch deferredSaveReached;
        private CountDownLatch resumeDeferredSave;

        PausingRepository(AIInitiativeRepository delegate, EntityManager entityManager,
                          AIInitiativePersistenceMapper mapper) {
            this.delegate = delegate;
            this.entityManager = entityManager;
            this.mapper = mapper;
        }

        @Override
        public AIInitiative create(AIInitiative initiative) {
            return delegate.create(initiative);
        }

        @Override
        public SavedAIInitiative save(LoadedAIInitiative loaded) {
            if (pauseNextDeferredSave.compareAndSet(true, false)) {
                // Deliberately leave Hibernate's UPDATE pending until transaction commit.
                entityManager.merge(mapper.toEntity(loaded.initiative(), loaded.version()));
                deferredSaveReached.countDown();
                try {
                    assertTrue(resumeDeferredSave.await(15, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return new SavedAIInitiative(loaded.initiative(), loaded.version());
            }
            return delegate.save(loaded);
        }

        @Override
        public Optional<LoadedAIInitiative> findById(AIInitiativeId id) {
            Optional<LoadedAIInitiative> result = delegate.findById(id);
            if (pauseNextLoad.compareAndSet(true, false)) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals(0L, result.orElseThrow().version());
                loaded.countDown();
                try {
                    assertTrue(resume.await(15, TimeUnit.SECONDS), "A must finish before B saves");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while coordinating concurrent use cases", exception);
                }
            }
            return result;
        }

        void reset() {
            pauseNextLoad.set(false);
            pauseNextDeferredSave.set(false);
            loaded = new CountDownLatch(1);
            resume = new CountDownLatch(1);
            deferredSaveReached = new CountDownLatch(1);
            resumeDeferredSave = new CountDownLatch(1);
        }
    }
}
