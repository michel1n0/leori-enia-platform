package com.leori.enia.risk.infrastructure.persistence;

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
import com.leori.enia.risk.domain.event.ControlImplementationRecorded;
import jakarta.persistence.PersistenceException;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringJUnitConfig(PostgreSQLControlImplementationPersistenceIntegrationTest.PersistenceConfiguration.class)
class PostgreSQLControlImplementationPersistenceIntegrationTest {

    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00.123456Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-02T10:15:30.123456Z");
    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T12:30:45.123456Z");
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
    private ControlImplementationRepository repository;

    @Autowired
    private ControlRepository controlRepository;

    @Autowired
    private RiskAssessmentRepository assessmentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearRows() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        jdbc.update("delete from control_implementations");
        jdbc.update("delete from controls");
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void persists_valid_control_implementation_with_scalar_db_values_and_preserved_events() {
        Control control = seedControl();
        ControlImplementation input = implementation(control.id(), "Evidence package uploaded and reviewed.");
        var events = input.domainEvents();

        ControlImplementation result = repository.create(input);

        assertSame(input, result);
        assertEquals(1, events.size());
        assertInstanceOf(ControlImplementationRecorded.class, events.getFirst());
        assertEquals(events, input.domainEvents());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, implementationRowCount());
        assertImplementationRow(input);
    }

    @Test
    void database_fk_rejects_missing_control_as_persistence_exception() {
        ControlImplementation input = implementation(ControlId.generate(), "Cannot reference a missing control.");

        assertThrows(PersistenceException.class, () -> repository.create(input));

        assertEquals(0, implementationRowCount());
    }

    @Test
    void allows_multiple_implementations_for_the_same_control_without_unique_control_id_constraint() {
        Control control = seedControl();
        ControlImplementation first = implementation(control.id(), "Initial operating procedure published.");
        ControlImplementation second = implementation(control.id(), "Evidence package attached.");

        repository.create(first);
        repository.create(second);

        assertEquals(2, implementationRowCount());
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from control_implementations where control_id = ?",
                Integer.class,
                control.id().value()));
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
                .findings(List.of(new RiskFinding(RiskFindingId.generate(), "Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH)))
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

    private ControlImplementation implementation(ControlId controlId, String description) {
        return ControlImplementation.builder()
                .id(ControlImplementationId.generate())
                .controlId(controlId)
                .description(description)
                .implementedAt(IMPLEMENTED_AT)
                .build();
    }

    private int implementationRowCount() {
        return jdbc.queryForObject("select count(*) from control_implementations", Integer.class);
    }

    private void assertImplementationRow(ControlImplementation expected) {
        jdbc.queryForObject("""
                select id, control_id, description, implemented_at
                from control_implementations where id = ?
                """, (row, rowNumber) -> {
            assertAll(
                    () -> assertEquals(expected.id().value(), row.getObject("id", UUID.class)),
                    () -> assertEquals(expected.controlId().value(), row.getObject("control_id", UUID.class)),
                    () -> assertEquals(expected.description(), row.getString("description")),
                    () -> assertEquals(IMPLEMENTED_AT, row.getTimestamp("implemented_at").toInstant())
            );
            return true;
        }, expected.id().value());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RiskPersistenceConfiguration.class, GovernancePersistenceConfiguration.class,
            AIInitiativePersistenceConfiguration.class})
    static class PersistenceConfiguration {
    }
}
