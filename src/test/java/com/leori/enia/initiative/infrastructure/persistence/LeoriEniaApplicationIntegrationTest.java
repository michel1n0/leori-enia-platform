package com.leori.enia.initiative.infrastructure.persistence;

import com.leori.enia.LeoriEniaApplication;
import com.leori.enia.initiative.application.ApproveAIInitiativeUseCase;
import com.leori.enia.initiative.application.AssessRiskAIInitiativeUseCase;
import com.leori.enia.initiative.application.CreateAIInitiativeCommand;
import com.leori.enia.initiative.application.CreateAIInitiativeUseCase;
import com.leori.enia.initiative.application.RejectAIInitiativeUseCase;
import com.leori.enia.initiative.application.StartAssessmentAIInitiativeUseCase;
import com.leori.enia.initiative.application.SubmitAIInitiativeUseCase;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.organization.domain.OrganizationId;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = LeoriEniaApplication.class)
class LeoriEniaApplicationIntegrationTest {

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
        assertInstanceOf(JpaAIInitiativeRepositoryAdapter.class, repository);
        assertNotNull(springDataRepository);
        assertNotNull(entityManagerFactory);
        assertNotNull(dataSource);
        assertNotNull(transactionManager);
        assertNotNull(clock);
        assertEquals("3", flyway.info().current().getVersion().toString());
        assertTrue(AopUtils.isAopProxy(create));

        AIInitiative created = create.execute(new CreateAIInitiativeCommand(
                OrganizationId.generate(), "Bootstrap test", "Production context persistence",
                false, false));

        assertEquals(InitiativeStatus.DRAFT, created.status());
        assertEquals(created.id(), repository.findById(created.id()).orElseThrow().initiative().id());
        assertEquals("DRAFT", jdbc.queryForObject(
                "select status from ai_initiatives where id = ?", String.class, created.id().value()));
        assertEquals(0L, jdbc.queryForObject(
                "select version from ai_initiatives where id = ?", Long.class, created.id().value()));
    }
}
