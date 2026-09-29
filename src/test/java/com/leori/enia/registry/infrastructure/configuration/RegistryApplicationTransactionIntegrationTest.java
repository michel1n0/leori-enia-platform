package com.leori.enia.registry.infrastructure.configuration;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.infrastructure.persistence.JpaAISystemRepositoryAdapter;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.registry.application.RegisterAIModelCommand;
import com.leori.enia.registry.application.RegisterAIModelUseCase;
import com.leori.enia.registry.application.port.AIModelRepository;
import com.leori.enia.registry.domain.AIModel;
import com.leori.enia.registry.domain.event.AIModelRegistered;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(RegistryApplicationTransactionIntegrationTest.TestConfiguration.class)
class RegistryApplicationTransactionIntegrationTest {

    private static final Instant SYSTEM_CREATED_AT = Instant.parse("2026-09-25T13:00:00.123456Z");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-29T10:00:00.123456Z");
    private static final UUID ORGANIZATION_UUID = UUID.fromString("20000000-0000-0000-0000-000000000001");

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
    private RegisterAIModelUseCase register;

    @Autowired
    private ObservedAISystemRepository systems;

    @Autowired
    private ObservedAIModelRepository models;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        systems.reset();
        models.reset();
        jdbc.update("delete from ai_models");
        jdbc.update("delete from ai_systems");
        jdbc.update("delete from ai_initiatives");
    }

    @Test
    void registers_model_for_existing_system_in_one_required_transaction() {
        AISystem system = seedSystem();

        AIModel result = register.execute(new RegisterAIModelCommand(
                system.id(), "Vision Model", "Object detection", "OpenAI"));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSingleTransaction();
        assertEquals(1, systems.finds);
        assertEquals(1, models.creates);
        assertEquals(1, rowCount());
        assertEquals(system.id(), result.systemId());
        assertEquals(REGISTERED_AT, result.createdAt());
        assertEquals(1, result.domainEvents().size());
        assertInstanceOf(AIModelRegistered.class, result.domainEvents().getFirst());
        assertModelRow(result);
    }

    @Test
    void rolls_back_insert_flushed_inside_registration_transaction() {
        AISystem system = seedSystem();
        models.failAfterFlush = true;

        assertThrows(FailureAfterFlush.class,
                () -> register.execute(new RegisterAIModelCommand(
                        system.id(), "Model", "Desc", "Provider")));

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertSingleTransaction();
        assertEquals(0, rowCount());
        assertNotNull(models.flushedSystemId);
        assertEquals(system.id(), models.flushedSystemId);
    }

    @Test
    void missing_system_fails_before_model_persistence() {
        AISystemId missingId = AISystemId.generate();

        AISystemNotFoundException exception = assertThrows(
                AISystemNotFoundException.class,
                () -> register.execute(new RegisterAIModelCommand(missingId, "Model", "Desc", "Provider"))
        );

        assertEquals("AI system not found: " + missingId, exception.getMessage());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, systems.finds);
        assertEquals(0, models.creates);
        assertEquals(0, rowCount());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void assertSingleTransaction() {
        assertNotNull(systems.findTransaction);
        assertNotNull(models.createTransaction);
        assertEquals(systems.findTransaction, models.createTransaction,
                "System findById and model create must run in the same PostgreSQL transaction");
    }

    private AISystem seedSystem() {
        UUID initiativeId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_initiatives
                    (id, organization_id, name, description, status, preliminary_risk,
                     uses_personal_data, impacts_rights, created_at)
                values (?, ?, 'Source', 'Description', 'APPROVED', 'HIGH', false, false, ?)
                """, initiativeId, ORGANIZATION_UUID, Timestamp.from(SYSTEM_CREATED_AT));
        AISystemId systemId = AISystemId.generate();
        jdbc.update("""
                insert into ai_systems
                    (id, organization_id, source_initiative_id, name, description, status, created_at)
                values (?, ?, ?, 'AI System', 'System description', 'REGISTERED', ?)
                """, systemId.value(), ORGANIZATION_UUID, initiativeId, Timestamp.from(SYSTEM_CREATED_AT));
        return systems.delegate.findById(systemId)
                .orElseThrow(() -> new AssertionError("Seeded system not found: " + systemId));
    }

    private void assertModelRow(AIModel model) {
        assertEquals(model.systemId().value(), jdbc.queryForObject(
                "select system_id from ai_models where id = ?", UUID.class, model.id().value()));
        assertEquals(model.name(), jdbc.queryForObject(
                "select name from ai_models where id = ?", String.class, model.id().value()));
        assertEquals(model.description(), jdbc.queryForObject(
                "select description from ai_models where id = ?", String.class, model.id().value()));
        assertEquals(model.provider(), jdbc.queryForObject(
                "select provider from ai_models where id = ?", String.class, model.id().value()));
    }

    private int rowCount() {
        return jdbc.queryForObject("select count(*) from ai_models", Integer.class);
    }

    // ---------------------------------------------------------------------------
    // Spring configuration
    // ---------------------------------------------------------------------------

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RegistryApplicationConfiguration.class, AIInitiativePersistenceConfiguration.class})
    static class TestConfiguration {

        @Bean
        Clock registryClock() {
            return Clock.fixed(REGISTERED_AT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        ObservedAISystemRepository observedAISystemRepository(
                @Qualifier("aiSystemRepository") AISystemRepository delegate,
                JdbcTemplate jdbc
        ) {
            return new ObservedAISystemRepository(delegate, jdbc);
        }

        @Bean
        @Primary
        ObservedAIModelRepository observedAIModelRepository(
                @Qualifier("aiModelRepository") AIModelRepository delegate,
                EntityManager entityManager,
                JdbcTemplate jdbc
        ) {
            return new ObservedAIModelRepository(delegate, entityManager, jdbc);
        }
    }

    // ---------------------------------------------------------------------------
    // Observed repositories
    // ---------------------------------------------------------------------------

    static class ObservedAISystemRepository implements AISystemRepository {
        private final AISystemRepository delegate;
        private final JdbcTemplate jdbc;
        private Long findTransaction;
        private int finds;

        ObservedAISystemRepository(AISystemRepository delegate, JdbcTemplate jdbc) {
            this.delegate = delegate;
            this.jdbc = jdbc;
        }

        @Override
        public AISystem create(AISystem system) {
            return delegate.create(system);
        }

        @Override
        public Optional<AISystem> findById(AISystemId id) {
            findTransaction = currentTransaction();
            finds++;
            return delegate.findById(id);
        }

        private Long currentTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before finding the system");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        void reset() {
            findTransaction = null;
            finds = 0;
        }
    }

    static class ObservedAIModelRepository implements AIModelRepository {
        private final AIModelRepository delegate;
        private final EntityManager entityManager;
        private final JdbcTemplate jdbc;
        private boolean failAfterFlush;
        private Long createTransaction;
        private AISystemId flushedSystemId;
        private int creates;

        ObservedAIModelRepository(AIModelRepository delegate, EntityManager entityManager, JdbcTemplate jdbc) {
            this.delegate = delegate;
            this.entityManager = entityManager;
            this.jdbc = jdbc;
        }

        @Override
        public AIModel create(AIModel model) {
            createTransaction = currentTransaction();
            creates++;
            return observeWrite(model, () -> delegate.create(model));
        }

        @Override
        public Optional<AIModel> findById(com.leori.enia.registry.domain.AIModelId id) {
            return delegate.findById(id);
        }

        private AIModel observeWrite(AIModel model, Supplier<AIModel> write) {
            AIModel result = write.get();
            // The adapter already flushed, but we force a second flush to confirm the row is visible
            // within the transaction before deciding whether to fail.
            entityManager.flush();
            flushedSystemId = model.systemId();
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from ai_models where id = ?", Integer.class, model.id().value()));
            if (failAfterFlush) {
                throw new FailureAfterFlush();
            }
            return result;
        }

        private Long currentTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Transaction must start before creating the model");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return jdbc.queryForObject("select txid_current()", Long.class);
        }

        void reset() {
            failAfterFlush = false;
            createTransaction = null;
            flushedSystemId = null;
            creates = 0;
        }
    }

    static class FailureAfterFlush extends RuntimeException {
    }
}
