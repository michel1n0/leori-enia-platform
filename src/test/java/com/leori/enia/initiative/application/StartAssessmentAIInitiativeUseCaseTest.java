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
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StartAssessmentAIInitiativeUseCaseTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-15T14:00:00Z");
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-16T15:00:00Z");

    @Test
    void should_start_assessment_and_save_a_submitted_initiative() {
        AIInitiative initiative = createInitiative();
        initiative.submit(SUBMITTED_AT);
        var eventsBefore = List.copyOf(initiative.domainEvents());
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(initiative);
        StartAssessmentAIInitiativeUseCase useCase =
                new StartAssessmentAIInitiativeUseCase(repository);

        VersionedAIInitiativeDetails result = useCase.execute(
                new StartAssessmentAIInitiativeCommand(initiative.id(), new ExpectedRevision(initiative.id(), 7))
        );

        assertEquals(InitiativeStatus.UNDER_ASSESSMENT, initiative.status());
        assertEquals(1, repository.saveCount());
        assertSame(initiative, repository.savedInitiative());
        assertEquals(AIInitiativeDetails.from(initiative), result.details());
        assertEquals(InitiativeStatus.UNDER_ASSESSMENT, result.details().status());
        assertEquals(42, result.revision());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(1, repository.loadCount);
    }

    @Test
    void should_fail_when_initiative_does_not_exist() {
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository();
        StartAssessmentAIInitiativeUseCase useCase =
                new StartAssessmentAIInitiativeUseCase(repository);
        AIInitiativeId missingId = AIInitiativeId.generate();

        AIInitiativeNotFoundException exception = assertThrows(
                AIInitiativeNotFoundException.class,
                () -> useCase.execute(
                        new StartAssessmentAIInitiativeCommand(missingId, new ExpectedRevision(missingId, 7))
                )
        );

        assertEquals("AI initiative not found: " + missingId, exception.getMessage());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void should_reject_null_command() {
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository();
        StartAssessmentAIInitiativeUseCase useCase =
                new StartAssessmentAIInitiativeUseCase(repository);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> useCase.execute(null)
        );

        assertEquals(
                "Start assessment AI initiative command is required",
                exception.getMessage()
        );
        assertEquals(0, repository.saveCount());
    }

    @Test
    void should_delegate_invalid_transition_enforcement_to_domain() {
        AIInitiative draftInitiative = createInitiative();
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(draftInitiative);
        StartAssessmentAIInitiativeUseCase useCase =
                new StartAssessmentAIInitiativeUseCase(repository);

        AIInitiativeInvalidTransitionException exception = assertThrows(
                AIInitiativeInvalidTransitionException.class,
                () -> useCase.execute(
                        new StartAssessmentAIInitiativeCommand(
                                draftInitiative.id(), new ExpectedRevision(draftInitiative.id(), 7)
                        )
                )
        );

        assertEquals(
                "Expected initiative status SUBMITTED but was DRAFT",
                exception.getMessage()
        );
        assertEquals(IllegalStateException.class, exception.getCause().getClass());
        assertEquals(exception.getMessage(), exception.getCause().getMessage());
        assertEquals(InitiativeStatus.DRAFT, draftInitiative.status());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void stale_revision_does_not_mutate_save_or_add_events() {
        AIInitiative initiative = createInitiative();
        initiative.submit(SUBMITTED_AT);
        var eventsBefore = List.copyOf(initiative.domainEvents());
        InMemoryAIInitiativeRepository repository = new InMemoryAIInitiativeRepository(initiative);
        StartAssessmentAIInitiativeUseCase useCase = new StartAssessmentAIInitiativeUseCase(repository);

        assertThrows(AIInitiativeRevisionMismatchException.class,
                () -> useCase.execute(new StartAssessmentAIInitiativeCommand(
                        initiative.id(), new ExpectedRevision(initiative.id(), 6))));

        assertEquals(InitiativeStatus.SUBMITTED, initiative.status());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void another_initiatives_revision_does_not_mutate_save_or_add_events() {
        AIInitiative initiative = createInitiative();
        initiative.submit(SUBMITTED_AT);
        var eventsBefore = List.copyOf(initiative.domainEvents());
        InMemoryAIInitiativeRepository repository = new InMemoryAIInitiativeRepository(initiative);
        StartAssessmentAIInitiativeUseCase useCase = new StartAssessmentAIInitiativeUseCase(repository);

        assertThrows(AIInitiativeRevisionMismatchException.class,
                () -> useCase.execute(new StartAssessmentAIInitiativeCommand(
                        initiative.id(), new ExpectedRevision(AIInitiativeId.generate(), 7))));

        assertEquals(InitiativeStatus.SUBMITTED, initiative.status());
        assertEquals(eventsBefore, initiative.domainEvents());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void should_reject_null_expected_revision_before_lookup() {
        InMemoryAIInitiativeRepository repository = new InMemoryAIInitiativeRepository();
        StartAssessmentAIInitiativeUseCase useCase = new StartAssessmentAIInitiativeUseCase(repository);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> useCase.execute(new StartAssessmentAIInitiativeCommand(AIInitiativeId.generate(), null)));

        assertEquals("Expected revision is required", exception.getMessage());
        assertEquals(0, repository.loadCount);
        assertEquals(0, repository.saveCount());
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
