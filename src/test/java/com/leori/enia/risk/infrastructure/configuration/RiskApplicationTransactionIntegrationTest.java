package com.leori.enia.risk.infrastructure.configuration;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.risk.application.DefineControlCommand;
import com.leori.enia.risk.application.DefineControlUseCase;
import com.leori.enia.risk.application.GetControlImplementationUseCase;
import com.leori.enia.risk.application.GetControlUseCase;
import com.leori.enia.risk.application.GetRiskAssessmentUseCase;
import com.leori.enia.risk.application.RecordControlImplementationCommand;
import com.leori.enia.risk.application.RecordControlImplementationUseCase;
import com.leori.enia.risk.application.RecordRiskAssessmentCommand;
import com.leori.enia.risk.application.RecordRiskAssessmentUseCase;
import com.leori.enia.risk.application.RecordRiskFindingCommand;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import com.leori.enia.risk.domain.ControlRepository;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.ContextOfUse;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFinding;
import com.leori.enia.risk.domain.RiskFindingId;
import com.leori.enia.risk.domain.event.ControlDefined;
import com.leori.enia.risk.domain.event.ControlImplementationRecorded;
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
    private GetRiskAssessmentUseCase get;

    @Autowired
    private DefineControlUseCase define;

    @Autowired
    private GetControlUseCase getControl;

    @Autowired
    private RecordControlImplementationUseCase recordImplementation;

    @Autowired
    private GetControlImplementationUseCase getImplementation;

    @Autowired
    private ObservedAISystemRepository systems;

    @Autowired
    private ObservedRiskAssessmentRepository assessments;

    @Autowired
    private ObservedControlRepository controls;

    @Autowired
    private ObservedControlImplementationRepository implementations;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        systems.reset();
        assessments.reset();
        controls.reset();
        implementations.reset();
        jdbc.update("delete from control_implementations");
        jdbc.update("delete from controls");
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
    void gets_risk_assessment_in_one_read_only_transaction_without_writing() {
        RiskAssessment assessment = assessments.delegate.create(assessment(seedSystem().id()));
        assessments.reset();

        RiskAssessment result = get.execute(assessment.id());

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(assessment.id(), result.id());
        assertEquals(assessment.systemId(), result.systemId());
        assertEquals(assessment.findings(), result.findings());
        assertEquals(0, assessments.creates);
        assertEquals(1, assessments.finds);
        assertTrue(assessments.findReadOnly);
        assertEquals(1, assessmentRowCount());
        assertEquals(3, findingRowCount());
    }

    @Test
    void defines_control_for_existing_finding_in_one_required_write_transaction() {
        RiskAssessment assessment = assessments.delegate.create(assessment(seedSystem().id()));
        assessments.reset();
        controls.reset();
        RiskFindingId findingId = assessment.findings().getFirst().id();

        Control result = define.execute(defineCommand(assessment.id(), findingId));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertDefineSingleTransaction();
        assertEquals(1, assessments.finds);
        assertFalse(assessments.findReadOnly);
        assertEquals(1, controls.creates);
        assertEquals(1, controlRowCount());
        assertEquals(assessment.id(), result.riskAssessmentId());
        assertEquals(findingId, result.riskFindingId());
        assertEquals(ASSESSED_AT, result.createdAt());
        assertEquals(1, result.domainEvents().size());
        assertInstanceOf(ControlDefined.class, result.domainEvents().getFirst());
    }

    @Test
    void gets_control_in_one_read_only_transaction_without_writing() {
        RiskAssessment assessment = assessments.delegate.create(assessment(seedSystem().id()));
        Control control = controls.delegate.create(control(assessment.id(), assessment.findings().getFirst().id()));
        controls.reset();

        Control result = getControl.execute(control.id());

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(control.id(), result.id());
        assertEquals(control.riskAssessmentId(), result.riskAssessmentId());
        assertEquals(control.riskFindingId(), result.riskFindingId());
        assertEquals(control.name(), result.name());
        assertEquals(control.description(), result.description());
        assertEquals(control.createdAt(), result.createdAt());
        assertEquals(0, controls.creates);
        assertEquals(1, controls.finds);
        assertTrue(controls.findReadOnly);
        assertEquals(1, controlRowCount());
    }

    @Test
    void rolls_back_control_flushed_inside_define_transaction() {
        RiskAssessment assessment = assessments.delegate.create(assessment(seedSystem().id()));
        assessments.reset();
        controls.reset();
        controls.failAfterFlush = true;
        RiskFindingId findingId = assessment.findings().getFirst().id();

        assertThrows(FailureAfterFlush.class, () -> define.execute(defineCommand(assessment.id(), findingId)));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertDefineSingleTransaction();
        assertEquals(0, controlRowCount());
        assertNotNull(controls.flushedControlId);
        assertEquals(assessment.id(), controls.flushedAssessmentId);
        assertEquals(findingId, controls.flushedFindingId);
    }

    @Test
    void records_control_implementation_for_existing_control_in_one_required_write_transaction() {
        RiskAssessment assessment = assessments.delegate.create(assessment(seedSystem().id()));
        Control control = controls.delegate.create(control(assessment.id(), assessment.findings().getFirst().id()));
        controls.reset();
        implementations.reset();

        ControlImplementation result = recordImplementation.execute(recordImplementationCommand(control.id()));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertRecordImplementationSingleTransaction();
        assertEquals(1, controls.finds);
        assertFalse(controls.findReadOnly);
        assertEquals(1, implementations.creates);
        assertEquals(1, controlImplementationRowCount());
        assertEquals(control.id(), result.controlId());
        assertEquals("Evidence package attached.", result.description());
        assertEquals(ASSESSED_AT, result.implementedAt());
        assertEquals(1, result.domainEvents().size());
        assertInstanceOf(ControlImplementationRecorded.class, result.domainEvents().getFirst());
    }

    @Test
    void gets_control_implementation_in_one_read_only_transaction_without_writing() {
        RiskAssessment assessment = assessments.delegate.create(assessment(seedSystem().id()));
        Control control = controls.delegate.create(control(assessment.id(), assessment.findings().getFirst().id()));
        ControlImplementation implementation = implementations.delegate.create(implementation(control.id()));
        implementations.reset();

        ControlImplementation result = getImplementation.execute(implementation.id());

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(implementation.id(), result.id());
        assertEquals(implementation.controlId(), result.controlId());
        assertEquals(implementation.description(), result.description());
        assertEquals(implementation.implementedAt(), result.implementedAt());
        assertEquals(0, result.domainEvents().size());
        assertEquals(0, implementations.creates);
        assertEquals(1, implementations.finds);
        assertTrue(implementations.findReadOnly);
        assertEquals(1, controlImplementationRowCount());
    }

    @Test
    void rolls_back_control_implementation_flushed_inside_recording_transaction() {
        RiskAssessment assessment = assessments.delegate.create(assessment(seedSystem().id()));
        Control control = controls.delegate.create(control(assessment.id(), assessment.findings().getFirst().id()));
        controls.reset();
        implementations.reset();
        implementations.failAfterFlush = true;

        assertThrows(FailureAfterFlush.class,
                () -> recordImplementation.execute(recordImplementationCommand(control.id())));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertRecordImplementationSingleTransaction();
        assertEquals(0, controlImplementationRowCount());
        assertNotNull(implementations.flushedImplementationId);
        assertEquals(control.id(), implementations.flushedControlId);
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

    private void assertDefineSingleTransaction() {
        assertNotNull(assessments.findTransaction);
        assertNotNull(controls.createTransaction);
        assertEquals(assessments.findTransaction, controls.createTransaction,
                "Risk assessment findById and control create must run in the same PostgreSQL transaction");
    }

    private void assertRecordImplementationSingleTransaction() {
        assertNotNull(controls.findTransaction);
        assertNotNull(implementations.createTransaction);
        assertEquals(controls.findTransaction, implementations.createTransaction,
                "Control findById and control implementation create must run in the same PostgreSQL transaction");
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

    private RiskAssessment assessment(AISystemId systemId) {
        return RiskAssessment.builder()
                .id(RiskAssessmentId.generate())
                .systemId(systemId)
                .contextOfUse(new ContextOfUse("Governance approval", "Public sector deployment"))
                .findings(List.of(
                        new RiskFinding(RiskFindingId.generate(), "Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH),
                        new RiskFinding(RiskFindingId.generate(), "Privacy risk", Likelihood.LOW, ImpactMagnitude.MEDIUM),
                        new RiskFinding(RiskFindingId.generate(), "Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH)
                ))
                .assessedAt(ASSESSED_AT)
                .build();
    }

    private DefineControlCommand defineCommand(RiskAssessmentId assessmentId, RiskFindingId findingId) {
        return new DefineControlCommand(
                assessmentId,
                findingId,
                "Human review gate",
                "Require documented human approval before deployment."
        );
    }

    private RecordControlImplementationCommand recordImplementationCommand(com.leori.enia.risk.domain.ControlId controlId) {
        return new RecordControlImplementationCommand(controlId, "Evidence package attached.");
    }

    private ControlImplementation implementation(com.leori.enia.risk.domain.ControlId controlId) {
        return ControlImplementation.builder()
                .id(com.leori.enia.risk.domain.ControlImplementationId.generate())
                .controlId(controlId)
                .description("Evidence package attached.")
                .implementedAt(ASSESSED_AT)
                .build();
    }

    private Control control(RiskAssessmentId assessmentId, RiskFindingId findingId) {
        return Control.builder()
                .id(com.leori.enia.risk.domain.ControlId.generate())
                .riskAssessmentId(assessmentId)
                .riskFindingId(findingId)
                .name("Human review gate")
                .description("Require documented human approval before deployment.")
                .createdAt(ASSESSED_AT)
                .build();
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

    private int controlRowCount() {
        return jdbc.queryForObject("select count(*) from controls", Integer.class);
    }

    private int controlImplementationRowCount() {
        return jdbc.queryForObject("select count(*) from control_implementations", Integer.class);
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

        @Bean
        @Primary
        ObservedControlRepository observedControlRepository(
                @Qualifier("controlRepository") ControlRepository delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            return new ObservedControlRepository(delegate, entityManager, jdbc);
        }

        @Bean
        @Primary
        ObservedControlImplementationRepository observedControlImplementationRepository(
                @Qualifier("controlImplementationRepository") ControlImplementationRepository delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            return new ObservedControlImplementationRepository(delegate, entityManager, jdbc);
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
        private Long findTransaction;
        private boolean findReadOnly;
        private com.leori.enia.risk.domain.RiskAssessmentId flushedAssessmentId;
        private AISystemId flushedSystemId;
        private int creates;
        private int finds;

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
            createTransaction = currentWriteTransaction();
            creates++;
            return observeWrite(assessment, () -> delegate.create(assessment));
        }

        @Override
        public Optional<RiskAssessment> findById(RiskAssessmentId id) {
            findTransaction = currentTransaction("Transaction must start before finding the risk assessment");
            findReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            finds++;
            return delegate.findById(id);
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

        private Long currentWriteTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before creating the risk assessment");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        private Long currentTransaction(String message) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), message);
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        void reset() {
            failAfterFlush = false;
            createTransaction = null;
            findTransaction = null;
            findReadOnly = false;
            flushedAssessmentId = null;
            flushedSystemId = null;
            creates = 0;
            finds = 0;
        }
    }

    static class ObservedControlRepository implements ControlRepository {
        private final ControlRepository delegate;
        private final EntityManager entityManager;
        private final JdbcTemplate jdbc;
        private boolean failAfterFlush;
        private Long createTransaction;
        private Long findTransaction;
        private boolean findReadOnly;
        private com.leori.enia.risk.domain.ControlId flushedControlId;
        private RiskAssessmentId flushedAssessmentId;
        private RiskFindingId flushedFindingId;
        private int creates;
        private int finds;

        ObservedControlRepository(
                ControlRepository delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            this.delegate = delegate;
            this.entityManager = entityManager;
            this.jdbc = jdbc;
        }

        @Override
        public Control create(Control control) {
            createTransaction = currentWriteTransaction();
            creates++;
            Control result = delegate.create(control);
            entityManager.flush();
            flushedControlId = control.id();
            flushedAssessmentId = control.riskAssessmentId();
            flushedFindingId = control.riskFindingId();
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from controls where id = ?", Integer.class, control.id().value()));
            if (failAfterFlush) {
                throw new FailureAfterFlush();
            }
            return result;
        }

        @Override
        public Optional<Control> findById(com.leori.enia.risk.domain.ControlId id) {
            findTransaction = currentTransaction("Transaction must start before finding the control");
            findReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            finds++;
            return delegate.findById(id);
        }

        private Long currentWriteTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before creating the control");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        private Long currentTransaction(String message) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), message);
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        void reset() {
            failAfterFlush = false;
            createTransaction = null;
            findTransaction = null;
            findReadOnly = false;
            flushedControlId = null;
            flushedAssessmentId = null;
            flushedFindingId = null;
            creates = 0;
            finds = 0;
        }
    }

    static class ObservedControlImplementationRepository implements ControlImplementationRepository {
        private final ControlImplementationRepository delegate;
        private final EntityManager entityManager;
        private final JdbcTemplate jdbc;
        private boolean failAfterFlush;
        private Long createTransaction;
        private Long findTransaction;
        private boolean findReadOnly;
        private com.leori.enia.risk.domain.ControlImplementationId flushedImplementationId;
        private com.leori.enia.risk.domain.ControlId flushedControlId;
        private int creates;
        private int finds;

        ObservedControlImplementationRepository(
                ControlImplementationRepository delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            this.delegate = delegate;
            this.entityManager = entityManager;
            this.jdbc = jdbc;
        }

        @Override
        public ControlImplementation create(ControlImplementation implementation) {
            createTransaction = currentWriteTransaction();
            creates++;
            ControlImplementation result = delegate.create(implementation);
            entityManager.flush();
            flushedImplementationId = implementation.id();
            flushedControlId = implementation.controlId();
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from control_implementations where id = ?",
                    Integer.class,
                    implementation.id().value()));
            if (failAfterFlush) {
                throw new FailureAfterFlush();
            }
            return result;
        }

        @Override
        public Optional<ControlImplementation> findById(com.leori.enia.risk.domain.ControlImplementationId id) {
            findTransaction = currentTransaction("Transaction must start before finding the control implementation");
            findReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            finds++;
            return delegate.findById(id);
        }

        private Long currentWriteTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before creating the control implementation");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        private Long currentTransaction(String message) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), message);
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        void reset() {
            failAfterFlush = false;
            createTransaction = null;
            findTransaction = null;
            findReadOnly = false;
            flushedImplementationId = null;
            flushedControlId = null;
            creates = 0;
            finds = 0;
        }
    }

    static class FailureAfterFlush extends RuntimeException {
    }
}
