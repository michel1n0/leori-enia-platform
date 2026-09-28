package com.leori.enia.governance.application;

import com.leori.enia.governance.application.exception.AISystemAlreadyRegisteredException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.event.AISystemRegistered;
import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.application.port.SavedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.initiative.domain.exception.AIInitiativeNotApprovedForSystemRegistrationException;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RegisterAISystemUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-25T13:00:00Z");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-25T14:00:00Z");
    private static final Clock CLOCK = Clock.fixed(REGISTERED_AT, ZoneOffset.UTC);

    @Test
    void registers_system_from_approved_initiative() {
        AIInitiative initiative = approvedInitiative();
        var eventsBefore = initiative.domainEvents();
        InMemoryAIInitiativeRepository initiativeRepository = new InMemoryAIInitiativeRepository(initiative);
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        CountingClock clock = new CountingClock(REGISTERED_AT);
        RegisterAISystemUseCase useCase = new RegisterAISystemUseCase(
                initiativeRepository, systemRepository, clock);

        AISystem result = useCase.execute(new RegisterAISystemCommand(
                initiative.id(), "  Registered system  ", "  System description  "));

        assertSame(result, systemRepository.createdSystem);
        assertNotNull(result.id());
        assertEquals(initiative.organizationId(), result.organizationId());
        assertEquals(initiative.id(), result.sourceInitiativeId());
        assertEquals("Registered system", result.name());
        assertEquals("System description", result.description());
        assertEquals(REGISTERED_AT, result.createdAt());
        assertEquals(1, result.domainEvents().size());
        AISystemRegistered event = assertInstanceOf(AISystemRegistered.class, result.domainEvents().getFirst());
        assertEquals(result.id(), event.systemId());
        assertEquals(result.organizationId(), event.organizationId());
        assertEquals(result.sourceInitiativeId(), event.sourceInitiativeId());
        assertEquals(REGISTERED_AT, event.occurredAt());
        assertEquals(1, initiativeRepository.loadCount);
        assertEquals(0, initiativeRepository.saveCount);
        assertEquals(1, systemRepository.createCount);
        assertEquals(1, clock.instantCalls);
        assertEquals(InitiativeStatus.APPROVED, initiative.status());
        assertEquals(eventsBefore, initiative.domainEvents());
    }

    @Test
    void missing_initiative_fails_without_clock_or_system_persistence() {
        InMemoryAIInitiativeRepository initiativeRepository = new InMemoryAIInitiativeRepository();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        Clock clock = mock(Clock.class);
        RegisterAISystemUseCase useCase = new RegisterAISystemUseCase(
                initiativeRepository, systemRepository, clock);
        AIInitiativeId missingId = AIInitiativeId.generate();

        AIInitiativeNotFoundException exception = assertThrows(
                AIInitiativeNotFoundException.class,
                () -> useCase.execute(new RegisterAISystemCommand(missingId, "System", "Description"))
        );

        assertEquals("AI initiative not found: " + missingId, exception.getMessage());
        assertEquals(1, initiativeRepository.loadCount);
        assertEquals(0, initiativeRepository.saveCount);
        assertEquals(0, systemRepository.createCount);
        verifyNoInteractions(clock);
    }

    @Test
    void not_approved_initiative_fails_without_clock_or_system_persistence() {
        AIInitiative initiative = rehydrate(InitiativeStatus.REJECTED, RiskLevel.HIGH);
        var eventsBefore = initiative.domainEvents();
        InMemoryAIInitiativeRepository initiativeRepository = new InMemoryAIInitiativeRepository(initiative);
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        Clock clock = mock(Clock.class);
        RegisterAISystemUseCase useCase = new RegisterAISystemUseCase(
                initiativeRepository, systemRepository, clock);

        AIInitiativeNotApprovedForSystemRegistrationException exception = assertThrows(
                AIInitiativeNotApprovedForSystemRegistrationException.class,
                () -> useCase.execute(new RegisterAISystemCommand(initiative.id(), "System", "Description"))
        );

        assertEquals(initiative.id(), exception.initiativeId());
        assertEquals(InitiativeStatus.REJECTED, exception.actualStatus());
        assertEquals(InitiativeStatus.REJECTED, initiative.status());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(1, initiativeRepository.loadCount);
        assertEquals(0, initiativeRepository.saveCount);
        assertEquals(0, systemRepository.createCount);
        verifyNoInteractions(clock);
    }

    @Test
    void duplicate_registration_propagates_without_saving_source() {
        AIInitiative initiative = approvedInitiative();
        var eventsBefore = initiative.domainEvents();
        InMemoryAIInitiativeRepository initiativeRepository = new InMemoryAIInitiativeRepository(initiative);
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        AISystemAlreadyRegisteredException duplicate =
                new AISystemAlreadyRegisteredException(initiative.id(), new RuntimeException("duplicate"));
        systemRepository.failure = duplicate;
        RegisterAISystemUseCase useCase = new RegisterAISystemUseCase(
                initiativeRepository, systemRepository, CLOCK);

        assertSame(duplicate, assertThrows(AISystemAlreadyRegisteredException.class,
                () -> useCase.execute(new RegisterAISystemCommand(initiative.id(), "System", "Description"))));

        assertEquals(1, initiativeRepository.loadCount);
        assertEquals(0, initiativeRepository.saveCount);
        assertEquals(1, systemRepository.createCount);
        assertEquals(InitiativeStatus.APPROVED, initiative.status());
        assertEquals(eventsBefore, initiative.domainEvents());
    }

    @Test
    void invalid_command_fails_before_repository_access() {
        InMemoryAIInitiativeRepository initiativeRepository = new InMemoryAIInitiativeRepository();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        Clock clock = mock(Clock.class);
        RegisterAISystemUseCase useCase = new RegisterAISystemUseCase(
                initiativeRepository, systemRepository, clock);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Register AI system command is required", exception.getMessage());
        assertEquals(0, initiativeRepository.loadCount);
        assertEquals(0, systemRepository.createCount);
        verifyNoInteractions(clock);
    }

    @Test
    void command_requires_source_initiative_id() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new RegisterAISystemCommand(null, "System", "Description"));

        assertEquals("Source initiative id is required", exception.getMessage());
    }

    @Test
    void text_validation_remains_in_system_builder_after_source_is_valid() {
        AIInitiative initiative = approvedInitiative();
        InMemoryAIInitiativeRepository initiativeRepository = new InMemoryAIInitiativeRepository(initiative);
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        CountingClock clock = new CountingClock(REGISTERED_AT);
        RegisterAISystemUseCase useCase = new RegisterAISystemUseCase(
                initiativeRepository, systemRepository, clock);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(new RegisterAISystemCommand(initiative.id(), "   ", "Description")));

        assertEquals("Name is required", exception.getMessage());
        assertEquals(1, clock.instantCalls);
        assertEquals(0, systemRepository.createCount);
    }

    private AIInitiative approvedInitiative() {
        return rehydrate(InitiativeStatus.APPROVED, RiskLevel.HIGH);
    }

    private AIInitiative rehydrate(InitiativeStatus status, RiskLevel riskLevel) {
        return AIInitiative.rehydrate(
                AIInitiativeId.generate(),
                OrganizationId.generate(),
                "Initiative",
                "Description",
                status,
                riskLevel,
                true,
                false,
                CREATED_AT,
                null
        );
    }

    private static final class InMemoryAIInitiativeRepository implements AIInitiativeRepository {
        private final Map<AIInitiativeId, AIInitiative> initiatives = new HashMap<>();
        private int loadCount;
        private int saveCount;

        private InMemoryAIInitiativeRepository(AIInitiative... initiatives) {
            for (AIInitiative initiative : initiatives) {
                this.initiatives.put(initiative.id(), initiative);
            }
        }

        @Override
        public AIInitiative create(AIInitiative initiative) {
            throw new AssertionError("Registration must not create an initiative");
        }

        @Override
        public SavedAIInitiative save(LoadedAIInitiative loaded) {
            saveCount++;
            throw new AssertionError("Registration must not save the source initiative");
        }

        @Override
        public Optional<LoadedAIInitiative> findById(AIInitiativeId id) {
            loadCount++;
            return Optional.ofNullable(initiatives.get(id)).map(initiative -> new LoadedAIInitiative(initiative, 7));
        }
    }

    private static final class InMemoryAISystemRepository implements AISystemRepository {
        private AISystem createdSystem;
        private RuntimeException failure;
        private int createCount;

        @Override
        public Optional<AISystem> findById(com.leori.enia.governance.domain.AISystemId id) {
            throw new AssertionError("Registration must not read an AI system");
        }

        @Override
        public AISystem create(AISystem system) {
            createCount++;
            createdSystem = system;
            if (failure != null) {
                throw failure;
            }
            return system;
        }
    }

    private static final class CountingClock extends Clock {
        private final Instant fixedInstant;
        private int instantCalls;

        private CountingClock(Instant fixedInstant) {
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
