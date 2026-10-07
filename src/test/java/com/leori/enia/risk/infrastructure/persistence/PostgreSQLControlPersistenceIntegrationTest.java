package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.ContextOfUse;
import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlRepository;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFinding;
import com.leori.enia.risk.domain.RiskFindingId;
import com.leori.enia.risk.domain.event.ControlDefined;
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
@SpringJUnitConfig(PostgreSQLControlPersistenceIntegrationTest.PersistenceConfiguration.class)
class PostgreSQLControlPersistenceIntegrationTest {

    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00.123456Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-02T10:15:30.123456Z");
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
    private ControlRepository repository;

    @Autowired
    private RiskAssessmentRepository assessmentRepository;

    @Autowired
    private ControlPersistenceMapper mapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void clearRows() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        jdbc.update("delete from controls");
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void creates_and_commits_control_preserving_events_but_not_replaying_them_on_reload() {
        RiskAssessment assessment = assessmentRepository.create(assessment(RiskAssessmentId.generate(), seedSystem()));
        RiskFinding finding = assessment.findings().getFirst();
        Control input = control(assessment.id(), finding.id());
        var events = input.domainEvents();

        Control result = repository.create(input);

        assertSame(input, result);
        assertEquals(1, events.size());
        assertInstanceOf(ControlDefined.class, events.getFirst());
        assertEquals(events, input.domainEvents());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, controlRowCount());
        assertControlRow(input);
        Control restored = reload(input.id());
        assertState(input, restored);
        assertTrue(restored.domainEvents().isEmpty());
        assertEquals("9", flyway.info().current().getVersion().toString());
        flyway.validate();
    }

    @Test
    void finds_existing_control_without_replaying_domain_events() {
        RiskAssessment assessment = assessmentRepository.create(assessment(RiskAssessmentId.generate(), seedSystem()));
        Control input = repository.create(control(assessment.id(), assessment.findings().getFirst().id()));

        var result = repository.findById(input.id());

        assertTrue(result.isPresent());
        assertState(input, result.get());
        assertTrue(result.get().domainEvents().isEmpty());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, controlRowCount());
    }

    @Test
    void missing_control_returns_empty_optional() {
        assertTrue(repository.findById(ControlId.generate()).isEmpty());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void rejects_null_find_id_before_accessing_persistence() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> repository.findById(null));

        assertEquals("Control id is required", exception.getMessage());
    }

    @Test
    void accepts_control_when_finding_belongs_to_the_referenced_assessment() {
        RiskAssessment assessment = assessmentRepository.create(assessment(RiskAssessmentId.generate(), seedSystem()));
        Control input = control(assessment.id(), assessment.findings().get(1).id());

        repository.create(input);

        assertEquals(1, controlRowCount());
        assertState(input, reload(input.id()));
    }

    @Test
    void rejects_control_when_finding_belongs_to_a_different_assessment() {
        AISystemId firstSystemId = seedSystem();
        AISystemId secondSystemId = seedSystem();
        RiskAssessment first = assessmentRepository.create(assessment(RiskAssessmentId.generate(), firstSystemId));
        RiskAssessment second = assessmentRepository.create(assessment(RiskAssessmentId.generate(), secondSystemId));
        Control mismatched = control(first.id(), second.findings().getFirst().id());

        assertThrows(PersistenceException.class, () -> repository.create(mismatched));

        assertEquals(0, controlRowCount());
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
                .findings(List.of(
                        new RiskFinding(RiskFindingId.generate(), "Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH),
                        new RiskFinding(RiskFindingId.generate(), "Privacy risk", Likelihood.LOW, ImpactMagnitude.MEDIUM)
                ))
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

    private int controlRowCount() {
        return jdbc.queryForObject("select count(*) from controls", Integer.class);
    }

    private Control reload(ControlId id) {
        var entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            var entity = entityManager.find(ControlJpaEntity.class, id.value());
            assertNotNull(entity);
            Control restored = mapper.toDomain(entity);
            entityManager.getTransaction().commit();
            return restored;
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    private void assertControlRow(Control expected) {
        jdbc.queryForObject("""
                select id, risk_assessment_id, risk_finding_id, name, description, created_at
                from controls where id = ?
                """, (row, rowNumber) -> {
            assertAll(
                    () -> assertEquals(expected.id().value(), row.getObject("id", UUID.class)),
                    () -> assertEquals(expected.riskAssessmentId().value(), row.getObject("risk_assessment_id", UUID.class)),
                    () -> assertEquals(expected.riskFindingId().value(), row.getObject("risk_finding_id", UUID.class)),
                    () -> assertEquals(expected.name(), row.getString("name")),
                    () -> assertEquals(expected.description(), row.getString("description")),
                    () -> assertEquals(CREATED_AT, row.getTimestamp("created_at").toInstant())
            );
            return true;
        }, expected.id().value());
    }

    private void assertState(Control expected, Control actual) {
        assertAll(
                () -> assertEquals(expected.id(), actual.id()),
                () -> assertEquals(expected.riskAssessmentId(), actual.riskAssessmentId()),
                () -> assertEquals(expected.riskFindingId(), actual.riskFindingId()),
                () -> assertEquals(expected.name(), actual.name()),
                () -> assertEquals(expected.description(), actual.description()),
                () -> assertEquals(expected.createdAt(), actual.createdAt())
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RiskPersistenceConfiguration.class, GovernancePersistenceConfiguration.class,
            AIInitiativePersistenceConfiguration.class})
    static class PersistenceConfiguration {
    }
}
