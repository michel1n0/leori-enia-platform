package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.ContextOfUse;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFinding;
import com.leori.enia.risk.domain.RiskFindingId;
import com.leori.enia.risk.domain.event.RiskAssessmentRecorded;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@SpringJUnitConfig(PostgreSQLRiskAssessmentPersistenceIntegrationTest.PersistenceConfiguration.class)
class PostgreSQLRiskAssessmentPersistenceIntegrationTest {

    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00.123456Z");
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
    private RiskAssessmentRepository repository;

    @Autowired
    private RiskAssessmentPersistenceMapper mapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void clearRows() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        jdbc.update("delete from risk_assessment_findings");
        jdbc.update("delete from risk_assessments");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void creates_and_commits_root_and_ordered_duplicate_findings_preserving_events_but_not_replaying_them_on_reload() {
        AISystemId systemId = seedSystem();
        RiskAssessment input = assessment(RiskAssessmentId.generate(), systemId);
        var events = input.domainEvents();

        RiskAssessment result = repository.create(input);

        assertSame(input, result);
        assertEquals(1, events.size());
        assertInstanceOf(RiskAssessmentRecorded.class, events.getFirst());
        assertEquals(events, input.domainEvents());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, assessmentRowCount());
        assertEquals(3, findingRowCount());
        assertRootRow(input);
        assertFindingRows(input);
        RiskAssessment restored = reload(input.id());
        assertState(input, restored);
        assertTrue(restored.domainEvents().isEmpty());
        assertEquals("8", flyway.info().current().getVersion().toString());
        flyway.validate();
    }

    @Test
    void finds_existing_assessment_by_id_rehydrating_complete_state_without_events() {
        AISystemId systemId = seedSystem();
        RiskAssessment input = repository.create(assessment(RiskAssessmentId.generate(), systemId));

        RiskAssessment found = repository.findById(input.id()).orElseThrow();

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertState(input, found);
        assertEquals(input.findings(), found.findings());
        assertEquals("Bias risk", found.findings().get(0).description());
        assertEquals("Privacy risk", found.findings().get(1).description());
        assertEquals("Bias risk", found.findings().get(2).description());
        assertEquals(found.findings().get(0).description(), found.findings().get(2).description());
        assertTrue(!found.findings().get(0).id().equals(found.findings().get(2).id()));
        assertTrue(found.domainEvents().isEmpty());
    }

    @Test
    void returns_empty_when_assessment_is_missing() {
        assertTrue(repository.findById(RiskAssessmentId.generate()).isEmpty());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void rejects_duplicate_id_without_overwriting() {
        AISystemId systemId = seedSystem();
        RiskAssessment original = repository.create(assessment(RiskAssessmentId.generate(), systemId));
        RiskAssessment duplicateId = assessment(original.id(), systemId);

        assertThrows(PersistenceException.class, () -> repository.create(duplicateId));

        assertEquals(1, assessmentRowCount());
        assertEquals(3, findingRowCount());
        assertState(original, reload(original.id()));
    }

    @Test
    void rejects_missing_system_id_via_fk_constraint() {
        RiskAssessment assessment = assessment(RiskAssessmentId.generate(), AISystemId.generate());

        assertThrows(PersistenceException.class, () -> repository.create(assessment));

        assertEquals(0, assessmentRowCount());
        assertEquals(0, findingRowCount());
    }

    @Test
    void prevents_deleting_a_referenced_system_without_cascading() {
        AISystemId systemId = seedSystem();
        RiskAssessment original = repository.create(assessment(RiskAssessmentId.generate(), systemId));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from ai_systems where id = ?", systemId.value()));

        assertEquals(1, jdbc.queryForObject("select count(*) from ai_systems", Integer.class));
        assertState(original, reload(original.id()));
    }

    @Test
    void persists_long_text_without_arbitrary_limits() {
        AISystemId systemId = seedSystem();
        RiskAssessment input = RiskAssessment.builder()
                .id(RiskAssessmentId.generate())
                .systemId(systemId)
                .contextOfUse(new ContextOfUse("p".repeat(5000), "d".repeat(5000)))
                .findings(List.of(new RiskFinding(RiskFindingId.generate(), "f".repeat(5000), Likelihood.HIGH, ImpactMagnitude.HIGH)))
                .assessedAt(ASSESSED_AT)
                .build();

        repository.create(input);

        assertState(input, reload(input.id()));
    }

    @Test
    void joins_outer_transaction_and_rolls_back_an_already_flushed_root_and_findings() {
        AISystemId systemId = seedSystem();
        RiskAssessment input = assessment(RiskAssessmentId.generate(), systemId);

        assertThrows(FailureAfterFlush.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(transaction -> {
                    assertSame(input, repository.create(input));
                    assertEquals(1, assessmentRowCount());
                    assertEquals(3, findingRowCount());
                    throw new FailureAfterFlush();
                }));

        assertEquals(0, assessmentRowCount());
        assertEquals(0, findingRowCount());
        assertEquals(1, input.domainEvents().size());
    }

    @Test
    void finding_checks_reject_unsupported_stored_values() {
        RiskAssessment original = repository.create(assessment(RiskAssessmentId.generate(), seedSystem()));

        assertAll(
                () -> assertThrows(DataIntegrityViolationException.class,
                        () -> jdbc.update("""
                                insert into risk_assessment_findings
                                    (id, risk_assessment_id, position, description, likelihood, impact_magnitude)
                                values (?, ?, -1, 'Invalid', 'LOW', 'LOW')
                                """, UUID.randomUUID(), original.id().value())),
                () -> assertThrows(DataIntegrityViolationException.class,
                        () -> jdbc.update("""
                                insert into risk_assessment_findings
                                    (id, risk_assessment_id, position, description, likelihood, impact_magnitude)
                                values (?, ?, 99, 'Invalid', 'IMPOSSIBLE', 'LOW')
                                """, UUID.randomUUID(), original.id().value())),
                () -> assertThrows(DataIntegrityViolationException.class,
                        () -> jdbc.update("""
                                insert into risk_assessment_findings
                                    (id, risk_assessment_id, position, description, likelihood, impact_magnitude)
                                values (?, ?, 99, 'Invalid', 'LOW', 'CATASTROPHIC')
                                """, UUID.randomUUID(), original.id().value()))
        );
        assertState(original, reload(original.id()));
    }

    @Test
    void upgrades_v7_to_v8_backfilling_finding_identity_and_preserving_existing_rows() {
        String schema = "risk_upgrade";
        Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).target("7").load().migrate();

        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into risk_upgrade.ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'DRAFT', 'NOT_ASSESSED', false, false, ?)
                """, initiativeId, ORGANIZATION_UUID, Timestamp.from(ASSESSED_AT));
        UUID systemId = UUID.randomUUID();
        jdbc.update("""
                insert into risk_upgrade.ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'System', 'Desc', 'REGISTERED', ?)
                """, systemId, ORGANIZATION_UUID, initiativeId, Timestamp.from(ASSESSED_AT));
        jdbc.update("""
                insert into risk_upgrade.ai_models (id, system_id, name, description, provider, created_at)
                values (?, ?, 'Model', 'Description', 'Provider', ?)
                """, UUID.randomUUID(), systemId, Timestamp.from(ASSESSED_AT));
        jdbc.update("""
                insert into risk_upgrade.ai_datasets (id, name, description, created_at)
                values (?, 'Dataset', 'Description', ?)
                """, UUID.randomUUID(), Timestamp.from(ASSESSED_AT));
        UUID assessmentId = UUID.randomUUID();
        jdbc.update("""
                insert into risk_upgrade.risk_assessments
                    (id, system_id, purpose, deployment_context, assessed_at)
                values (?, ?, 'Governance approval', 'Public sector deployment', ?)
                """, assessmentId, systemId, Timestamp.from(ASSESSED_AT));
        jdbc.update("""
                insert into risk_upgrade.risk_assessment_findings
                    (risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, 0, 'Bias risk', 'MEDIUM', 'HIGH')
                """, assessmentId);
        jdbc.update("""
                insert into risk_upgrade.risk_assessment_findings
                    (risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, 1, 'Bias risk', 'MEDIUM', 'HIGH')
                """, assessmentId);
        var initiativesBefore = jdbc.queryForList("select * from risk_upgrade.ai_initiatives order by id");
        var systemsBefore = jdbc.queryForList("select * from risk_upgrade.ai_systems order by id");
        var modelsBefore = jdbc.queryForList("select * from risk_upgrade.ai_models order by id");
        var datasetsBefore = jdbc.queryForList("select * from risk_upgrade.ai_datasets order by id");
        var assessmentsBefore = jdbc.queryForList("select * from risk_upgrade.risk_assessments order by id");

        Flyway upgrade = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).target("8").load();

        assertEquals(1, upgrade.migrate().migrationsExecuted);
        assertEquals("8", upgrade.info().current().getVersion().toString());
        upgrade.validate();
        assertEquals(initiativesBefore, jdbc.queryForList("select * from risk_upgrade.ai_initiatives order by id"));
        assertEquals(systemsBefore, jdbc.queryForList("select * from risk_upgrade.ai_systems order by id"));
        assertEquals(modelsBefore, jdbc.queryForList("select * from risk_upgrade.ai_models order by id"));
        assertEquals(datasetsBefore, jdbc.queryForList("select * from risk_upgrade.ai_datasets order by id"));
        assertEquals(assessmentsBefore, jdbc.queryForList("select * from risk_upgrade.risk_assessments order by id"));
        List<Map<String, Object>> findings = jdbc.queryForList("""
                select id, risk_assessment_id, position, description, likelihood, impact_magnitude
                from risk_upgrade.risk_assessment_findings
                order by position
                """);
        assertEquals(2, findings.size());
        assertNotNull(findings.get(0).get("id"));
        assertNotNull(findings.get(1).get("id"));
        assertTrue(!findings.get(0).get("id").equals(findings.get(1).get("id")));
        assertEquals(assessmentId, findings.get(0).get("risk_assessment_id"));
        assertEquals(0, findings.get(0).get("position"));
        assertEquals(1, findings.get(1).get("position"));
        assertEquals("Bias risk", findings.get(0).get("description"));
        assertRiskTableShape(schema);
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
                        new RiskFinding(RiskFindingId.generate(), "Privacy risk", Likelihood.LOW, ImpactMagnitude.MEDIUM),
                        new RiskFinding(RiskFindingId.generate(), "Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH)
                ))
                .assessedAt(ASSESSED_AT)
                .build();
    }

    private int assessmentRowCount() {
        return jdbc.queryForObject("select count(*) from risk_assessments", Integer.class);
    }

    private int findingRowCount() {
        return jdbc.queryForObject("select count(*) from risk_assessment_findings", Integer.class);
    }

    private RiskAssessment reload(RiskAssessmentId id) {
        // Independent context and transaction ensure this is a database read, not a cached entity.
        var entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            var entity = entityManager.find(RiskAssessmentJpaEntity.class, id.value());
            assertNotNull(entity);
            RiskAssessment restored = mapper.toDomain(entity);
            entityManager.getTransaction().commit();
            return restored;
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    private void assertRootRow(RiskAssessment expected) {
        jdbc.queryForObject("""
                select id, system_id, purpose, deployment_context, assessed_at
                from risk_assessments where id = ?
                """, (row, rowNumber) -> {
            assertAll(
                    () -> assertEquals(expected.id().value(), row.getObject("id", UUID.class)),
                    () -> assertEquals(expected.systemId().value(), row.getObject("system_id", UUID.class)),
                    () -> assertEquals(expected.contextOfUse().purpose(), row.getString("purpose")),
                    () -> assertEquals(expected.contextOfUse().deploymentContext(), row.getString("deployment_context")),
                    () -> assertEquals(ASSESSED_AT, row.getTimestamp("assessed_at").toInstant())
            );
            return true;
        }, expected.id().value());
    }

    private void assertFindingRows(RiskAssessment expected) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, position, description, likelihood, impact_magnitude
                from risk_assessment_findings
                where risk_assessment_id = ?
                order by position
                """, expected.id().value());
        assertEquals(expected.findings().size(), rows.size());
        for (int index = 0; index < rows.size(); index++) {
            int position = index;
            RiskFinding finding = expected.findings().get(position);
            Map<String, Object> row = rows.get(position);
            assertAll(
                    () -> assertEquals(finding.id().value(), row.get("id")),
                    () -> assertEquals(position, row.get("position")),
                    () -> assertEquals(finding.description(), row.get("description")),
                    () -> assertEquals(finding.likelihood().name(), row.get("likelihood")),
                    () -> assertEquals(finding.impactMagnitude().name(), row.get("impact_magnitude"))
            );
        }
        assertEquals(rows.get(0).get("description"), rows.get(2).get("description"));
    }

    private void assertState(RiskAssessment expected, RiskAssessment actual) {
        assertAll(
                () -> assertEquals(expected.id(), actual.id()),
                () -> assertEquals(expected.systemId(), actual.systemId()),
                () -> assertEquals(expected.contextOfUse(), actual.contextOfUse()),
                () -> assertEquals(expected.findings(), actual.findings()),
                () -> assertEquals(expected.assessedAt(), actual.assessedAt())
        );
    }

    private void assertRiskTableShape(String schema) {
        Set<String> assessmentConstraints = Set.copyOf(jdbc.queryForList("""
                select constraint_name from information_schema.table_constraints
                where table_schema = ? and table_name = 'risk_assessments'
                """, String.class, schema));
        assertTrue(assessmentConstraints.containsAll(Set.of("pk_risk_assessments", "fk_risk_assessments_system")));
        Set<String> findingConstraints = Set.copyOf(jdbc.queryForList("""
                select constraint_name from information_schema.table_constraints
                where table_schema = ? and table_name = 'risk_assessment_findings'
                """, String.class, schema));
        assertTrue(findingConstraints.containsAll(Set.of(
                "pk_risk_assessment_findings",
                "fk_risk_assessment_findings_assessment",
                "uq_risk_assessment_findings_assessment_position",
                "ck_risk_assessment_findings_position",
                "ck_risk_assessment_findings_likelihood",
                "ck_risk_assessment_findings_impact_magnitude")));
        assertEquals(Set.of("pk_risk_assessments", "pk_risk_assessment_findings",
                        "uq_risk_assessment_findings_assessment_position"),
                Set.copyOf(jdbc.queryForList("""
                        select indexname from pg_indexes
                        where schemaname = ?
                            and tablename in ('risk_assessments', 'risk_assessment_findings')
                        """, String.class, schema)));
    }

    static class FailureAfterFlush extends RuntimeException {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RiskPersistenceConfiguration.class, GovernancePersistenceConfiguration.class,
            AIInitiativePersistenceConfiguration.class})
    static class PersistenceConfiguration {
    }
}
