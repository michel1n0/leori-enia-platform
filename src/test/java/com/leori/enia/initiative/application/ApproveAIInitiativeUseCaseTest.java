package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.exception.AIInitiativeInvalidTransitionException;
import com.leori.enia.initiative.application.exception.AIInitiativeRevisionMismatchException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.application.port.SavedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.initiative.domain.event.AIInitiativeApproved;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApproveAIInitiativeUseCaseTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-15T14:00:00Z");
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-16T13:00:00Z");
    private static final Instant ASSESSED_AT =
            Instant.parse("2026-09-16T14:00:00Z");
    private static final Instant APPROVED_AT =
            Instant.parse("2026-09-16T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(APPROVED_AT, ZoneOffset.UTC);

    @Test
    void should_approve_and_save_a_risk_assessed_initiative() {
        AIInitiative initiative = createRiskAssessedInitiative();
        var eventsBefore = initiative.domainEvents();
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(initiative);
        ApproveAIInitiativeUseCase useCase =
                new ApproveAIInitiativeUseCase(repository, CLOCK);

        VersionedAIInitiativeDetails result = useCase.execute(
                new ApproveAIInitiativeCommand(initiative.id(), new ExpectedRevision(initiative.id(), 7))
        );

        assertEquals(InitiativeStatus.APPROVED, initiative.status());
        assertEquals(RiskLevel.HIGH, initiative.preliminaryRisk());
        assertEquals(1, repository.saveCount());
        assertSame(initiative, repository.savedInitiative());
        assertEquals(AIInitiativeDetails.from(repository.savedInitiative()), result.details());
        assertEquals(42, result.revision());
        assertEquals(1, repository.loadCount);
        assertEquals(eventsBefore.size() + 1, initiative.domainEvents().size());
        assertEquals(eventsBefore, initiative.domainEvents().subList(0, eventsBefore.size()));

        AIInitiativeApproved event = assertInstanceOf(
                AIInitiativeApproved.class,
                initiative.domainEvents().getLast()
        );
        assertEquals(initiative.id(), event.initiativeId());
        assertEquals(APPROVED_AT, event.occurredAt());
    }

    @Test
    void should_fail_when_initiative_does_not_exist() {
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository();
        ApproveAIInitiativeUseCase useCase =
                new ApproveAIInitiativeUseCase(repository, CLOCK);
        AIInitiativeId missingId = AIInitiativeId.generate();

        AIInitiativeNotFoundException exception = assertThrows(
                AIInitiativeNotFoundException.class,
                () -> useCase.execute(new ApproveAIInitiativeCommand(missingId, new ExpectedRevision(missingId, 7)))
        );

        assertEquals("AI initiative not found: " + missingId, exception.getMessage());
        assertEquals(1, repository.loadCount);
        assertEquals(0, repository.saveCount());
    }

    @Test
    void should_reject_null_command() {
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository();
        ApproveAIInitiativeUseCase useCase =
                new ApproveAIInitiativeUseCase(repository, CLOCK);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> useCase.execute(null)
        );

        assertEquals(
                "Approve AI initiative command is required",
                exception.getMessage()
        );
        assertEquals(0, repository.loadCount);
        assertEquals(0, repository.saveCount());
    }

    @Test
    void should_delegate_invalid_transition_enforcement_to_domain() {
        AIInitiative submittedInitiative = createInitiative();
        submittedInitiative.submit(SUBMITTED_AT);
        var eventsBefore = submittedInitiative.domainEvents();
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(submittedInitiative);
        ApproveAIInitiativeUseCase useCase =
                new ApproveAIInitiativeUseCase(repository, CLOCK);

        AIInitiativeInvalidTransitionException exception = assertThrows(
                AIInitiativeInvalidTransitionException.class,
                () -> useCase.execute(
                        new ApproveAIInitiativeCommand(submittedInitiative.id(), new ExpectedRevision(submittedInitiative.id(), 7))
                )
        );

        assertEquals(
                "Expected initiative status RISK_ASSESSED but was SUBMITTED",
                exception.getMessage()
        );
        assertEquals(IllegalStateException.class, exception.getCause().getClass());
        assertEquals(exception.getMessage(), exception.getCause().getMessage());
        assertEquals(InitiativeStatus.SUBMITTED, submittedInitiative.status());
        assertEquals(RiskLevel.NOT_ASSESSED, submittedInitiative.preliminaryRisk());
        assertEquals(eventsBefore, submittedInitiative.domainEvents());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void stale_revision_does_not_access_clock_mutate_save_or_add_events() {
        AIInitiative initiative = createRiskAssessedInitiative();
        var eventsBefore = initiative.domainEvents();
        var repository = new InMemoryAIInitiativeRepository(initiative);
        Clock clock = mock(Clock.class);
        var useCase = new ApproveAIInitiativeUseCase(repository, clock);

        assertThrows(AIInitiativeRevisionMismatchException.class,
                () -> useCase.execute(new ApproveAIInitiativeCommand(
                        initiative.id(), new ExpectedRevision(initiative.id(), 6))));

        assertEquals(InitiativeStatus.RISK_ASSESSED, initiative.status());
        assertEquals(RiskLevel.HIGH, initiative.preliminaryRisk());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(0, repository.saveCount());
        verifyNoInteractions(clock);
    }

    @Test
    void foreign_revision_does_not_access_clock_mutate_save_or_add_events() {
        AIInitiative initiative = createRiskAssessedInitiative();
        var eventsBefore = initiative.domainEvents();
        var repository = new InMemoryAIInitiativeRepository(initiative);
        Clock clock = mock(Clock.class);
        var useCase = new ApproveAIInitiativeUseCase(repository, clock);

        assertThrows(AIInitiativeRevisionMismatchException.class,
                () -> useCase.execute(new ApproveAIInitiativeCommand(
                        initiative.id(), new ExpectedRevision(AIInitiativeId.generate(), 7))));

        assertEquals(InitiativeStatus.RISK_ASSESSED, initiative.status());
        assertEquals(RiskLevel.HIGH, initiative.preliminaryRisk());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(0, repository.saveCount());
        verifyNoInteractions(clock);
    }

    @Test
    void null_expected_revision_is_rejected_before_lookup() {
        var repository = new InMemoryAIInitiativeRepository();
        var useCase = new ApproveAIInitiativeUseCase(repository, CLOCK);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> useCase.execute(new ApproveAIInitiativeCommand(AIInitiativeId.generate(), null)));

        assertEquals("Expected revision is required", exception.getMessage());
        assertEquals(0, repository.loadCount);
        assertEquals(0, repository.saveCount());
    }

    @Test
    void clock_failure_is_not_translated_as_a_lifecycle_failure() {
        AIInitiative initiative = createRiskAssessedInitiative();
        var eventsBefore = initiative.domainEvents();
        var repository = new InMemoryAIInitiativeRepository(initiative);
        Clock clock = mock(Clock.class);
        var failure = new IllegalStateException("Clock unavailable");
        when(clock.instant()).thenThrow(failure);
        var useCase = new ApproveAIInitiativeUseCase(repository, clock);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> useCase.execute(new ApproveAIInitiativeCommand(
                        initiative.id(), new ExpectedRevision(initiative.id(), 7)))));
        assertEquals(InitiativeStatus.RISK_ASSESSED, initiative.status());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void repository_failure_is_not_translated_as_a_lifecycle_failure() {
        AIInitiative initiative = createRiskAssessedInitiative();
        var loaded = new LoadedAIInitiative(initiative, 7);
        AIInitiativeRepository repository = mock(AIInitiativeRepository.class);
        when(repository.findById(initiative.id())).thenReturn(Optional.of(loaded));
        var failure = new IllegalStateException("Save unavailable");
        when(repository.save(loaded)).thenThrow(failure);
        var useCase = new ApproveAIInitiativeUseCase(repository, CLOCK);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> useCase.execute(new ApproveAIInitiativeCommand(
                        initiative.id(), new ExpectedRevision(initiative.id(), 7)))));
    }

    private AIInitiative createRiskAssessedInitiative() {
        AIInitiative initiative = createInitiative();
        initiative.submit(SUBMITTED_AT);
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.HIGH, ASSESSED_AT);
        return initiative;
    }

    private AIInitiative createInitiative() {
        return AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("Detección de anomalías de asistencia")
                .description("Detectar patrones anómalos de asistencia laboral")
                .usesPersonalData(true)
                .impactsRights(true)
                .createdAt(CREATED_AT)
                .build();
    }

    private static final class InMemoryAIInitiativeRepository
            implements AIInitiativeRepository {

        private final Map<AIInitiativeId, AIInitiative> initiatives =
                new HashMap<>();
        private AIInitiative savedInitiative;
        private int saveCount;
        private int loadCount;

        private InMemoryAIInitiativeRepository(AIInitiative... initiatives) {
            for (AIInitiative initiative : initiatives) {
                this.initiatives.put(initiative.id(), initiative);
            }
        }

        @Override
        public AIInitiative create(AIInitiative initiative) {
            throw new AssertionError("Lifecycle changes must use the loaded revision");
        }

        @Override
        public SavedAIInitiative save(LoadedAIInitiative loaded) {
            assertEquals(7L, loaded.version(), "Must preserve the loaded revision");
            AIInitiative initiative = loaded.initiative();
            initiatives.put(initiative.id(), initiative);
            savedInitiative = initiative;
            saveCount++;
            return new SavedAIInitiative(initiative, 42);
        }

        @Override
        public Optional<LoadedAIInitiative> findById(AIInitiativeId id) {
            loadCount++;
            return Optional.ofNullable(initiatives.get(id))
                    .map(initiative -> new LoadedAIInitiative(initiative, 7));
        }

        AIInitiative savedInitiative() {
            return savedInitiative;
        }

        int saveCount() {
            return saveCount;
        }
    }
}
