package com.leori.enia.evidence.infrastructure.configuration;

import com.leori.enia.evidence.application.GetEvidenceUseCase;
import com.leori.enia.evidence.application.RecordEvidenceCommand;
import com.leori.enia.evidence.application.RecordEvidenceUseCase;
import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.evidence.domain.EvidenceRepository;
import com.leori.enia.evidence.domain.event.EvidenceRecorded;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(EvidenceApplicationTransactionIntegrationTest.TestConfiguration.class)
class EvidenceApplicationTransactionIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T14:00:00.123456Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T12:45:00.123456Z");
    private static final UUID ORGANIZATION_UUID = UUID.fromString("40000000-0000-0000-0000-000000000001");

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
    private RecordEvidenceUseCase record;

    @Autowired
    private GetEvidenceUseCase get;

    @Autowired
    private ObservedControlImplementationRepository implementations;

    @Autowired
    private ObservedEvidenceRepository evidence;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        implementations.reset();
        evidence.reset();
        jdbc.update("delete from evidence");
        jdbc.update("delete from control_implementations");
        jdbc.update("delete from controls");
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void record_evidence_use_case_is_transactionally_proxied() {
        assertTrue(AopUtils.isAopProxy(record));
    }

    @Test
    void get_evidence_use_case_is_transactionally_proxied() {
        assertTrue(AopUtils.isAopProxy(get));
    }

    @Test
    void records_evidence_for_existing_control_implementation_in_one_required_read_write_transaction() {
        ControlImplementation implementation = seedControlImplementation();

        Evidence result = record.execute(new RecordEvidenceCommand(
                implementation.id(), "  Signed approval memo  ", "  archive://approval.pdf  "));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSingleTransaction();
        assertEquals(1, implementations.finds);
        assertFalse(implementations.findReadOnly);
        assertEquals(1, evidence.creates);
        assertFalse(evidence.createReadOnly);
        assertEquals(1, evidenceRowCount());
        assertEquals(implementation.id(), result.controlImplementationId());
        assertEquals("Signed approval memo", result.description());
        assertEquals("archive://approval.pdf", result.reference());
        assertEquals(RECORDED_AT, result.recordedAt());
        assertEquals(1, result.domainEvents().size());
        assertInstanceOf(EvidenceRecorded.class, result.domainEvents().getFirst());
        assertEvidenceRow(result);
    }

    @Test
    void gets_evidence_in_one_required_read_only_transaction_without_writing() {
        ControlImplementation implementation = seedControlImplementation();
        Evidence recorded = evidence.delegate.create(evidence(implementation.id()));
        evidence.reset();

        Evidence result = get.execute(recorded.id());

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(recorded.id(), result.id());
        assertEquals(recorded.controlImplementationId(), result.controlImplementationId());
        assertEquals(recorded.description(), result.description());
        assertEquals(recorded.reference(), result.reference());
        assertEquals(recorded.recordedAt(), result.recordedAt());
        assertEquals(0, result.domainEvents().size());
        assertEquals(0, evidence.creates);
        assertEquals(1, evidence.finds);
        assertTrue(evidence.findReadOnly);
        assertEquals(1, evidenceRowCount());
    }

    @Test
    void rolls_back_evidence_flushed_inside_recording_transaction() {
        ControlImplementation implementation = seedControlImplementation();
        evidence.failAfterFlush = true;

        assertThrows(FailureAfterFlush.class, () -> record.execute(new RecordEvidenceCommand(
                implementation.id(), "Signed approval memo", "archive://approval.pdf")));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSingleTransaction();
        assertEquals(0, evidenceRowCount());
        assertNotNull(evidence.flushedEvidenceId);
        assertEquals(implementation.id(), evidence.flushedControlImplementationId);
    }

    private void assertSingleTransaction() {
        assertNotNull(implementations.findTransaction);
        assertNotNull(evidence.createTransaction);
        assertEquals(implementations.findTransaction, evidence.createTransaction,
                "Control implementation findById and evidence create must run in the same PostgreSQL transaction");
    }

    private ControlImplementation seedControlImplementation() {
        UUID initiativeId = UUID.randomUUID();
        UUID systemId = UUID.randomUUID();
        UUID assessmentId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        UUID controlId = UUID.randomUUID();
        ControlImplementationId implementationId = ControlImplementationId.generate();

        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'APPROVED', 'HIGH', false, false, ?)
                """, initiativeId, ORGANIZATION_UUID, Timestamp.from(CREATED_AT));
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'AI System', 'System description', 'REGISTERED', ?)
                """, systemId, ORGANIZATION_UUID, initiativeId, Timestamp.from(CREATED_AT));
        jdbc.update("""
                insert into risk_assessments
                    (id, system_id, purpose, deployment_context, assessed_at)
                values (?, ?, 'Governance approval', 'Public sector deployment', ?)
                """, assessmentId, systemId, Timestamp.from(CREATED_AT));
        jdbc.update("""
                insert into risk_assessment_findings
                    (id, risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, ?, 0, 'Bias risk', 'MEDIUM', 'HIGH')
                """, findingId, assessmentId);
        jdbc.update("""
                insert into controls
                    (id, risk_assessment_id, risk_finding_id, name, description, created_at)
                values (?, ?, ?, 'Human review gate', 'Require approval.', ?)
                """, controlId, assessmentId, findingId, Timestamp.from(CREATED_AT));
        jdbc.update("""
                insert into control_implementations
                    (id, control_id, description, implemented_at)
                values (?, ?, 'Evidence package attached.', ?)
                """, implementationId.value(), controlId, Timestamp.from(CREATED_AT));

        return implementations.delegate.findById(implementationId)
                .orElseThrow(() -> new AssertionError("Seeded control implementation not found: " + implementationId));
    }

    private Evidence evidence(ControlImplementationId implementationId) {
        return Evidence.builder()
                .id(EvidenceId.generate())
                .controlImplementationId(implementationId)
                .description("Signed approval memo")
                .reference("archive://approval.pdf")
                .recordedAt(RECORDED_AT)
                .build();
    }

    private void assertEvidenceRow(Evidence recorded) {
        jdbc.queryForObject("""
                select control_implementation_id, description, reference, recorded_at
                from evidence where id = ?
                """, (row, rowNumber) -> {
            assertEquals(recorded.controlImplementationId().value(),
                    row.getObject("control_implementation_id", UUID.class));
            assertEquals(recorded.description(), row.getString("description"));
            assertEquals(recorded.reference(), row.getString("reference"));
            assertEquals(RECORDED_AT, row.getTimestamp("recorded_at").toInstant());
            return true;
        }, recorded.id().value());
    }

    private int evidenceRowCount() {
        return jdbc.queryForObject("select count(*) from evidence", Integer.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({EvidenceApplicationConfiguration.class, AIInitiativePersistenceConfiguration.class})
    static class TestConfiguration {

        @Bean
        Clock evidenceClock() {
            return Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        ObservedControlImplementationRepository observedControlImplementationRepository(
                @Qualifier("controlImplementationRepository") ControlImplementationRepository delegate,
                JdbcTemplate jdbc
        ) {
            return new ObservedControlImplementationRepository(delegate, jdbc);
        }

        @Bean
        @Primary
        ObservedEvidenceRepository observedEvidenceRepository(
                @Qualifier("evidenceRepository") EvidenceRepository delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            return new ObservedEvidenceRepository(delegate, entityManager, jdbc);
        }
    }

    static class ObservedControlImplementationRepository implements ControlImplementationRepository {
        private final ControlImplementationRepository delegate;
        private final JdbcTemplate jdbc;
        private Long findTransaction;
        private boolean findReadOnly;
        private int finds;

        ObservedControlImplementationRepository(ControlImplementationRepository delegate, JdbcTemplate jdbc) {
            this.delegate = delegate;
            this.jdbc = jdbc;
        }

        @Override
        public ControlImplementation create(ControlImplementation implementation) {
            return delegate.create(implementation);
        }

        @Override
        public Optional<ControlImplementation> findById(ControlImplementationId id) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before finding the control implementation");
            findTransaction = jdbc.queryForObject("select txid_current()", Long.class);
            findReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            finds++;
            return delegate.findById(id);
        }

        void reset() {
            findTransaction = null;
            findReadOnly = false;
            finds = 0;
        }
    }

    static class ObservedEvidenceRepository implements EvidenceRepository {
        private final EvidenceRepository delegate;
        private final EntityManager entityManager;
        private final JdbcTemplate jdbc;
        private boolean failAfterFlush;
        private Long createTransaction;
        private Long findTransaction;
        private boolean createReadOnly;
        private boolean findReadOnly;
        private com.leori.enia.evidence.domain.EvidenceId flushedEvidenceId;
        private ControlImplementationId flushedControlImplementationId;
        private int creates;
        private int finds;

        ObservedEvidenceRepository(EvidenceRepository delegate, EntityManager entityManager, JdbcTemplate jdbc) {
            this.delegate = delegate;
            this.entityManager = entityManager;
            this.jdbc = jdbc;
        }

        @Override
        public Evidence create(Evidence recorded) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before creating evidence");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            createTransaction = jdbc.queryForObject("select txid_current()", Long.class);
            createReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            creates++;
            Evidence result = delegate.create(recorded);
            entityManager.flush();
            flushedEvidenceId = recorded.id();
            flushedControlImplementationId = recorded.controlImplementationId();
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from evidence where id = ?", Integer.class, recorded.id().value()));
            if (failAfterFlush) {
                throw new FailureAfterFlush();
            }
            return result;
        }

        @Override
        public Optional<Evidence> findById(EvidenceId id) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before finding evidence");
            findTransaction = jdbc.queryForObject("select txid_current()", Long.class);
            findReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            finds++;
            return delegate.findById(id);
        }

        void reset() {
            failAfterFlush = false;
            createTransaction = null;
            findTransaction = null;
            createReadOnly = false;
            findReadOnly = false;
            flushedEvidenceId = null;
            flushedControlImplementationId = null;
            creates = 0;
            finds = 0;
        }
    }

    static class FailureAfterFlush extends RuntimeException {
    }
}
