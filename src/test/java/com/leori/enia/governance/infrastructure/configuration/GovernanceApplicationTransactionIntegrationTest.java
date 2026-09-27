package com.leori.enia.governance.infrastructure.configuration;

import com.leori.enia.governance.application.RegisterAISystemCommand;
import com.leori.enia.governance.application.RegisterAISystemUseCase;
import com.leori.enia.governance.application.exception.AISystemAlreadyRegisteredException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.event.AISystemRegistered;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.application.port.SavedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.initiative.domain.exception.AIInitiativeNotApprovedForSystemRegistrationException;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.initiative.infrastructure.persistence.JpaAIInitiativeRepositoryAdapter;
import com.leori.enia.organization.domain.OrganizationId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(GovernanceApplicationTransactionIntegrationTest.TestConfiguration.class)
class GovernanceApplicationTransactionIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-25T13:00:00.123456Z");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-25T14:00:00.123456Z");

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
    private RegisterAISystemUseCase register;

    @Autowired
    private ObservedAIInitiativeRepository initiatives;

    @Autowired
    private ObservedAISystemRepository systems;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        systems.reset();
        initiatives.reset();
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void registers_system_from_approved_initiative_in_one_required_transaction() {
        AIInitiative source = seedSource(InitiativeStatus.APPROVED, RiskLevel.HIGH);
        long versionBefore = storedVersion(source.id());

        AISystem result = register.execute(new RegisterAISystemCommand(source.id(), "System", "Description"));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSingleTransaction();
        assertEquals(1, systems.creates);
        assertEquals(1, initiatives.loads);
        assertEquals(0, initiatives.saves);
        assertEquals(1, rowCount());
        assertEquals(source.id(), result.sourceInitiativeId());
        assertEquals(source.organizationId(), result.organizationId());
        assertEquals(REGISTERED_AT, result.createdAt());
        assertEquals(1, result.domainEvents().size());
        assertInstanceOf(AISystemRegistered.class, result.domainEvents().getFirst());
        assertSystemRow(result);
        assertSourceUnchanged(source, versionBefore);
    }

    @Test
    void rolls_back_insert_flushed_inside_registration_transaction() {
        AIInitiative source = seedSource(InitiativeStatus.APPROVED, RiskLevel.HIGH);
        long versionBefore = storedVersion(source.id());
        systems.failAfterFlush = true;

        assertThrows(FailureAfterFlush.class,
                () -> register.execute(new RegisterAISystemCommand(source.id(), "System", "Description")));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSingleTransaction();
        assertEquals(0, rowCount());
        assertEquals(source.id(), systems.flushedSourceId);
        assertSourceUnchanged(source, versionBefore);
    }

    @Test
    void unapproved_source_fails_before_system_persistence() {
        AIInitiative source = seedSource(InitiativeStatus.DRAFT, RiskLevel.NOT_ASSESSED);
        long versionBefore = storedVersion(source.id());

        AIInitiativeNotApprovedForSystemRegistrationException exception = assertThrows(
                AIInitiativeNotApprovedForSystemRegistrationException.class,
                () -> register.execute(new RegisterAISystemCommand(source.id(), "System", "Description"))
        );

        assertEquals(source.id(), exception.initiativeId());
        assertEquals(InitiativeStatus.DRAFT, exception.actualStatus());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, initiatives.loads);
        assertEquals(0, initiatives.saves);
        assertEquals(0, systems.creates);
        assertEquals(0, rowCount());
        assertSourceUnchanged(source, versionBefore);
    }

    @Test
    void duplicate_registration_propagates_and_preserves_original_row_and_source() {
        AIInitiative source = seedSource(InitiativeStatus.APPROVED, RiskLevel.HIGH);
        long versionBefore = storedVersion(source.id());
        AISystem original = register.execute(new RegisterAISystemCommand(source.id(), "Original", "Description"));
        systems.reset();
        initiatives.reset();

        AISystemAlreadyRegisteredException duplicate = assertThrows(
                AISystemAlreadyRegisteredException.class,
                () -> register.execute(new RegisterAISystemCommand(source.id(), "Duplicate", "Description"))
        );

        assertEquals(source.id(), duplicate.sourceInitiativeId());
        assertNotNull(duplicate.getCause());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSingleTransaction();
        assertEquals(1, rowCount());
        assertSystemRow(original);
        assertSourceUnchanged(source, versionBefore);
    }

    private void assertSingleTransaction() {
        assertNotNull(initiatives.loadTransaction);
        assertNotNull(systems.createTransaction);
        assertEquals(initiatives.loadTransaction, systems.createTransaction,
                "Initiative load and AI system create must run in the same PostgreSQL transaction");
    }

    private AIInitiative seedSource(InitiativeStatus status, RiskLevel riskLevel) {
        AIInitiative initiative = AIInitiative.rehydrate(
                AIInitiativeId.generate(),
                OrganizationId.generate(),
                "Source initiative",
                "Description",
                status,
                riskLevel,
                true,
                false,
                CREATED_AT,
                null
        );
        return initiatives.delegate.create(initiative);
    }

    private void assertSourceUnchanged(AIInitiative source, long versionBefore) {
        assertEquals(source.status().name(), jdbc.queryForObject(
                "select status from ai_initiatives where id = ?", String.class, source.id().value()));
        assertEquals(source.preliminaryRisk().name(), jdbc.queryForObject(
                "select preliminary_risk from ai_initiatives where id = ?", String.class, source.id().value()));
        assertEquals(versionBefore, storedVersion(source.id()));
    }

    private void assertSystemRow(AISystem system) {
        assertEquals(system.organizationId().value(), jdbc.queryForObject(
                "select organization_id from ai_systems where id = ?", java.util.UUID.class, system.id().value()));
        assertEquals(system.sourceInitiativeId().value(), jdbc.queryForObject(
                "select source_initiative_id from ai_systems where id = ?", java.util.UUID.class, system.id().value()));
        assertEquals(system.name(), jdbc.queryForObject(
                "select name from ai_systems where id = ?", String.class, system.id().value()));
        assertEquals(system.description(), jdbc.queryForObject(
                "select description from ai_systems where id = ?", String.class, system.id().value()));
    }

    private long storedVersion(AIInitiativeId id) {
        return jdbc.queryForObject("select version from ai_initiatives where id = ?", Long.class, id.value());
    }

    private int rowCount() {
        return jdbc.queryForObject("select count(*) from ai_systems", Integer.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({GovernanceApplicationConfiguration.class, AIInitiativePersistenceConfiguration.class})
    static class TestConfiguration {
        @Bean
        Clock governanceClock() {
            return Clock.fixed(REGISTERED_AT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        ObservedAIInitiativeRepository observedAIInitiativeRepository(
                JpaAIInitiativeRepositoryAdapter delegate,
                JdbcTemplate jdbc
        ) {
            return new ObservedAIInitiativeRepository(delegate, jdbc);
        }

        @Bean
        @Primary
        ObservedAISystemRepository observedAISystemRepository(
                @Qualifier("aiSystemRepository") AISystemRepository delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            return new ObservedAISystemRepository(delegate, entityManager, jdbc);
        }
    }

    static class ObservedAIInitiativeRepository implements AIInitiativeRepository {
        private final AIInitiativeRepository delegate;
        private final JdbcTemplate jdbc;
        private Long loadTransaction;
        private int loads;
        private int saves;

        ObservedAIInitiativeRepository(AIInitiativeRepository delegate, JdbcTemplate jdbc) {
            this.delegate = delegate;
            this.jdbc = jdbc;
        }

        @Override
        public AIInitiative create(AIInitiative initiative) {
            return delegate.create(initiative);
        }

        @Override
        public SavedAIInitiative save(LoadedAIInitiative loaded) {
            saves++;
            return delegate.save(loaded);
        }

        @Override
        public Optional<LoadedAIInitiative> findById(AIInitiativeId id) {
            loadTransaction = currentTransaction();
            loads++;
            return delegate.findById(id);
        }

        private Long currentTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before loading the initiative");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        void reset() {
            loadTransaction = null;
            loads = 0;
            saves = 0;
        }
    }

    static class ObservedAISystemRepository implements AISystemRepository {
        private final AISystemRepository delegate;
        private final EntityManager entityManager;
        private final JdbcTemplate jdbc;
        private boolean failAfterFlush;
        private Long createTransaction;
        private AIInitiativeId flushedSourceId;
        private int creates;

        ObservedAISystemRepository(AISystemRepository delegate, EntityManager entityManager, JdbcTemplate jdbc) {
            this.delegate = delegate;
            this.entityManager = entityManager;
            this.jdbc = jdbc;
        }

        @Override
        public AISystem create(AISystem system) {
            createTransaction = currentTransaction();
            creates++;
            return observeWrite(system, () -> delegate.create(system));
        }

        private AISystem observeWrite(AISystem system, Supplier<AISystem> write) {
            AISystem result = write.get();
            entityManager.flush();
            flushedSourceId = system.sourceInitiativeId();
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from ai_systems where id = ?", Integer.class, system.id().value()));
            if (failAfterFlush) {
                throw new FailureAfterFlush();
            }
            return result;
        }

        private Long currentTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before creating the system");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        void reset() {
            failAfterFlush = false;
            createTransaction = null;
            flushedSourceId = null;
            creates = 0;
        }
    }

    static class FailureAfterFlush extends RuntimeException {
    }
}
