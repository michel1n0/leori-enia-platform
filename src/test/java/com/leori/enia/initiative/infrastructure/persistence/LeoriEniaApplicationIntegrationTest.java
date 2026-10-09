package com.leori.enia.initiative.infrastructure.persistence;

import com.leori.enia.LeoriEniaApplication;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.application.ApproveAIInitiativeUseCase;
import com.leori.enia.initiative.application.AssessRiskAIInitiativeUseCase;
import com.leori.enia.initiative.application.CreateAIInitiativeCommand;
import com.leori.enia.initiative.application.CreateAIInitiativeUseCase;
import com.leori.enia.initiative.application.RejectAIInitiativeUseCase;
import com.leori.enia.initiative.application.StartAssessmentAIInitiativeUseCase;
import com.leori.enia.initiative.application.SubmitAIInitiativeUseCase;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.organization.domain.OrganizationId;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(
        classes = {LeoriEniaApplication.class, LeoriEniaApplicationIntegrationTest.FixedClockConfiguration.class},
        properties = "spring.main.allow-bean-definition-overriding=true"
)
class LeoriEniaApplicationIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-16T14:00:00.123456Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CreateAIInitiativeUseCase create;

    @Autowired
    private AIInitiativeRepository repository;

    @Autowired
    private AISystemRepository systems;

    @Autowired
    private SpringDataAIInitiativeRepository springDataRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void production_context_wires_initiative_and_persists_through_a_transactional_use_case() {
        assertNotNull(context.getBean(LeoriEniaApplication.class));
        assertNotNull(context.getBean(SubmitAIInitiativeUseCase.class));
        assertNotNull(context.getBean(StartAssessmentAIInitiativeUseCase.class));
        assertNotNull(context.getBean(AssessRiskAIInitiativeUseCase.class));
        assertNotNull(context.getBean(ApproveAIInitiativeUseCase.class));
        assertNotNull(context.getBean(RejectAIInitiativeUseCase.class));
        assertNotNull(repository);
        assertNotNull(springDataRepository);
        assertNotNull(entityManagerFactory);
        assertNotNull(dataSource);
        assertNotNull(transactionManager);
        assertNotNull(clock);
        assertEquals("12", flyway.info().current().getVersion().toString());
        assertNotNull(systems);

        AIInitiative created = create.execute(new CreateAIInitiativeCommand(
                OrganizationId.generate(), "Bootstrap test", "Production context persistence",
                false, false));

        assertEquals(InitiativeStatus.DRAFT, created.status());
        assertEquals(CREATED_AT, created.createdAt());
        AIInitiative persisted = repository.findById(created.id()).orElseThrow().initiative();
        assertEquals(created.id(), persisted.id());
        assertEquals(CREATED_AT, persisted.createdAt());
        assertEquals("DRAFT", jdbc.queryForObject(
                "select status from ai_initiatives where id = ?", String.class, created.id().value()));
        assertEquals(Timestamp.from(CREATED_AT), jdbc.queryForObject(
                "select created_at from ai_initiatives where id = ?", Timestamp.class, created.id().value()));
        assertEquals(0L, jdbc.queryForObject(
                "select version from ai_initiatives where id = ?", Long.class, created.id().value()));

        AISystem system = AISystem.builder()
                .id(AISystemId.generate())
                .organizationId(created.organizationId())
                .sourceInitiativeId(created.id())
                .name("Context system")
                .description("AI system repository behavior")
                .createdAt(CREATED_AT)
                .build();
        systems.create(system);

        AISystem persistedSystem = systems.findById(system.id()).orElseThrow();
        assertEquals(system.id(), persistedSystem.id());
        assertEquals(created.organizationId(), persistedSystem.organizationId());
        assertEquals(created.id(), persistedSystem.sourceInitiativeId());
        assertEquals("Context system", persistedSystem.name());
        assertEquals("AI system repository behavior", persistedSystem.description());
        assertEquals(CREATED_AT, persistedSystem.createdAt());
    }

    @Test
    void create_use_case_joins_outer_transaction_and_rolls_back_created_initiative() {
        UUID[] createdId = new UUID[1];

        assertThrows(FailureAfterCreate.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(transaction -> {
                    AIInitiative created = create.execute(new CreateAIInitiativeCommand(
                            OrganizationId.generate(), "Rollback test", "Must not commit",
                            false, false));
                    createdId[0] = created.id().value();

                    assertEquals(InitiativeStatus.DRAFT, created.status());
                    assertTrue(repository.findById(created.id()).isPresent());
                    throw new FailureAfterCreate();
                }));

        assertNotNull(createdId[0]);
        assertFalse(repository.findById(new AIInitiativeId(createdId[0])).isPresent());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from ai_initiatives where id = ?", Integer.class, createdId[0]));
    }

    static class FailureAfterCreate extends RuntimeException {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock aiInitiativeClock() {
            return Clock.fixed(CREATED_AT, ZoneOffset.UTC);
        }
    }
}
