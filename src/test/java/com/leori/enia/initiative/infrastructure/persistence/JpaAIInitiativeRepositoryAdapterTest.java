package com.leori.enia.initiative.infrastructure.persistence;

import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.organization.domain.OrganizationId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ContextConfiguration(
        classes = JpaAIInitiativeRepositoryAdapterTest.JpaTestConfiguration.class
)
@Import(JpaAIInitiativeRepositoryAdapterTest.AdapterConfiguration.class)
class JpaAIInitiativeRepositoryAdapterTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-15T14:00:00Z");
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-16T13:00:00Z");
    private static final Instant ASSESSED_AT =
            Instant.parse("2026-09-16T14:00:00Z");
    private static final Instant DECIDED_AT =
            Instant.parse("2026-09-16T15:00:00Z");

    @Autowired
    private JpaAIInitiativeRepositoryAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void should_apply_the_flyway_baseline_migration() {
        assertEquals("1", flyway.info().applied()[0].getVersion().toString());
        assertEquals("3", flyway.info().current().getVersion().toString());

        Set<String> columns = Set.copyOf(jdbcTemplate.queryForList(
                """
                select lower(column_name)
                from information_schema.columns
                where lower(table_name) = 'ai_initiatives'
                """,
                String.class
        ));

        assertEquals(Set.of(
                "id",
                "organization_id",
                "name",
                "description",
                "status",
                "preliminary_risk",
                "uses_personal_data",
                "impacts_rights",
                "created_at",
                "version",
                "rejection_reason"
        ), columns);
    }

    @Test
    void should_save_and_load_a_new_draft_initiative() {
        AIInitiative initiative = createInitiative();

        AIInitiative saved = adapter.create(initiative);
        entityManager.flush();
        entityManager.clear();
        AIInitiative loaded = adapter.findById(initiative.id()).orElseThrow().initiative();

        assertInitiativeState(saved, initiative);
        assertInitiativeState(loaded, initiative);
        assertTrue(saved.domainEvents().isEmpty());
        assertTrue(loaded.domainEvents().isEmpty());
    }

    @Test
    void should_save_and_load_a_risk_assessed_initiative() {
        AIInitiative initiative = createRiskAssessedInitiative();

        AIInitiative saved = adapter.create(initiative);
        entityManager.flush();
        entityManager.clear();
        AIInitiative loaded = adapter.findById(initiative.id()).orElseThrow().initiative();

        assertInitiativeState(saved, initiative);
        assertInitiativeState(loaded, initiative);
        assertTrue(saved.domainEvents().isEmpty());
        assertTrue(loaded.domainEvents().isEmpty());

        String status = jdbcTemplate.queryForObject(
                "select status from ai_initiatives where id = ?",
                String.class,
                initiative.id().value()
        );
        String preliminaryRisk = jdbcTemplate.queryForObject(
                "select preliminary_risk from ai_initiatives where id = ?",
                String.class,
                initiative.id().value()
        );

        assertEquals("RISK_ASSESSED", status);
        assertEquals("HIGH", preliminaryRisk);
    }

    @Test
    void should_save_and_load_an_approved_initiative() {
        AIInitiative initiative = createRiskAssessedInitiative();
        initiative.approve(DECIDED_AT);
        initiative.clearDomainEvents();

        AIInitiative saved = adapter.create(initiative);
        entityManager.flush();
        entityManager.clear();
        AIInitiative loaded = adapter.findById(initiative.id()).orElseThrow().initiative();

        assertEquals(InitiativeStatus.APPROVED, saved.status());
        assertEquals(InitiativeStatus.APPROVED, loaded.status());
        assertNull(loaded.rejectionReason());
        assertEquals(RiskLevel.HIGH, loaded.preliminaryRisk());
        assertTrue(saved.domainEvents().isEmpty());
        assertTrue(loaded.domainEvents().isEmpty());
    }

    @Test
    void should_save_and_load_a_rejected_initiative() {
        AIInitiative initiative = createRiskAssessedInitiative();
        initiative.reject("  Riesgo residual no aceptable  ", DECIDED_AT);
        initiative.clearDomainEvents();

        AIInitiative saved = adapter.create(initiative);
        entityManager.flush();
        entityManager.clear();
        AIInitiative loaded = adapter.findById(initiative.id()).orElseThrow().initiative();

        assertEquals(InitiativeStatus.REJECTED, saved.status());
        assertEquals(InitiativeStatus.REJECTED, loaded.status());
        assertEquals("Riesgo residual no aceptable", saved.rejectionReason());
        assertEquals("Riesgo residual no aceptable", loaded.rejectionReason());
        assertEquals(RiskLevel.HIGH, loaded.preliminaryRisk());
        assertTrue(saved.domainEvents().isEmpty());
        assertTrue(loaded.domainEvents().isEmpty());
    }

    @Test
    void should_return_empty_when_initiative_does_not_exist() {
        assertFalse(adapter.findById(AIInitiativeId.generate()).isPresent());
    }

    @Test
    void should_load_a_legacy_rejected_row_without_a_reason() {
        AIInitiative initiative = adapter.create(createRiskAssessedInitiative());
        entityManager.flush();
        entityManager.clear();
        jdbcTemplate.update("update ai_initiatives set status = 'REJECTED' where id = ?",
                initiative.id().value());

        AIInitiative loaded = adapter.findById(initiative.id()).orElseThrow().initiative();

        assertEquals(InitiativeStatus.REJECTED, loaded.status());
        assertNull(loaded.rejectionReason());
        assertTrue(loaded.domainEvents().isEmpty());
    }

    private AIInitiative createRiskAssessedInitiative() {
        AIInitiative initiative = createInitiative();
        initiative.submit(SUBMITTED_AT);
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.HIGH, ASSESSED_AT);
        initiative.clearDomainEvents();
        return initiative;
    }

    private AIInitiative createInitiative() {
        return AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("Detección de anomalías de asistencia")
                .description("Detectar patrones anómalos de asistencia laboral")
                .usesPersonalData(true)
                .impactsRights(false)
                .createdAt(CREATED_AT)
                .build();
    }

    private void assertInitiativeState(
            AIInitiative actual,
            AIInitiative expected
    ) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.organizationId(), actual.organizationId());
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.description(), actual.description());
        assertEquals(expected.status(), actual.status());
        assertEquals(expected.preliminaryRisk(), actual.preliminaryRisk());
        assertEquals(expected.rejectionReason(), actual.rejectionReason());
        assertEquals(expected.usesPersonalData(), actual.usesPersonalData());
        assertEquals(expected.impactsRights(), actual.impactsRights());
        assertEquals(expected.createdAt(), actual.createdAt());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = AIInitiativeJpaEntity.class)
    @EnableJpaRepositories(
            basePackageClasses = SpringDataAIInitiativeRepository.class
    )
    static class JpaTestConfiguration {
    }

    @TestConfiguration
    static class AdapterConfiguration {

        @Bean
        AIInitiativePersistenceMapper aiInitiativePersistenceMapper() {
            return new AIInitiativePersistenceMapper();
        }

        @Bean
        JpaAIInitiativeRepositoryAdapter jpaAIInitiativeRepositoryAdapter(
                SpringDataAIInitiativeRepository repository,
                AIInitiativePersistenceMapper mapper
        ) {
            return new JpaAIInitiativeRepositoryAdapter(repository, mapper);
        }
    }
}
