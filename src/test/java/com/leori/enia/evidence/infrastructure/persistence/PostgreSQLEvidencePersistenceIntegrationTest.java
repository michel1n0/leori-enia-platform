package com.leori.enia.evidence.infrastructure.persistence;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.evidence.domain.EvidenceRepository;
import com.leori.enia.evidence.domain.event.EvidenceRecorded;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.ContextOfUse;
import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import com.leori.enia.risk.domain.ControlRepository;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFinding;
import com.leori.enia.risk.domain.RiskFindingId;
import com.leori.enia.risk.infrastructure.persistence.RiskPersistenceConfiguration;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
@SpringJUnitConfig(PostgreSQLEvidencePersistenceIntegrationTest.PersistenceConfiguration.class)
class PostgreSQLEvidencePersistenceIntegrationTest {

    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00.123456Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-02T10:15:30.123456Z");
    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T12:30:45.123456Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T12:30:45.123456Z");
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
    private EvidenceRepository repository;

    @Autowired
    private ControlImplementationRepository implementationRepository;

    @Autowired
    private ControlRepository controlRepository;

    @Autowired
    private RiskAssessmentRepository assessmentRepository;

    @Autowired
    private EvidencePersistenceMapper mapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void clearRows() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        jdbc.update("delete from evidence");
        jdbc.update("delete from control_implementations");
        jdbc.update("delete from controls");
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void flyway_creates_evidence_table_with_latest_version_and_expected_constraints() {
        assertEquals("12", flyway.info().current().getVersion().toString());
        flyway.validate();
        Set<String> constraints = Set.copyOf(jdbc.queryForList("""
                select constraint_name from information_schema.table_constraints
                where table_schema = 'public' and table_name = 'evidence'
                """, String.class));

        assertTrue(constraints.containsAll(Set.of("pk_evidence", "fk_evidence_control_implementation")));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_schema = 'public' and table_name = 'evidence'
                    and constraint_type = 'UNIQUE'
                """, Integer.class));
    }

    @Test
    void persists_valid_evidence_with_scalar_db_values_and_preserved_events() {
        ControlImplementation implementation = seedImplementation();
        Evidence input = evidence(implementation.id(), "Signed approval minutes", "evidence-vault:item-123");
        var events = input.domainEvents();

        Evidence result = repository.create(input);

        assertSame(input, result);
        assertEquals(1, events.size());
        assertInstanceOf(EvidenceRecorded.class, events.getFirst());
        assertEquals(events, input.domainEvents());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, evidenceRowCount());
        assertEvidenceRow(input);
        Evidence restored = reload(input.id());
        assertState(input, restored);
        assertTrue(restored.domainEvents().isEmpty());
    }

    @Test
    void finds_existing_evidence_with_rehydrated_state_and_no_pending_events() {
        ControlImplementation implementation = seedImplementation();
        Evidence input = evidence(implementation.id(), "Signed approval minutes", "evidence-vault:item-123");
        repository.create(input);

        var result = repository.findById(input.id());

        assertTrue(result.isPresent());
        Evidence found = result.orElseThrow();
        assertAll(
                () -> assertEquals(input.id(), found.id()),
                () -> assertEquals(input.controlImplementationId(), found.controlImplementationId()),
                () -> assertEquals(input.description(), found.description()),
                () -> assertEquals(input.reference(), found.reference()),
                () -> assertEquals(input.recordedAt(), found.recordedAt()),
                () -> assertEquals(0, found.domainEvents().size())
        );
        assertEquals(1, evidenceRowCount());
    }

    @Test
    void missing_evidence_find_returns_empty() {
        assertTrue(repository.findById(EvidenceId.generate()).isEmpty());
    }

    @Test
    void read_does_not_mutate_or_create_events() {
        ControlImplementation implementation = seedImplementation();
        Evidence input = evidence(implementation.id(), "Signed approval minutes", "evidence-vault:item-123");
        repository.create(input);
        input.clearDomainEvents();

        Evidence first = repository.findById(input.id()).orElseThrow();
        Evidence second = repository.findById(input.id()).orElseThrow();

        assertEquals(1, evidenceRowCount());
        assertEquals(0, first.domainEvents().size());
        assertEquals(0, second.domainEvents().size());
        assertEvidenceRow(input);
    }

    @Test
    void database_fk_accepts_existing_control_implementation() {
        ControlImplementation implementation = seedImplementation();
        Evidence input = evidence(implementation.id(), "Signed approval minutes", "evidence-vault:item-123");

        repository.create(input);

        assertEquals(1, evidenceRowCount());
        assertEvidenceRow(input);
    }

    @Test
    void database_fk_rejects_missing_control_implementation_as_persistence_exception() {
        Evidence input = evidence(ControlImplementationId.generate(), "Cannot reference a missing implementation.",
                "evidence-vault:missing");

        assertThrows(PersistenceException.class, () -> repository.create(input));

        assertEquals(0, evidenceRowCount());
    }

    @Test
    void rejects_duplicate_evidence_id_without_overwriting() {
        ControlImplementation implementation = seedImplementation();
        Evidence original = repository.create(evidence(implementation.id(), "Signed approval minutes",
                "evidence-vault:item-123"));
        Evidence duplicateId = evidence(original.id(), implementation.id(), "Second evidence",
                "evidence-vault:item-456");

        assertThrows(PersistenceException.class, () -> repository.create(duplicateId));

        assertEquals(1, evidenceRowCount());
        assertState(original, reload(original.id()));
    }

    @Test
    void allows_multiple_evidence_rows_for_the_same_control_implementation() {
        ControlImplementation implementation = seedImplementation();
        Evidence first = evidence(implementation.id(), "Signed approval minutes", "evidence-vault:item-123");
        Evidence second = evidence(implementation.id(), "Monitoring report", "evidence-vault:item-456");

        repository.create(first);
        repository.create(second);

        assertEquals(2, evidenceRowCount());
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from evidence where control_implementation_id = ?",
                Integer.class,
                implementation.id().value()));
    }

    @Test
    void finds_evidence_by_control_implementation_in_recorded_at_then_id_order() {
        ControlImplementation firstImplementation = seedImplementation();
        ControlImplementation secondImplementation = implementationRepository.create(implementation(seedControl().id()));
        Instant firstRecordedAt = Instant.parse("2026-10-04T12:30:00.123456Z");
        Instant secondRecordedAt = Instant.parse("2026-10-04T12:45:00.123456Z");
        Evidence first = evidence(
                new EvidenceId(UUID.fromString("90000000-0000-0000-0000-000000000001")),
                firstImplementation.id(),
                "Signed approval minutes",
                "evidence-vault:item-a1",
                firstRecordedAt
        );
        Evidence second = evidence(
                new EvidenceId(UUID.fromString("90000000-0000-0000-0000-000000000003")),
                firstImplementation.id(),
                "Second monitoring report",
                "evidence-vault:item-a2",
                secondRecordedAt
        );
        Evidence third = evidence(
                new EvidenceId(UUID.fromString("90000000-0000-0000-0000-000000000002")),
                firstImplementation.id(),
                "First monitoring report",
                "evidence-vault:item-a3",
                secondRecordedAt
        );
        Evidence otherImplementationEvidence = evidence(
                new EvidenceId(UUID.fromString("90000000-0000-0000-0000-000000000004")),
                secondImplementation.id(),
                "Other implementation evidence",
                "evidence-vault:item-b1",
                firstRecordedAt
        );
        repository.create(second);
        repository.create(otherImplementationEvidence);
        repository.create(third);
        repository.create(first);

        List<Evidence> result = repository.findByControlImplementationId(firstImplementation.id());

        assertEquals(List.of(first.id(), third.id(), second.id()), result.stream().map(Evidence::id).toList());
        assertAll(
                () -> assertState(first, result.get(0)),
                () -> assertState(third, result.get(1)),
                () -> assertState(second, result.get(2)),
                () -> assertEquals(0, result.get(0).domainEvents().size()),
                () -> assertEquals(0, result.get(1).domainEvents().size()),
                () -> assertEquals(0, result.get(2).domainEvents().size())
        );
        assertFalse(result.stream().map(Evidence::id).toList().contains(otherImplementationEvidence.id()));
        assertEquals(4, evidenceRowCount());
    }

    @Test
    void finds_empty_evidence_list_for_existing_control_implementation_without_rows() {
        ControlImplementation implementation = seedImplementation();

        List<Evidence> result = repository.findByControlImplementationId(implementation.id());

        assertTrue(result.isEmpty());
        assertEquals(0, evidenceRowCount());
    }

    private ControlImplementation seedImplementation() {
        Control control = seedControl();
        return implementationRepository.create(implementation(control.id()));
    }

    private Control seedControl() {
        RiskAssessment assessment = assessmentRepository.create(assessment(RiskAssessmentId.generate(), seedSystem()));
        return controlRepository.create(control(assessment.id(), assessment.findings().getFirst().id()));
    }

    private AISystemId seedSystem() {
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'DRAFT', 'NOT_ASSESSED', false, false, ?)
                """, initiativeId, ORGANIZATION_UUID, Timestamp.from(ASSESSED_AT));
        AISystemId systemId = AISystemId.generate();
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'System', 'Desc', 'REGISTERED', ?)
                """, systemId.value(), ORGANIZATION_UUID, initiativeId, Timestamp.from(ASSESSED_AT));
        return systemId;
    }

    private RiskAssessment assessment(RiskAssessmentId id, AISystemId systemId) {
        return RiskAssessment.builder()
                .id(id)
                .systemId(systemId)
                .contextOfUse(new ContextOfUse("Governance approval", "Public sector deployment"))
                .findings(List.of(new RiskFinding(RiskFindingId.generate(), "Bias risk", Likelihood.MEDIUM,
                        ImpactMagnitude.HIGH)))
                .assessedAt(ASSESSED_AT)
                .build();
    }

    private Control control(RiskAssessmentId assessmentId, RiskFindingId findingId) {
        return Control.builder()
                .id(ControlId.generate())
                .riskAssessmentId(assessmentId)
                .riskFindingId(findingId)
                .name("Human review gate")
                .description("Require documented human approval before deployment.")
                .createdAt(CREATED_AT)
                .build();
    }

    private ControlImplementation implementation(ControlId controlId) {
        return ControlImplementation.builder()
                .id(ControlImplementationId.generate())
                .controlId(controlId)
                .description("Evidence package uploaded and reviewed.")
                .implementedAt(IMPLEMENTED_AT)
                .build();
    }

    private Evidence evidence(ControlImplementationId implementationId, String description, String reference) {
        return evidence(EvidenceId.generate(), implementationId, description, reference);
    }

    private Evidence evidence(EvidenceId id, ControlImplementationId implementationId, String description, String reference) {
        return evidence(id, implementationId, description, reference, RECORDED_AT);
    }

    private Evidence evidence(
            EvidenceId id,
            ControlImplementationId implementationId,
            String description,
            String reference,
            Instant recordedAt
    ) {
        return Evidence.builder()
                .id(id)
                .controlImplementationId(implementationId)
                .description(description)
                .reference(reference)
                .recordedAt(recordedAt)
                .build();
    }

    private int evidenceRowCount() {
        return jdbc.queryForObject("select count(*) from evidence", Integer.class);
    }

    private Evidence reload(EvidenceId id) {
        var entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            var entity = entityManager.find(EvidenceJpaEntity.class, id.value());
            assertNotNull(entity);
            Evidence restored = mapper.toDomain(entity);
            entityManager.getTransaction().commit();
            return restored;
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    private void assertEvidenceRow(Evidence expected) {
        jdbc.queryForObject("""
                select id, control_implementation_id, description, reference, recorded_at
                from evidence where id = ?
                """, (row, rowNumber) -> {
            assertAll(
                    () -> assertEquals(expected.id().value(), row.getObject("id", UUID.class)),
                    () -> assertEquals(expected.controlImplementationId().value(),
                            row.getObject("control_implementation_id", UUID.class)),
                    () -> assertEquals(expected.description(), row.getString("description")),
                    () -> assertEquals(expected.reference(), row.getString("reference")),
                    () -> assertEquals(RECORDED_AT, row.getTimestamp("recorded_at").toInstant())
            );
            return true;
        }, expected.id().value());
    }

    private void assertState(Evidence expected, Evidence actual) {
        assertAll(
                () -> assertEquals(expected.id(), actual.id()),
                () -> assertEquals(expected.controlImplementationId(), actual.controlImplementationId()),
                () -> assertEquals(expected.description(), actual.description()),
                () -> assertEquals(expected.reference(), actual.reference()),
                () -> assertEquals(expected.recordedAt(), actual.recordedAt())
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({EvidencePersistenceConfiguration.class, RiskPersistenceConfiguration.class,
            GovernancePersistenceConfiguration.class, AIInitiativePersistenceConfiguration.class})
    static class PersistenceConfiguration {
    }
}
