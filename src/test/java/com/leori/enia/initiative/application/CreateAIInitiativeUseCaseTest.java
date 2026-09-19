package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CreateAIInitiativeUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-16T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void should_create_and_save_an_ai_initiative() {
        InMemoryAIInitiativeRepository repository =
                new InMemoryAIInitiativeRepository();
        CreateAIInitiativeUseCase useCase =
                new CreateAIInitiativeUseCase(repository, CLOCK);
        OrganizationId organizationId = OrganizationId.generate();
        CreateAIInitiativeCommand command = new CreateAIInitiativeCommand(
                organizationId,
                "Detección de anomalías de asistencia",
                "Detectar patrones anómalos de asistencia laboral",
                true,
                true
        );

        AIInitiative initiative = useCase.execute(command);

        assertNotNull(initiative.id());
        assertEquals(organizationId, initiative.organizationId());
        assertEquals(command.name(), initiative.name());
        assertEquals(command.description(), initiative.description());
        assertEquals(command.usesPersonalData(), initiative.usesPersonalData());
        assertEquals(command.impactsRights(), initiative.impactsRights());
        assertEquals(InitiativeStatus.DRAFT, initiative.status());
        assertEquals(RiskLevel.NOT_ASSESSED, initiative.preliminaryRisk());
        assertEquals(NOW, initiative.createdAt());
        assertEquals(1, repository.savedInitiatives().size());
        assertSame(initiative, repository.savedInitiatives().getFirst());
    }

    private static final class InMemoryAIInitiativeRepository
            implements AIInitiativeRepository {

        private final List<AIInitiative> initiatives = new ArrayList<>();

        @Override
        public AIInitiative create(AIInitiative initiative) {
            initiatives.add(initiative);
            return initiative;
        }

        @Override
        public AIInitiative save(LoadedAIInitiative loaded) {
            throw new AssertionError("Creation must not update an existing initiative");
        }

        @Override
        public Optional<LoadedAIInitiative> findById(AIInitiativeId id) {
            return initiatives.stream()
                    .filter(initiative -> initiative.id().equals(id))
                    .findFirst()
                    .map(initiative -> new LoadedAIInitiative(initiative, 0));
        }

        List<AIInitiative> savedInitiatives() {
            return List.copyOf(initiatives);
        }
    }
}
