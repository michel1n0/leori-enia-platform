package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.application.port.SavedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.initiative.domain.event.AIInitiativeRejected;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

class RejectAIInitiativeUseCaseTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-15T14:00:00Z");
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-16T13:00:00Z");
    private static final Instant ASSESSED_AT =
            Instant.parse("2026-09-16T14:00:00Z");
    private static final Instant REJECTED_AT =
            Instant.parse("2026-09-16T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(REJECTED_AT, ZoneOffset.UTC);

    @Test
    void should_reject_and_save_a_risk_assessed_initiative() {
        AIInitiative initiative = createRiskAssessedInitiative();
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(initiative);
        RejectAIInitiativeUseCase useCase =
                new RejectAIInitiativeUseCase(repository, CLOCK);

        AIInitiative result = useCase.execute(
                new RejectAIInitiativeCommand(
                        initiative.id(),
                        "  Riesgo residual no aceptable  "
                )
        );

        assertEquals(InitiativeStatus.REJECTED, initiative.status());
        assertEquals(1, repository.saveCount());
        assertSame(initiative, repository.savedInitiative());
        assertSame(initiative, result);
        assertEquals(1, initiative.domainEvents().size());

        AIInitiativeRejected event = assertInstanceOf(
                AIInitiativeRejected.class,
                initiative.domainEvents().getFirst()
        );
        assertEquals(initiative.id(), event.initiativeId());
        assertEquals("Riesgo residual no aceptable", event.reason());
        assertEquals(REJECTED_AT, event.occurredAt());
    }

    @Test
    void should_fail_when_initiative_does_not_exist() {
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository();
        RejectAIInitiativeUseCase useCase =
                new RejectAIInitiativeUseCase(repository, CLOCK);
        AIInitiativeId missingId = AIInitiativeId.generate();

        AIInitiativeNotFoundException exception = assertThrows(
                AIInitiativeNotFoundException.class,
                () -> useCase.execute(
                        new RejectAIInitiativeCommand(missingId, "Reason")
                )
        );

        assertEquals("AI initiative not found: " + missingId, exception.getMessage());
        assertEquals(0, repository.saveCount());
    }

    @Test
    void should_reject_null_command() {
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository();
        RejectAIInitiativeUseCase useCase =
                new RejectAIInitiativeUseCase(repository, CLOCK);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> useCase.execute(null)
        );

        assertEquals(
                "Reject AI initiative command is required",
                exception.getMessage()
        );
        assertEquals(0, repository.saveCount());
    }

    @Test
    void should_delegate_invalid_transition_enforcement_to_domain() {
        AIInitiative draftInitiative = createInitiative();
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(draftInitiative);
        RejectAIInitiativeUseCase useCase =
                new RejectAIInitiativeUseCase(repository, CLOCK);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> useCase.execute(
                        new RejectAIInitiativeCommand(
                                draftInitiative.id(),
                                "Reason"
                        )
                )
        );

        assertEquals(
                "Expected initiative status RISK_ASSESSED but was DRAFT",
                exception.getMessage()
        );
        assertEquals(0, repository.saveCount());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void should_delegate_invalid_reason_enforcement_to_domain(String reason) {
        AIInitiative initiative = createRiskAssessedInitiative();
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository(initiative);
        RejectAIInitiativeUseCase useCase =
                new RejectAIInitiativeUseCase(repository, CLOCK);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(
                        new RejectAIInitiativeCommand(initiative.id(), reason)
                )
        );

        assertEquals("Rejection reason is required", exception.getMessage());
        assertEquals(InitiativeStatus.RISK_ASSESSED, initiative.status());
        assertEquals(0, repository.saveCount());
    }

    private AIInitiative createRiskAssessedInitiative() {
        AIInitiative initiative = createInitiative();
        initiative.submit(SUBMITTED_AT);
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.HIGH, ASSESSED_AT);
        initiative.clearDomainEvents();
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
            return new SavedAIInitiative(initiative, loaded.version() + 1);
        }

        @Override
        public Optional<LoadedAIInitiative> findById(AIInitiativeId id) {
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
