package com.leori.enia.initiative.infrastructure.persistence;

import com.leori.enia.initiative.application.port.LoadedAIInitiative;
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(
        classes = PostgreSQLAIInitiativePersistenceIntegrationTest
                .JpaTestConfiguration.class
)
@Import(PostgreSQLAIInitiativePersistenceIntegrationTest.AdapterConfiguration.class)
class PostgreSQLAIInitiativePersistenceIntegrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-16T14:00:00.123456Z");
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-16T15:00:00.234567Z");
    private static final Instant ASSESSED_AT =
            Instant.parse("2026-09-16T16:00:00.345678Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.6-alpine")
                    .withDatabaseName("enia_test")
                    .withUsername("enia_test")
                    .withPassword("enia_test");

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private JpaAIInitiativeRepositoryAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void should_apply_v1_with_postgresql_native_types() {
        assertEquals("1", flyway.info().applied()[0].getVersion().toString());
        assertEquals("4", flyway.info().current().getVersion().toString());

        Integer tableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'ai_initiatives'
                """,
                Integer.class
        );
        assertEquals(1, tableCount);

        Map<String, String> columnTypes = jdbcTemplate.query(
                """
                select column_name, data_type
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'ai_initiatives'
                """,
                resultSet -> {
                    Map<String, String> types = new java.util.HashMap<>();
                    while (resultSet.next()) {
                        types.put(
                                resultSet.getString("column_name"),
                                resultSet.getString("data_type")
                        );
                    }
                    return types;
                }
        );

        assertEquals("bigint", columnTypes.get("version"));
        assertEquals("text", columnTypes.get("rejection_reason"));
        assertEquals("uuid", columnTypes.get("id"));
        assertEquals("uuid", columnTypes.get("organization_id"));
        assertEquals("character varying", columnTypes.get("status"));
        assertEquals("character varying", columnTypes.get("preliminary_risk"));
        assertEquals("boolean", columnTypes.get("uses_personal_data"));
        assertEquals("boolean", columnTypes.get("impacts_rights"));
        assertEquals("timestamp with time zone", columnTypes.get("created_at"));
    }

    @Test
    void should_round_trip_a_draft_with_uuid_and_fractional_instant() {
        AIInitiative initiative = createInitiative();

        AIInitiative saved = adapter.create(initiative);
        flushAndClear();
        AIInitiative loaded = adapter.findById(initiative.id()).orElseThrow().initiative();

        assertInitiativeState(saved, initiative);
        assertInitiativeState(loaded, initiative);
        assertEquals(InitiativeStatus.DRAFT, loaded.status());
        assertEquals(RiskLevel.NOT_ASSESSED, loaded.preliminaryRisk());
        assertTrue(saved.domainEvents().isEmpty());
        assertTrue(loaded.domainEvents().isEmpty());

        UUID storedId = jdbcTemplate.queryForObject(
                "select id from ai_initiatives where id = ?",
                UUID.class,
                initiative.id().value()
        );
        assertEquals(initiative.id().value(), storedId);
        assertEquals(CREATED_AT, loaded.createdAt());
    }

    @Test
    void should_round_trip_a_risk_assessed_initiative_with_string_enums() {
        AIInitiative initiative = createRiskAssessedInitiative();

        AIInitiative saved = adapter.create(initiative);
        flushAndClear();
        AIInitiative loaded = adapter.findById(initiative.id()).orElseThrow().initiative();

        assertInitiativeState(saved, initiative);
        assertInitiativeState(loaded, initiative);
        assertEquals(InitiativeStatus.RISK_ASSESSED, loaded.status());
        assertEquals(RiskLevel.MEDIUM, loaded.preliminaryRisk());
        assertTrue(saved.domainEvents().isEmpty());
        assertTrue(loaded.domainEvents().isEmpty());

        Map<String, Object> storedEnums = jdbcTemplate.queryForMap(
                """
                select status, preliminary_risk
                from ai_initiatives
                where id = ?
                """,
                initiative.id().value()
        );
        assertEquals("RISK_ASSESSED", storedEnums.get("status"));
        assertEquals("MEDIUM", storedEnums.get("preliminary_risk"));
    }

    @Test
    void should_update_the_existing_row_through_valid_domain_behavior() {
        AIInitiative draft = adapter.create(createInitiative());
        flushAndClear();

        LoadedAIInitiative loaded = adapter.findById(draft.id()).orElseThrow();
        AIInitiative loadedDraft = loaded.initiative();
        loadedDraft.submit(SUBMITTED_AT);
        loadedDraft.startAssessment();
        loadedDraft.assessRisk(RiskLevel.HIGH, ASSESSED_AT);

        AIInitiative saved = adapter.save(loaded).initiative();
        flushAndClear();
        AIInitiative reloaded = adapter.findById(draft.id()).orElseThrow().initiative();

        assertEquals(InitiativeStatus.RISK_ASSESSED, saved.status());
        assertEquals(RiskLevel.HIGH, saved.preliminaryRisk());
        assertEquals(InitiativeStatus.RISK_ASSESSED, reloaded.status());
        assertEquals(RiskLevel.HIGH, reloaded.preliminaryRisk());
        assertTrue(saved.domainEvents().isEmpty());
        assertTrue(reloaded.domainEvents().isEmpty());

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_initiatives where id = ?",
                Integer.class,
                draft.id().value()
        );
        assertEquals(1, rowCount);
    }

    @Test
    void should_save_and_reload_rejection_reason_without_pending_events() {
        AIInitiative initial = adapter.create(createRiskAssessedInitiative());
        flushAndClear();
        LoadedAIInitiative loaded = adapter.findById(initial.id()).orElseThrow();
        assertEquals(0, loaded.version());
        loaded.initiative().reject("  Residual risk unacceptable  ", ASSESSED_AT.plusSeconds(1));
        loaded.initiative().clearDomainEvents();

        var saved = adapter.save(loaded);
        flushAndClear();
        var reloaded = adapter.findById(initial.id()).orElseThrow();

        assertEquals(1, saved.version());
        assertEquals(saved.version(), reloaded.version());
        assertEquals(InitiativeStatus.REJECTED, reloaded.initiative().status());
        assertEquals(RiskLevel.MEDIUM, reloaded.initiative().preliminaryRisk());
        assertEquals("Residual risk unacceptable", saved.initiative().rejectionReason());
        assertEquals(saved.initiative().rejectionReason(), reloaded.initiative().rejectionReason());
        assertTrue(reloaded.initiative().domainEvents().isEmpty());
    }

    @Test
    void should_reload_approved_state_without_rejection_reason() {
        AIInitiative initial = createRiskAssessedInitiative();
        initial.approve(ASSESSED_AT.plusSeconds(1));
        adapter.create(initial);
        flushAndClear();

        AIInitiative loaded = adapter.findById(initial.id()).orElseThrow().initiative();
        assertEquals(InitiativeStatus.APPROVED, loaded.status());
        assertNull(loaded.rejectionReason());
    }

    private AIInitiative createRiskAssessedInitiative() {
        AIInitiative initiative = createInitiative();
        initiative.submit(SUBMITTED_AT);
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.MEDIUM, ASSESSED_AT);
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

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
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
