package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.AISystemGovernanceGaps;
import com.leori.enia.governance.application.port.AISystemGovernanceGapsRepository;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
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

@Testcontainers
@SpringJUnitConfig(PostgreSQLAISystemGovernanceGapsProjectionIntegrationTest.PersistenceConfiguration.class)
class PostgreSQLAISystemGovernanceGapsProjectionIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T10:00:00.123456Z");
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
    private AISystemGovernanceGapsRepository repository;

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
    void no_risk_assessment_returns_empty_gap_lists() {
        AISystemId systemId = seedSystem();

        AISystemGovernanceGaps gaps = repository.findByAISystemId(systemId);

        assertAll(
                () -> assertEquals(systemId, gaps.aiSystemId()),
                () -> assertEquals(List.of(), gaps.findingsWithoutControls()),
                () -> assertEquals(List.of(), gaps.controlsWithoutImplementation()),
                () -> assertEquals(List.of(), gaps.implementationsWithoutEvidence())
        );
    }

    @Test
    void critical_fixture_returns_exact_unresolved_gap_items_in_deterministic_order() {
        AISystemId systemId = seedSystem();
        UUID assessment = seedAssessment(systemId, CREATED_AT.plusSeconds(1));
        UUID findingA = seedFinding(assessment, 0, "Finding A has no controls", "HIGH", "HIGH");
        UUID findingB = seedFinding(assessment, 1, "Finding B has an unimplemented control", "MEDIUM", "HIGH");
        UUID findingC = seedFinding(assessment, 2, "Finding C has implementation without evidence", "MEDIUM", "MEDIUM");
        UUID findingD = seedFinding(assessment, 3, "Finding D is fully evidenced", "LOW", "MEDIUM");
        UUID findingE = seedFinding(assessment, 4, "Finding E has one evidenced sibling", "LOW", "HIGH");
        UUID controlB1 = seedControl(assessment, findingB, "Control B1", "Control B1 has no implementation",
                CREATED_AT.plusSeconds(2));
        UUID controlC1 = seedControl(assessment, findingC, "Control C1", "Control C1 is implemented",
                CREATED_AT.plusSeconds(3));
        UUID implementationC11 = seedImplementation(controlC1, "Implementation C1.1 has no evidence",
                CREATED_AT.plusSeconds(4));
        UUID controlD1 = seedControl(assessment, findingD, "Control D1", "Control D1 is complete",
                CREATED_AT.plusSeconds(5));
        UUID implementationD11 = seedImplementation(controlD1, "Implementation D1.1 has evidence",
                CREATED_AT.plusSeconds(6));
        seedEvidence(implementationD11);
        UUID controlE1 = seedControl(assessment, findingE, "Control E1", "Control E1 has sibling implementations",
                CREATED_AT.plusSeconds(7));
        UUID implementationE11 = seedImplementation(controlE1, "Implementation E1.1 has evidence",
                CREATED_AT.plusSeconds(8));
        seedEvidence(implementationE11);
        UUID implementationE12 = seedImplementation(controlE1, "Implementation E1.2 has no evidence",
                CREATED_AT.plusSeconds(9));

        AISystemGovernanceGaps gaps = repository.findByAISystemId(systemId);

        assertAll(
                () -> assertEquals(systemId, gaps.aiSystemId()),
                () -> assertEquals(List.of(findingA), gaps.findingsWithoutControls().stream()
                        .map(item -> item.riskFindingId().value())
                        .toList()),
                () -> assertEquals("Finding A has no controls", gaps.findingsWithoutControls().getFirst().description()),
                () -> assertEquals(Likelihood.HIGH, gaps.findingsWithoutControls().getFirst().likelihood()),
                () -> assertEquals(ImpactMagnitude.HIGH, gaps.findingsWithoutControls().getFirst().impactMagnitude()),
                () -> assertEquals(List.of(controlB1), gaps.controlsWithoutImplementation().stream()
                        .map(item -> item.controlId().value())
                        .toList()),
                () -> assertEquals(findingB, gaps.controlsWithoutImplementation().getFirst().riskFindingId().value()),
                () -> assertEquals("Control B1", gaps.controlsWithoutImplementation().getFirst().name()),
                () -> assertEquals("Control B1 has no implementation",
                        gaps.controlsWithoutImplementation().getFirst().description()),
                () -> assertEquals(List.of(implementationC11, implementationE12),
                        gaps.implementationsWithoutEvidence().stream()
                                .map(item -> item.controlImplementationId().value())
                                .toList()),
                () -> assertEquals(List.of(controlC1, controlE1), gaps.implementationsWithoutEvidence().stream()
                        .map(item -> item.controlId().value())
                        .toList()),
                () -> assertEquals(List.of("Implementation C1.1 has no evidence", "Implementation E1.2 has no evidence"),
                        gaps.implementationsWithoutEvidence().stream()
                                .map(AISystemGovernanceGaps.ImplementationWithoutEvidence::description)
                                .toList()),
                () -> assertEquals(List.of(CREATED_AT.plusSeconds(4), CREATED_AT.plusSeconds(9)),
                        gaps.implementationsWithoutEvidence().stream()
                                .map(AISystemGovernanceGaps.ImplementationWithoutEvidence::implementedAt)
                                .toList()),
                () -> assertFalse(gaps.findingsWithoutControls().stream()
                        .anyMatch(item -> item.riskFindingId().value().equals(findingD))),
                () -> assertFalse(gaps.implementationsWithoutEvidence().stream()
                        .anyMatch(item -> item.controlImplementationId().value().equals(implementationD11))),
                () -> assertFalse(gaps.implementationsWithoutEvidence().stream()
                        .anyMatch(item -> item.controlImplementationId().value().equals(implementationE11)))
        );
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

    private UUID seedAssessment(AISystemId systemId, Instant assessedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into risk_assessments (id, system_id, purpose, deployment_context, assessed_at)
                values (?, ?, 'Governance approval', 'Public sector deployment', ?)
                """, id, systemId.value(), Timestamp.from(assessedAt));
        return id;
    }

    private UUID seedFinding(UUID assessmentId, int position, String description, String likelihood, String impact) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into risk_assessment_findings
                    (id, risk_assessment_id, position, description, likelihood, impact_magnitude)
                values (?, ?, ?, ?, ?, ?)
                """, id, assessmentId, position, description, likelihood, impact);
        return id;
    }

    private UUID seedControl(UUID assessmentId, UUID findingId, String name, String description, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into controls (id, risk_assessment_id, risk_finding_id, name, description, created_at)
                values (?, ?, ?, ?, ?, ?)
                """, id, assessmentId, findingId, name, description, Timestamp.from(createdAt));
        return id;
    }

    private UUID seedImplementation(UUID controlId, String description, Instant implementedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into control_implementations (id, control_id, description, implemented_at)
                values (?, ?, ?, ?)
                """, id, controlId, description, Timestamp.from(implementedAt));
        return id;
    }

    private void seedEvidence(UUID implementationId) {
        jdbc.update("""
                insert into evidence (id, control_implementation_id, description, reference, recorded_at)
                values (?, ?, 'Signed approval minutes', 'evidence-vault:item', ?)
                """, UUID.randomUUID(), implementationId, Timestamp.from(CREATED_AT));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(GovernancePersistenceConfiguration.class)
    static class PersistenceConfiguration {
    }
}
