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
import com.leori.enia.initiative.domain.event.AIInitiativeRiskAssessed;
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

class AssessRiskAIInitiativeUseCaseTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-15T14:00:00Z");
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-16T14:00:00Z");
    private static final Instant ASSESSED_AT =
            Instant.parse("2026-09-16T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(ASSESSED_AT, ZoneOffset.UTC);

    @Test
    void should_assess_preliminary_risk_and_save_an_initiative() {
        AIInitiative initiative = createInitiativeUnderAssessment();
        var eventsBefore = initiative.domainEvents();
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(initiative);
        AssessRiskAIInitiativeUseCase useCase =
                new AssessRiskAIInitiativeUseCase(repository, CLOCK);

        VersionedAIInitiativeDetails result = useCase.execute(
                new AssessRiskAIInitiativeCommand(
                        initiative.id(),
                        RiskLevel.HIGH, new ExpectedRevision(initiative.id(), 7)
                )
        );

        assertEquals(RiskLevel.HIGH, initiative.preliminaryRisk());
        assertEquals(InitiativeStatus.RISK_ASSESSED, initiative.status());
        assertEquals(1, repository.saveCount());
        assertSame(initiative, repository.savedInitiative());
        assertEquals(AIInitiativeDetails.from(repository.savedInitiative()), result.details());
        assertEquals(42, result.revision());
        assertEquals(1, repository.loadCount);
        assertEquals(eventsBefore.size() + 1, initiative.domainEvents().size());
        assertEquals(eventsBefore, initiative.domainEvents().subList(0, eventsBefore.size()));

        AIInitiativeRiskAssessed event = assertInstanceOf(
                AIInitiativeRiskAssessed.class,
                initiative.domainEvents().getLast()
        );
        assertEquals(initiative.id(), event.initiativeId());
        assertEquals(RiskLevel.HIGH, event.riskLevel());
        assertEquals(ASSESSED_AT, event.occurredAt());
    }

    @Test
    void should_fail_when_initiative_does_not_exist() {
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository();
        AssessRiskAIInitiativeUseCase useCase =
                new AssessRiskAIInitiativeUseCase(repository, CLOCK);
        AIInitiativeId missingId = AIInitiativeId.generate();

        AIInitiativeNotFoundException exception = assertThrows(
                AIInitiativeNotFoundException.class,
                () -> useCase.execute(
                        new AssessRiskAIInitiativeCommand(
                                missingId,
                                RiskLevel.MEDIUM, new ExpectedRevision(missingId, 7)
                        )
                )
        );

        assertEquals("AI initiative not found: " + missingId, exception.getMessage());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void should_reject_null_command() {
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository();
        AssessRiskAIInitiativeUseCase useCase =
                new AssessRiskAIInitiativeUseCase(repository, CLOCK);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> useCase.execute(null)
        );

        assertEquals(
                "Assess risk AI initiative command is required",
                exception.getMessage()
        );
        assertEquals(0, repository.saveCount());
    }

    @Test
    void should_delegate_invalid_transition_enforcement_to_domain() {
        AIInitiative draftInitiative = createInitiative();
        var eventsBefore = draftInitiative.domainEvents();
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(draftInitiative);
        AssessRiskAIInitiativeUseCase useCase =
                new AssessRiskAIInitiativeUseCase(repository, CLOCK);

        AIInitiativeInvalidTransitionException exception = assertThrows(
                AIInitiativeInvalidTransitionException.class,
                () -> useCase.execute(
                        new AssessRiskAIInitiativeCommand(
                                draftInitiative.id(),
                                RiskLevel.LOW, new ExpectedRevision(draftInitiative.id(), 7)
                        )
                )
        );

        assertEquals(
                "Expected initiative status UNDER_ASSESSMENT but was DRAFT",
                exception.getMessage()
        );
        assertEquals(IllegalStateException.class, exception.getCause().getClass());
        assertEquals(exception.getMessage(), exception.getCause().getMessage());
        assertEquals(InitiativeStatus.DRAFT, draftInitiative.status());
        assertEquals(RiskLevel.NOT_ASSESSED, draftInitiative.preliminaryRisk());
        assertEquals(eventsBefore, draftInitiative.domainEvents());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void should_delegate_not_assessed_risk_rejection_to_domain() {
        AIInitiative initiative = createInitiativeUnderAssessment();
        var eventsBefore = initiative.domainEvents();
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(initiative);
        AssessRiskAIInitiativeUseCase useCase =
                new AssessRiskAIInitiativeUseCase(repository, CLOCK);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(
                        new AssessRiskAIInitiativeCommand(
                                initiative.id(),
                                RiskLevel.NOT_ASSESSED, new ExpectedRevision(initiative.id(), 7)
                        )
                )
        );

        assertEquals(
                "Risk assessment must have a valid risk level",
                exception.getMessage()
        );
        assertEquals(InitiativeStatus.UNDER_ASSESSMENT, initiative.status());
        assertEquals(RiskLevel.NOT_ASSESSED, initiative.preliminaryRisk());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void stale_revision_does_not_mutate_save_or_add_events() {
        AIInitiative initiative = createInitiativeUnderAssessment();
        var eventsBefore = initiative.domainEvents();
        InMemoryAIInitiativeRepository repository = new InMemoryAIInitiativeRepository(initiative);
        AssessRiskAIInitiativeUseCase useCase = new AssessRiskAIInitiativeUseCase(repository, CLOCK);

        assertThrows(AIInitiativeRevisionMismatchException.class,
                () -> useCase.execute(new AssessRiskAIInitiativeCommand(
                        initiative.id(), RiskLevel.HIGH, new ExpectedRevision(initiative.id(), 6))));

        assertEquals(InitiativeStatus.UNDER_ASSESSMENT, initiative.status());
        assertEquals(RiskLevel.NOT_ASSESSED, initiative.preliminaryRisk());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void foreign_revision_does_not_mutate_save_or_add_events() {
        AIInitiative initiative = createInitiativeUnderAssessment();
        var eventsBefore = initiative.domainEvents();
        InMemoryAIInitiativeRepository repository = new InMemoryAIInitiativeRepository(initiative);
        AssessRiskAIInitiativeUseCase useCase = new AssessRiskAIInitiativeUseCase(repository, CLOCK);

        assertThrows(AIInitiativeRevisionMismatchException.class,
                () -> useCase.execute(new AssessRiskAIInitiativeCommand(
                        initiative.id(), RiskLevel.HIGH, new ExpectedRevision(AIInitiativeId.generate(), 7))));

        assertEquals(InitiativeStatus.UNDER_ASSESSMENT, initiative.status());
        assertEquals(RiskLevel.NOT_ASSESSED, initiative.preliminaryRisk());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void null_risk_preserves_domain_validation_without_mutation() {
        AIInitiative initiative = createInitiativeUnderAssessment();
        var eventsBefore = initiative.domainEvents();
        InMemoryAIInitiativeRepository repository = new InMemoryAIInitiativeRepository(initiative);
        AssessRiskAIInitiativeUseCase useCase = new AssessRiskAIInitiativeUseCase(repository, CLOCK);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> useCase.execute(new AssessRiskAIInitiativeCommand(
                        initiative.id(), null, new ExpectedRevision(initiative.id(), 7))));

        assertEquals("Risk level is required", exception.getMessage());
        assertEquals(InitiativeStatus.UNDER_ASSESSMENT, initiative.status());
        assertEquals(RiskLevel.NOT_ASSESSED, initiative.preliminaryRisk());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void null_expected_revision_is_rejected_before_lookup() {
        InMemoryAIInitiativeRepository repository = new InMemoryAIInitiativeRepository();
        AssessRiskAIInitiativeUseCase useCase = new AssessRiskAIInitiativeUseCase(repository, CLOCK);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> useCase.execute(new AssessRiskAIInitiativeCommand(
                        AIInitiativeId.generate(), RiskLevel.HIGH, null)));

        assertEquals("Expected revision is required", exception.getMessage());
        assertEquals(0, repository.loadCount);
        assertEquals(0, repository.saveCount());
    }

    private AIInitiative createInitiativeUnderAssessment() {
        AIInitiative initiative = createInitiative();
        initiative.submit(SUBMITTED_AT);
        initiative.startAssessment();
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
