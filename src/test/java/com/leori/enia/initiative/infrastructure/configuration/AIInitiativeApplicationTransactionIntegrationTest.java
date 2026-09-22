package com.leori.enia.initiative.infrastructure.configuration;

import com.leori.enia.initiative.application.ApproveAIInitiativeCommand;
import com.leori.enia.initiative.application.ApproveAIInitiativeUseCase;
import com.leori.enia.initiative.application.AssessRiskAIInitiativeCommand;
import com.leori.enia.initiative.application.AssessRiskAIInitiativeUseCase;
import com.leori.enia.initiative.application.CreateAIInitiativeCommand;
import com.leori.enia.initiative.application.CreateAIInitiativeUseCase;
import com.leori.enia.initiative.application.RejectAIInitiativeCommand;
import com.leori.enia.initiative.application.RejectAIInitiativeUseCase;
import com.leori.enia.initiative.application.StartAssessmentAIInitiativeCommand;
import com.leori.enia.initiative.application.StartAssessmentAIInitiativeUseCase;
import com.leori.enia.initiative.application.SubmitAIInitiativeCommand;
import com.leori.enia.initiative.application.ExpectedRevision;
import com.leori.enia.initiative.application.SubmitAIInitiativeUseCase;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.application.port.SavedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.initiative.infrastructure.persistence.JpaAIInitiativeRepositoryAdapter;
import com.leori.enia.organization.domain.OrganizationId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(AIInitiativeApplicationTransactionIntegrationTest.TestConfiguration.class)
class AIInitiativeApplicationTransactionIntegrationTest {

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
    private CreateAIInitiativeUseCase create;

    @Autowired
    private SubmitAIInitiativeUseCase submit;

    @Autowired
    private StartAssessmentAIInitiativeUseCase startAssessment;

    @Autowired
    private AssessRiskAIInitiativeUseCase assessRisk;

    @Autowired
    private ApproveAIInitiativeUseCase approve;

    @Autowired
    private RejectAIInitiativeUseCase reject;

    @Autowired
    private ObservedRepository repository;

    @Autowired
    private JdbcTemplate jdbc;


    @BeforeEach
    void reset() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        repository.reset();
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void should_translate_database_failure_without_adapter_exception_translation() {
        AIInitiative initiative = AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("x".repeat(256))
                .description("Exceeds the database name column length")
                .createdAt(NOW)
                .build();

        // Deliberately bypass the use-case transaction: Spring Data owns this
        // transaction and translates the PostgreSQL failure through the adapter.
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.delegate.create(initiative));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from ai_initiatives where id = ?",
                Integer.class, initiative.id().value()));
    }

    @Test
    void should_preserve_domain_validation_failure_during_rehydration() {
        AIInitiative draft = prepare(Operation.SUBMIT);
        jdbc.update("update ai_initiatives set preliminary_risk = 'HIGH' where id = ?",
                draft.id().value());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> repository.delegate.findById(draft.id()));

        assertEquals("Preliminary risk must be NOT_ASSESSED for status DRAFT", failure.getMessage());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void should_commit_each_complete_use_case(Operation operation) {
        AIInitiative before = prepare(operation);

        AIInitiative result = execute(operation, before);

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(operation.resultStatus, result.status());
        assertEquals(operation.resultStatus.name(), storedStatus(result.id()));
        assertEquals(NOW, result.createdAt());
        RiskLevel expectedRisk = switch (operation) {
            case CREATE, SUBMIT, START_ASSESSMENT -> RiskLevel.NOT_ASSESSED;
            case ASSESS_RISK, APPROVE, REJECT -> RiskLevel.HIGH;
        };
        assertEquals(expectedRisk, result.preliminaryRisk());
        assertSingleTransaction(operation);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void should_rollback_when_save_flushes_then_throws(Operation operation) {
        AIInitiative before = prepare(operation);
        repository.failAfterFlush = true;

        assertThrows(FailureAfterFlush.class, () -> execute(operation, before));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSingleTransaction(operation);
        assertEquals(operation.resultStatus.name(), repository.flushedStatus);
        if (before == null) {
            assertEquals(0, jdbc.queryForObject(
                    "select count(*) from ai_initiatives where id = ?",
                    Integer.class, repository.savedId.value()));
        } else {
            assertEquals(before.status().name(), storedStatus(before.id()));
            assertEquals(before.preliminaryRisk().name(), jdbc.queryForObject(
                    "select preliminary_risk from ai_initiatives where id = ?",
                    String.class, before.id().value()));
        }
    }

    private void assertSingleTransaction(Operation operation) {
        assertNotNull(repository.saveTransaction);
        assertEquals(1, repository.saves);
        assertEquals(operation == Operation.CREATE ? 0 : 1, repository.loads);
        if (operation != Operation.CREATE) {
            assertEquals(repository.loadTransaction, repository.saveTransaction,
                    "Load and save must run in the same PostgreSQL transaction");
        }
    }

    private AIInitiative prepare(Operation operation) {
        if (operation == Operation.CREATE) {
            return null;
        }
        AIInitiative initiative = AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("AI initiative")
                .description("Transaction boundary verification")
                .usesPersonalData(true)
                .impactsRights(false)
                .createdAt(NOW)
                .build();
        if (operation != Operation.SUBMIT) {
            initiative.submit(NOW);
        }
        if (operation == Operation.ASSESS_RISK || operation == Operation.APPROVE
                || operation == Operation.REJECT) {
            initiative.startAssessment();
        }
        if (operation == Operation.APPROVE || operation == Operation.REJECT) {
            initiative.assessRisk(RiskLevel.HIGH, NOW);
        }
        // Seed in an independent, already committed repository transaction.
        return repository.delegate.create(initiative);
    }

    private AIInitiative execute(Operation operation, AIInitiative before) {
        return switch (operation) {
            case CREATE -> create.execute(new CreateAIInitiativeCommand(
                    OrganizationId.generate(), "AI initiative", "Transaction verification", true, false));
            case SUBMIT -> {
                submit.execute(new SubmitAIInitiativeCommand(before.id(), new ExpectedRevision(before.id(), 0)));
                yield repository.delegate.findById(before.id()).orElseThrow().initiative();
            }
            case START_ASSESSMENT -> {
                startAssessment.execute(new StartAssessmentAIInitiativeCommand(
                        before.id(), new ExpectedRevision(before.id(), 0)));
                yield repository.delegate.findById(before.id()).orElseThrow().initiative();
            }
            case ASSESS_RISK -> assessRisk.execute(new AssessRiskAIInitiativeCommand(before.id(), RiskLevel.HIGH));
            case APPROVE -> approve.execute(new ApproveAIInitiativeCommand(before.id()));
            case REJECT -> reject.execute(new RejectAIInitiativeCommand(before.id(), "Residual risk unacceptable"));
        };
    }

    private String storedStatus(AIInitiativeId id) {
        return jdbc.queryForObject("select status from ai_initiatives where id = ?", String.class, id.value());
    }

    enum Operation {
        CREATE(InitiativeStatus.DRAFT),
        SUBMIT(InitiativeStatus.SUBMITTED),
        START_ASSESSMENT(InitiativeStatus.UNDER_ASSESSMENT),
        ASSESS_RISK(InitiativeStatus.RISK_ASSESSED),
        APPROVE(InitiativeStatus.APPROVED),
        REJECT(InitiativeStatus.REJECTED);

        private final InitiativeStatus resultStatus;

        Operation(InitiativeStatus resultStatus) {
            this.resultStatus = resultStatus;
        }
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
        ObservedRepository observedRepository(
                JpaAIInitiativeRepositoryAdapter delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            return new ObservedRepository(delegate, entityManager, jdbc);
        }
    }

    /** Observes the production adapter and fails only after SQL reached PostgreSQL. */
    static class ObservedRepository implements AIInitiativeRepository {
        private final AIInitiativeRepository delegate;
        private final EntityManager entityManager;
        private final JdbcTemplate jdbc;
        private boolean failAfterFlush;
        private Long loadTransaction;
        private Long saveTransaction;
        private AIInitiativeId savedId;
        private String flushedStatus;
        private int loads;
        private int saves;

        ObservedRepository(
                AIInitiativeRepository delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            this.delegate = delegate;
            this.entityManager = entityManager;
            this.jdbc = jdbc;
        }

        @Override
        public Optional<LoadedAIInitiative> findById(AIInitiativeId id) {
            loadTransaction = currentTransaction();
            loads++;
            return delegate.findById(id);
        }

        @Override
        public AIInitiative create(AIInitiative initiative) {
            return observeWrite(initiative, () -> delegate.create(initiative));
        }

        @Override
        public SavedAIInitiative save(LoadedAIInitiative loaded) {
            saveTransaction = currentTransaction();
            saves++;
            SavedAIInitiative result = delegate.save(loaded);
            entityManager.flush();
            savedId = result.initiative().id();
            flushedStatus = jdbc.queryForObject(
                    "select status from ai_initiatives where id = ?", String.class, savedId.value());
            assertEquals(loaded.initiative().status().name(), flushedStatus);
            if (failAfterFlush) {
                throw new FailureAfterFlush();
            }
            return result;
        }

        private AIInitiative observeWrite(AIInitiative initiative, Supplier<AIInitiative> write) {
            saveTransaction = currentTransaction();
            saves++;
            AIInitiative result = write.get();
            entityManager.flush();
            savedId = result.id();
            flushedStatus = jdbc.queryForObject(
                    "select status from ai_initiatives where id = ?", String.class, savedId.value());
            assertEquals(initiative.status().name(), flushedStatus);
            if (failAfterFlush) {
                throw new FailureAfterFlush();
            }
            return result;
        }

        private Long currentTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before entering the repository adapter");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        void reset() {
            failAfterFlush = false;
            loadTransaction = null;
            saveTransaction = null;
            savedId = null;
            flushedStatus = null;
            loads = 0;
            saves = 0;
        }
    }

    static class FailureAfterFlush extends RuntimeException {
    }
}
