package com.leori.enia.risk.infrastructure.configuration;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.risk.application.RecordRiskAssessmentCommand;
import com.leori.enia.risk.application.RecordRiskAssessmentUseCase;
import com.leori.enia.risk.application.RecordRiskFindingCommand;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.event.RiskAssessmentRecorded;
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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(RiskApplicationTransactionIntegrationTest.TestConfiguration.class)
class RiskApplicationTransactionIntegrationTest {

    private static final Instant SYSTEM_CREATED_AT = Instant.parse("2026-09-25T13:00:00.123456Z");
    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00.123456Z");
    private static final UUID ORGANIZATION_UUID = UUID.fromString("30000000-0000-0000-0000-000000000001");

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
    private RecordRiskAssessmentUseCase record;

    @Autowired
    private ObservedAISystemRepository systems;

    @Autowired
    private ObservedRiskAssessmentRepository assessments;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        systems.reset();
        assessments.reset();
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void records_risk_assessment_for_existing_system_in_one_required_transaction() {
        AISystem system = seedSystem();

        RiskAssessment result = record.execute(command(system.id()));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSingleTransaction();
        assertEquals(1, systems.finds);
        assertEquals(1, assessments.creates);
        assertEquals(1, assessmentRowCount());
        assertEquals(3, findingRowCount());
        assertEquals(system.id(), result.systemId());
        assertEquals(ASSESSED_AT, result.assessedAt());
        assertEquals(1, result.domainEvents().size());
        assertInstanceOf(RiskAssessmentRecorded.class, result.domainEvents().getFirst());
        assertRootRow(result);
    }

    @Test
    void rolls_back_root_and_findings_flushed_inside_recording_transaction() {
        AISystem system = seedSystem();
        assessments.failAfterFlush = true;

        assertThrows(FailureAfterFlush.class, () -> record.execute(command(system.id())));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSingleTransaction();
        assertEquals(0, assessmentRowCount());
        assertEquals(0, findingRowCount());
        assertNotNull(assessments.flushedAssessmentId);
        assertEquals(system.id(), assessments.flushedSystemId);
    }

    @Test
    void missing_system_fails_before_risk_assessment_persistence() {
        AISystemId missingId = AISystemId.generate();

        AISystemNotFoundException exception = assertThrows(
                AISystemNotFoundException.class,
                () -> record.execute(command(missingId))
        );

        assertEquals("AI system not found: " + missingId, exception.getMessage());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, systems.finds);
        assertEquals(0, assessments.creates);
        assertEquals(0, assessmentRowCount());
        assertEquals(0, findingRowCount());
    }

    private void assertSingleTransaction() {
        assertNotNull(systems.findTransaction);
        assertNotNull(assessments.createTransaction);
        assertEquals(systems.findTransaction, assessments.createTransaction,
                "System findById and risk assessment create must run in the same PostgreSQL transaction");
    }

    private AISystem seedSystem() {
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'APPROVED', 'HIGH', false, false, ?)
                """, initiativeId, ORGANIZATION_UUID, Timestamp.from(SYSTEM_CREATED_AT));
        AISystemId systemId = AISystemId.generate();
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'AI System', 'System description', 'REGISTERED', ?)
                """, systemId.value(), ORGANIZATION_UUID, initiativeId, Timestamp.from(SYSTEM_CREATED_AT));
        return systems.delegate.findById(systemId)
                .orElseThrow(() -> new AssertionError("Seeded system not found: " + systemId));
    }

    private RecordRiskAssessmentCommand command(AISystemId systemId) {
        return new RecordRiskAssessmentCommand(
                systemId,
                "Governance approval",
                "Public sector deployment",
                List.of(
                        new RecordRiskFindingCommand("Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH),
                        new RecordRiskFindingCommand("Privacy risk", Likelihood.LOW, ImpactMagnitude.MEDIUM),
                        new RecordRiskFindingCommand("Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH)
                )
        );
    }

    private void assertRootRow(RiskAssessment assessment) {
        jdbc.queryForObject("""
                select system_id, purpose, deployment_context, assessed_at
                from risk_assessments where id = ?
                """, (row, rowNumber) -> {
            assertEquals(assessment.systemId().value(), row.getObject("system_id", UUID.class));
            assertEquals(assessment.contextOfUse().purpose(), row.getString("purpose"));
            assertEquals(assessment.contextOfUse().deploymentContext(), row.getString("deployment_context"));
            assertEquals(ASSESSED_AT, row.getTimestamp("assessed_at").toInstant());
            return true;
        }, assessment.id().value());
    }

    private int assessmentRowCount() {
        return jdbc.queryForObject("select count(*) from risk_assessments", Integer.class);
    }

    private int findingRowCount() {
        return jdbc.queryForObject("select count(*) from risk_assessment_findings", Integer.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RiskApplicationConfiguration.class, AIInitiativePersistenceConfiguration.class})
    static class TestConfiguration {

        @Bean
        Clock riskClock() {
            return Clock.fixed(ASSESSED_AT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        ObservedAISystemRepository observedAISystemRepository(
                @Qualifier("aiSystemRepository") AISystemRepository delegate,
                JdbcTemplate jdbc
        ) {
            return new ObservedAISystemRepository(delegate, jdbc);
        }

        @Bean
        @Primary
        ObservedRiskAssessmentRepository observedRiskAssessmentRepository(
                @Qualifier("riskAssessmentRepository") RiskAssessmentRepository delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            return new ObservedRiskAssessmentRepository(delegate, entityManager, jdbc);
        }
    }

    static class ObservedAISystemRepository implements AISystemRepository {
        private final AISystemRepository delegate;
        private final JdbcTemplate jdbc;
        private Long findTransaction;
        private int finds;

        ObservedAISystemRepository(AISystemRepository delegate, JdbcTemplate jdbc) {
            this.delegate = delegate;
            this.jdbc = jdbc;
        }

        @Override
        public AISystem create(AISystem system) {
            return delegate.create(system);
        }

        @Override
        public Optional<AISystem> findById(AISystemId id) {
            findTransaction = currentTransaction();
            finds++;
            return delegate.findById(id);
        }

        private Long currentTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before finding the system");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        void reset() {
            findTransaction = null;
            finds = 0;
        }
    }

    static class ObservedRiskAssessmentRepository implements RiskAssessmentRepository {
        private final RiskAssessmentRepository delegate;
        private final EntityManager entityManager;
        private final JdbcTemplate jdbc;
        private boolean failAfterFlush;
        private Long createTransaction;
        private com.leori.enia.risk.domain.RiskAssessmentId flushedAssessmentId;
        private AISystemId flushedSystemId;
        private int creates;

        ObservedRiskAssessmentRepository(
                RiskAssessmentRepository delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            this.delegate = delegate;
            this.entityManager = entityManager;
            this.jdbc = jdbc;
        }

        @Override
        public RiskAssessment create(RiskAssessment assessment) {
            createTransaction = currentTransaction();
            creates++;
            return observeWrite(assessment, () -> delegate.create(assessment));
        }

        private RiskAssessment observeWrite(RiskAssessment assessment, Supplier<RiskAssessment> write) {
            RiskAssessment result = write.get();
            entityManager.flush();
            flushedAssessmentId = assessment.id();
            flushedSystemId = assessment.systemId();
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from risk_assessments where id = ?", Integer.class, assessment.id().value()));
            assertEquals(3, jdbc.queryForObject(
                    "select count(*) from risk_assessment_findings where risk_assessment_id = ?",
                    Integer.class,
                    assessment.id().value()));
            if (failAfterFlush) {
                throw new FailureAfterFlush();
            }
            return result;
        }

        private Long currentTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before creating the risk assessment");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        void reset() {
            failAfterFlush = false;
            createTransaction = null;
            flushedAssessmentId = null;
            flushedSystemId = null;
            creates = 0;
        }
    }

    static class FailureAfterFlush extends RuntimeException {
    }
}
