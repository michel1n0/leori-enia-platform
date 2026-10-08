package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.AISystemGovernanceSummary;
import com.leori.enia.governance.application.port.AISystemGovernanceSummaryRepository;
import com.leori.enia.governance.domain.AISystemId;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers
@SpringJUnitConfig(PostgreSQLAISystemGovernanceSummaryProjectionIntegrationTest.PersistenceConfiguration.class)
class PostgreSQLAISystemGovernanceSummaryProjectionIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-05T14:00:00.123456Z");
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
    private AISystemGovernanceSummaryRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearRows() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        jdbc.update("delete from evidence");
        jdbc.update("delete from control_implementations");
        jdbc.update("delete from controls");
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_models");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void existing_system_with_zero_related_data_returns_zero_counts() {
        AISystemId systemId = seedSystem();

        AISystemGovernanceSummary summary = repository.summarize(systemId);

        assertSummary(summary, systemId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void critical_multiplication_fixture_counts_each_relation_without_inflation() {
        AISystemId systemId = seedSystem();
        seedModel(systemId);
        seedModel(systemId);
        UUID assessment = seedAssessment(systemId);
        UUID firstFinding = seedFinding(assessment, 0);
        seedFinding(assessment, 1);
        UUID firstControl = seedControl(assessment, firstFinding);
        UUID secondControl = seedControl(assessment, firstFinding);
        UUID firstImplementation = seedImplementation(firstControl);
        UUID secondImplementation = seedImplementation(firstControl);
        seedEvidence(firstImplementation);
        seedEvidence(firstImplementation);
        seedEvidence(secondImplementation);

        AISystemGovernanceSummary summary = repository.summarize(systemId);

        assertSummary(summary, systemId, 1, 2, 2, 1, 2, 3, 1, 1, 0, 2);
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from control_implementations where control_id = ?", Integer.class,
                secondControl));
    }

    @Test
    void implementation_with_zero_evidence_increments_implementations_without_evidence() {
        AISystemId systemId = seedSystem();
        UUID assessment = seedAssessment(systemId);
        UUID finding = seedFinding(assessment, 0);
        UUID control = seedControl(assessment, finding);
        seedImplementation(control);

        AISystemGovernanceSummary summary = repository.summarize(systemId);

        assertSummary(summary, systemId, 1, 1, 1, 1, 1, 0, 0, 0, 1, 0);
    }

    @Test
    void implemented_control_count_is_distinct_when_one_control_has_multiple_implementations() {
        AISystemId systemId = seedSystem();
        UUID assessment = seedAssessment(systemId);
        UUID finding = seedFinding(assessment, 0);
        UUID control = seedControl(assessment, finding);
        seedImplementation(control);
        seedImplementation(control);

        AISystemGovernanceSummary summary = repository.summarize(systemId);

        assertSummary(summary, systemId, 1, 1, 1, 1, 2, 0, 0, 0, 2, 0);
    }

    @Test
    void multiple_evidence_rows_do_not_inflate_control_or_implementation_counts() {
        AISystemId systemId = seedSystem();
        UUID assessment = seedAssessment(systemId);
        UUID finding = seedFinding(assessment, 0);
        UUID control = seedControl(assessment, finding);
        UUID implementation = seedImplementation(control);
        seedEvidence(implementation);
        seedEvidence(implementation);

        AISystemGovernanceSummary summary = repository.summarize(systemId);

        assertSummary(summary, systemId, 1, 1, 1, 1, 1, 2, 0, 0, 0, 0);
    }

    private AISystemId seedSystem() {
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'DRAFT', 'NOT_ASSESSED', false, false, ?)
                """, initiativeId, ORGANIZATION_UUID, Timestamp.from(CREATED_AT));
        AISystemId systemId = AISystemId.generate();
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'System', 'Desc', 'REGISTERED', ?)
                """, systemId.value(), ORGANIZATION_UUID, initiativeId, Timestamp.from(CREATED_AT));
        return systemId;
    }

    private void seedModel(AISystemId systemId) {
        jdbc.update("""
                insert into ai_models (id, system_id, name, description, provider, created_at)
                values (?, ?, 'Model', 'Description', 'Provider', ?)
                """, UUID.randomUUID(), systemId.value(), Timestamp.from(CREATED_AT));
    }

    private UUID seedAssessment(AISystemId systemId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into risk_assessments (id, system_id, purpose, deployment_context, assessed_at)
                values (?, ?, 'Governance approval', 'Public sector deployment', ?)
                """, id, systemId.value(), Timestamp.from(CREATED_AT));
        return id;
    }

    private UUID seedFinding(UUID assessmentId, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into risk_assessment_findings
                    (id, risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, ?, ?, 'Bias risk', 'MEDIUM', 'HIGH')
                """, id, assessmentId, position);
        return id;
    }

    private UUID seedControl(UUID assessmentId, UUID findingId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into controls (id, risk_assessment_id, risk_finding_id, name, description, created_at)
                values (?, ?, ?, 'Human review gate', 'Require documented human approval.', ?)
                """, id, assessmentId, findingId, Timestamp.from(CREATED_AT));
        return id;
    }

    private UUID seedImplementation(UUID controlId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into control_implementations (id, control_id, description, implemented_at)
                values (?, ?, 'Evidence package uploaded and reviewed.', ?)
                """, id, controlId, Timestamp.from(CREATED_AT));
        return id;
    }

    private void seedEvidence(UUID implementationId) {
        jdbc.update("""
                insert into evidence (id, control_implementation_id, description, reference, recorded_at)
                values (?, ?, 'Signed approval minutes', 'evidence-vault:item', ?)
                """, UUID.randomUUID(), implementationId, Timestamp.from(CREATED_AT));
    }

    private void assertSummary(
            AISystemGovernanceSummary summary,
            AISystemId systemId,
            long riskAssessments,
            long findings,
            long controls,
            long implementedControls,
            long implementations,
            long evidence,
            long findingsWithoutControls,
            long controlsWithoutImplementation,
            long implementationsWithoutEvidence,
            long registeredModels
    ) {
        assertAll(
                () -> assertEquals(systemId, summary.aiSystemId()),
                () -> assertEquals(riskAssessments, summary.riskAssessmentCount()),
                () -> assertEquals(findings, summary.findingCount()),
                () -> assertEquals(controls, summary.controlCount()),
                () -> assertEquals(implementedControls, summary.implementedControlCount()),
                () -> assertEquals(implementations, summary.controlImplementationCount()),
                () -> assertEquals(evidence, summary.evidenceCount()),
                () -> assertEquals(findingsWithoutControls, summary.findingsWithoutControls()),
                () -> assertEquals(controlsWithoutImplementation, summary.controlsWithoutImplementation()),
                () -> assertEquals(implementationsWithoutEvidence, summary.implementationsWithoutEvidence()),
                () -> assertEquals(registeredModels, summary.registeredModelCount())
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(GovernancePersistenceConfiguration.class)
    static class PersistenceConfiguration {
    }
}
