package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.exception.AIInitiativeRevisionMismatchException;
import com.leori.enia.initiative.application.exception.AIInitiativeInvalidTransitionException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.application.port.SavedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.event.AIInitiativeSubmitted;
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

class SubmitAIInitiativeUseCaseTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-15T14:00:00Z");
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-16T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(SUBMITTED_AT, ZoneOffset.UTC);

    @Test
    void should_submit_and_save_an_existing_draft_initiative() {
        AIInitiative initiative = createInitiative();
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(initiative);
        SubmitAIInitiativeUseCase useCase =
                new SubmitAIInitiativeUseCase(repository, CLOCK);

        VersionedAIInitiativeDetails result = useCase.execute(
                new SubmitAIInitiativeCommand(initiative.id(), new ExpectedRevision(initiative.id(), 7))
        );
        AIInitiative submitted = repository.findById(initiative.id()).orElseThrow().initiative();

        assertSame(initiative, submitted);
        assertEquals(8, result.revision());
        assertEquals(InitiativeStatus.SUBMITTED, result.details().status());
        assertEquals(InitiativeStatus.SUBMITTED, submitted.status());
        assertEquals(1, repository.saveCount());
        assertSame(submitted, repository.findById(initiative.id()).orElseThrow().initiative());
        assertEquals(1, submitted.domainEvents().size());

        AIInitiativeSubmitted event = assertInstanceOf(
                AIInitiativeSubmitted.class,
                submitted.domainEvents().getFirst()
        );
        assertEquals(initiative.id(), event.initiativeId());
        assertEquals(SUBMITTED_AT, event.occurredAt());
    }

    @Test
    void should_fail_when_initiative_does_not_exist() {
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository();
        SubmitAIInitiativeUseCase useCase =
                new SubmitAIInitiativeUseCase(repository, CLOCK);
        AIInitiativeId missingId = AIInitiativeId.generate();

        AIInitiativeNotFoundException exception = assertThrows(
                AIInitiativeNotFoundException.class,
                () -> useCase.execute(new SubmitAIInitiativeCommand(missingId, new ExpectedRevision(missingId, 0)))
        );

        assertEquals("AI initiative not found: " + missingId, exception.getMessage());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void stale_revision_does_not_mutate_or_save() {
        AIInitiative initiative = createInitiative();
        InMemoryAIInitiativeRepository repository = new InMemoryAIInitiativeRepository(initiative);
        SubmitAIInitiativeUseCase useCase = new SubmitAIInitiativeUseCase(repository, CLOCK);

        assertThrows(AIInitiativeRevisionMismatchException.class,
                () -> useCase.execute(new SubmitAIInitiativeCommand(initiative.id(), new ExpectedRevision(initiative.id(), 6))));

        assertEquals(InitiativeStatus.DRAFT, initiative.status());
        assertEquals(0, initiative.domainEvents().size());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void revision_for_another_initiative_does_not_authorize_mutation() {
        AIInitiative initiative = createInitiative();
        InMemoryAIInitiativeRepository repository = new InMemoryAIInitiativeRepository(initiative);
        SubmitAIInitiativeUseCase useCase = new SubmitAIInitiativeUseCase(repository, CLOCK);

        assertThrows(AIInitiativeRevisionMismatchException.class,
                () -> useCase.execute(new SubmitAIInitiativeCommand(initiative.id(),
                        new ExpectedRevision(AIInitiativeId.generate(), 7))));
        assertEquals(InitiativeStatus.DRAFT, initiative.status());
        assertEquals(0, initiative.domainEvents().size());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void current_revision_still_obeys_lifecycle_rules() {
        AIInitiative initiative = createInitiative();
        initiative.submit(SUBMITTED_AT);
        InMemoryAIInitiativeRepository repository = new InMemoryAIInitiativeRepository(initiative);
        SubmitAIInitiativeUseCase useCase = new SubmitAIInitiativeUseCase(repository, CLOCK);

        assertThrows(AIInitiativeInvalidTransitionException.class,
                () -> useCase.execute(new SubmitAIInitiativeCommand(initiative.id(), new ExpectedRevision(initiative.id(), 7))));
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
        private int saveCount;

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
            saveCount++;
            return new SavedAIInitiative(initiative, loaded.version() + 1);
        }

        @Override
        public Optional<LoadedAIInitiative> findById(AIInitiativeId id) {
            return Optional.ofNullable(initiatives.get(id))
                    .map(initiative -> new LoadedAIInitiative(initiative, 7));
        }

        int saveCount() {
            return saveCount;
        }
    }
}
