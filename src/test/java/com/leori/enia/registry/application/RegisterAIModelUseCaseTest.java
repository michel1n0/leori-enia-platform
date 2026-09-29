package com.leori.enia.registry.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.domain.AISystemStatus;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import com.leori.enia.registry.application.port.AIModelRepository;
import com.leori.enia.registry.domain.AIModel;
import com.leori.enia.registry.domain.AIModelId;
import com.leori.enia.registry.domain.event.AIModelRegistered;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RegisterAIModelUseCaseTest {

    private static final Instant SYSTEM_CREATED_AT = Instant.parse("2026-09-25T13:00:00Z");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-29T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(REGISTERED_AT, ZoneOffset.UTC);

    // ---------------------------------------------------------------------------
    // Happy path
    // ---------------------------------------------------------------------------

    @Test
    void registers_model_for_existing_system() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        CountingClock clock = new CountingClock(REGISTERED_AT);
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, clock);

        AIModel result = useCase.execute(new RegisterAIModelCommand(
                system.id(), "  Vision Model  ", "  Object detection  ", "  OpenAI  "));

        assertSame(result, modelRepository.createdModel);
        assertNotNull(result.id());
        assertEquals(system.id(), result.systemId());
        assertEquals("Vision Model", result.name());
        assertEquals("Object detection", result.description());
        assertEquals("OpenAI", result.provider());
        assertEquals(REGISTERED_AT, result.createdAt());
        assertEquals(1, systemRepository.findCount);
        assertEquals(1, modelRepository.createCount);
        assertEquals(1, clock.instantCalls);
    }

    @Test
    void generated_model_id_is_unique_per_call() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, CLOCK);

        AIModel first = useCase.execute(new RegisterAIModelCommand(system.id(), "Model A", "Desc", "Provider"));
        modelRepository.reset();
        AIModel second = useCase.execute(new RegisterAIModelCommand(system.id(), "Model B", "Desc", "Provider"));

        assertNotNull(first.id());
        assertNotNull(second.id());
        // Records equality compares the wrapped UUID value
        assertEquals(false, first.id().equals(second.id()));
    }

    @Test
    void timestamp_comes_from_injected_clock() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        Instant expected = Instant.parse("2026-09-29T12:34:56.789Z");
        CountingClock clock = new CountingClock(expected);
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, clock);

        AIModel result = useCase.execute(new RegisterAIModelCommand(system.id(), "Model", "Desc", "Provider"));

        assertEquals(expected, result.createdAt());
        assertEquals(1, clock.instantCalls);
    }

    @Test
    void pending_ai_model_registered_event_is_preserved() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, CLOCK);

        AIModel result = useCase.execute(new RegisterAIModelCommand(system.id(), "Model", "Desc", "Provider"));

        assertEquals(1, result.domainEvents().size());
        AIModelRegistered event = assertInstanceOf(AIModelRegistered.class, result.domainEvents().getFirst());
        assertEquals(result.id(), event.modelId());
        assertEquals(system.id(), event.systemId());
        assertEquals(REGISTERED_AT, event.occurredAt());
    }

    // ---------------------------------------------------------------------------
    // Missing system
    // ---------------------------------------------------------------------------

    @Test
    void missing_system_throws_without_model_persistence() {
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        Clock clock = mock(Clock.class);
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, clock);
        AISystemId missingId = AISystemId.generate();

        AISystemNotFoundException exception = assertThrows(
                AISystemNotFoundException.class,
                () -> useCase.execute(new RegisterAIModelCommand(missingId, "Model", "Desc", "Provider"))
        );

        assertEquals("AI system not found: " + missingId, exception.getMessage());
        assertEquals(1, systemRepository.findCount);
        assertEquals(0, modelRepository.createCount);
        verifyNoInteractions(clock);
    }

    @Test
    void missing_system_does_not_persist_a_model() {
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, CLOCK);

        assertThrows(AISystemNotFoundException.class,
                () -> useCase.execute(new RegisterAIModelCommand(
                        AISystemId.generate(), "Model", "Desc", "Provider")));

        assertEquals(0, modelRepository.createCount);
    }

    // ---------------------------------------------------------------------------
    // Null command
    // ---------------------------------------------------------------------------

    @Test
    void null_command_fails_before_repository_access() {
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        Clock clock = mock(Clock.class);
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, clock);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Register AI model command is required", exception.getMessage());
        assertEquals(0, systemRepository.findCount);
        assertEquals(0, modelRepository.createCount);
        verifyNoInteractions(clock);
    }

    // ---------------------------------------------------------------------------
    // Invalid domain text
    // ---------------------------------------------------------------------------

    @Test
    void blank_name_is_rejected_by_ai_model_builder() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        CountingClock clock = new CountingClock(REGISTERED_AT);
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, clock);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(new RegisterAIModelCommand(system.id(), "   ", "Desc", "Provider")));

        assertEquals("Name is required", exception.getMessage());
        assertEquals(1, clock.instantCalls);
        assertEquals(0, modelRepository.createCount);
    }

    @Test
    void null_name_is_rejected_by_ai_model_builder() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        CountingClock clock = new CountingClock(REGISTERED_AT);
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, clock);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(new RegisterAIModelCommand(system.id(), null, "Desc", "Provider")));

        assertEquals("Name is required", exception.getMessage());
        assertEquals(1, clock.instantCalls);
        assertEquals(0, modelRepository.createCount);
    }

    @Test
    void blank_provider_is_rejected_by_ai_model_builder() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        CountingClock clock = new CountingClock(REGISTERED_AT);
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, clock);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(new RegisterAIModelCommand(system.id(), "Model", "Desc", "   ")));

        assertEquals("Provider is required", exception.getMessage());
        assertEquals(1, clock.instantCalls);
        assertEquals(0, modelRepository.createCount);
    }

    // ---------------------------------------------------------------------------
    // Persistence failure
    // ---------------------------------------------------------------------------

    @Test
    void persistence_failure_propagates_unchanged() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryAIModelRepository modelRepository = new InMemoryAIModelRepository();
        RuntimeException failure = new RuntimeException("Storage unavailable");
        modelRepository.failure = failure;
        RegisterAIModelUseCase useCase = new RegisterAIModelUseCase(systemRepository, modelRepository, CLOCK);

        assertSame(failure, assertThrows(RuntimeException.class,
                () -> useCase.execute(new RegisterAIModelCommand(system.id(), "Model", "Desc", "Provider"))));

        assertEquals(1, modelRepository.createCount);
    }

    // ---------------------------------------------------------------------------
    // Command invariant
    // ---------------------------------------------------------------------------

    @Test
    void command_requires_system_id() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new RegisterAIModelCommand(null, "Model", "Desc", "Provider"));

        assertEquals("System id is required", exception.getMessage());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private AISystem registeredSystem() {
        return AISystem.rehydrate(
                AISystemId.generate(),
                OrganizationId.generate(),
                AIInitiativeId.generate(),
                "AI System",
                "System description",
                AISystemStatus.REGISTERED,
                SYSTEM_CREATED_AT
        );
    }

    private static final class InMemoryAISystemRepository implements AISystemRepository {
        private final java.util.Map<AISystemId, AISystem> systems = new java.util.HashMap<>();
        private int findCount;

        InMemoryAISystemRepository(AISystem... systems) {
            for (AISystem system : systems) {
                this.systems.put(system.id(), system);
            }
        }

        @Override
        public AISystem create(AISystem system) {
            throw new AssertionError("Registration must not create an AI system");
        }

        @Override
        public Optional<AISystem> findById(AISystemId id) {
            findCount++;
            return Optional.ofNullable(systems.get(id));
        }
    }

    private static final class InMemoryAIModelRepository implements AIModelRepository {
        private AIModel createdModel;
        private RuntimeException failure;
        private int createCount;

        @Override
        public AIModel create(AIModel model) {
            createCount++;
            createdModel = model;
            if (failure != null) {
                throw failure;
            }
            return model;
        }

        void reset() {
            createdModel = null;
            failure = null;
            createCount = 0;
        }
    }

    private static final class CountingClock extends Clock {
        private final Instant fixedInstant;
        private int instantCalls;

        CountingClock(Instant fixedInstant) {
            this.fixedInstant = fixedInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            instantCalls++;
            return fixedInstant;
        }
    }
}
