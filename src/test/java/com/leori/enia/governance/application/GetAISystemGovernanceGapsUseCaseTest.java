package com.leori.enia.governance.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemGovernanceGapsRepository;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.domain.AIInitiativeId;
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

class GetAISystemGovernanceGapsUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");

    @Test
    void existing_system_calls_gaps_repository_after_existence_validation_and_returns_gaps_unchanged() {
        AISystem system = system();
        AISystemGovernanceGaps gaps = emptyGaps(system.id());
        CallRecorder calls = new CallRecorder();
        InMemoryAISystemRepository systems = new InMemoryAISystemRepository(calls, system);
        RecordingGapsRepository gapsRepository = new RecordingGapsRepository(calls, gaps);
        GetAISystemGovernanceGapsUseCase useCase = new GetAISystemGovernanceGapsUseCase(systems, gapsRepository);

        AISystemGovernanceGaps result = useCase.execute(system.id());

        assertSame(gaps, result);
        assertEquals(1, systems.finds);
        assertEquals(0, systems.creates);
        assertEquals(1, gapsRepository.finds);
        assertEquals(system.id(), gapsRepository.requestedId);
        assertEquals(2, gapsRepository.findOrder);
        assertEquals(0, systems.createOrder);
    }

    @Test
    void missing_system_fails_without_calling_gaps_repository() {
        CallRecorder calls = new CallRecorder();
        InMemoryAISystemRepository systems = new InMemoryAISystemRepository(calls);
        RecordingGapsRepository gapsRepository = new RecordingGapsRepository(calls, emptyGaps(AISystemId.generate()));
        GetAISystemGovernanceGapsUseCase useCase = new GetAISystemGovernanceGapsUseCase(systems, gapsRepository);
        AISystemId missingId = AISystemId.generate();

        AISystemNotFoundException exception = assertThrows(
                AISystemNotFoundException.class,
                () -> useCase.execute(missingId)
        );

        assertEquals("AI system not found: " + missingId, exception.getMessage());
        assertEquals(1, systems.finds);
        assertEquals(0, systems.creates);
        assertEquals(0, gapsRepository.finds);
    }

    @Test
    void null_id_fails_before_repository_access() {
        CallRecorder calls = new CallRecorder();
        InMemoryAISystemRepository systems = new InMemoryAISystemRepository(calls);
        RecordingGapsRepository gapsRepository = new RecordingGapsRepository(calls, emptyGaps(AISystemId.generate()));
        GetAISystemGovernanceGapsUseCase useCase = new GetAISystemGovernanceGapsUseCase(systems, gapsRepository);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("AI system id is required", exception.getMessage());
        assertEquals(0, systems.finds);
        assertEquals(0, systems.creates);
        assertEquals(0, gapsRepository.finds);
    }

    @Test
    void projection_repository_failure_propagates_after_system_exists() {
        AISystem system = system();
        CallRecorder calls = new CallRecorder();
        InMemoryAISystemRepository systems = new InMemoryAISystemRepository(calls, system);
        RecordingGapsRepository gapsRepository = new RecordingGapsRepository(calls, new IllegalStateException("projection failed"));
        GetAISystemGovernanceGapsUseCase useCase = new GetAISystemGovernanceGapsUseCase(systems, gapsRepository);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> useCase.execute(system.id()));

        assertEquals("projection failed", exception.getMessage());
        assertEquals(1, systems.finds);
        assertEquals(1, gapsRepository.finds);
    }

    @Test
    void existing_system_with_no_gaps_returns_empty_lists() {
        AISystem system = system();
        AISystemGovernanceGaps gaps = emptyGaps(system.id());
        CallRecorder calls = new CallRecorder();
        InMemoryAISystemRepository systems = new InMemoryAISystemRepository(calls, system);
        RecordingGapsRepository gapsRepository = new RecordingGapsRepository(calls, gaps);
        GetAISystemGovernanceGapsUseCase useCase = new GetAISystemGovernanceGapsUseCase(systems, gapsRepository);

        AISystemGovernanceGaps result = useCase.execute(system.id());

        assertEquals(List.of(), result.findingsWithoutControls());
        assertEquals(List.of(), result.controlsWithoutImplementation());
        assertEquals(List.of(), result.implementationsWithoutEvidence());
    }

    private AISystemGovernanceGaps emptyGaps(AISystemId id) {
        return new AISystemGovernanceGaps(id, List.of(), List.of(), List.of());
    }

    private AISystem system() {
        return AISystem.builder()
                .id(AISystemId.generate())
                .organizationId(OrganizationId.generate())
                .sourceInitiativeId(AIInitiativeId.generate())
                .name("System")
                .description("Description")
                .createdAt(CREATED_AT)
                .build();
    }

    private static final class CallRecorder {
        private int sequence;

        private int nextOrder() {
            return ++sequence;
        }
    }

    private static final class InMemoryAISystemRepository implements AISystemRepository {
        private final CallRecorder calls;
        private final Map<AISystemId, AISystem> systems = new HashMap<>();
        private int finds;
        private int creates;
        private int createOrder;

        private InMemoryAISystemRepository(CallRecorder calls, AISystem... systems) {
            this.calls = calls;
            for (AISystem system : systems) {
                this.systems.put(system.id(), system);
            }
        }

        @Override
        public AISystem create(AISystem system) {
            creates++;
            createOrder = calls.nextOrder();
            throw new AssertionError("Read use case must not create an AI system");
        }

        @Override
        public Optional<AISystem> findById(AISystemId id) {
            finds++;
            calls.nextOrder();
            return Optional.ofNullable(systems.get(id));
        }
    }

    private static final class RecordingGapsRepository implements AISystemGovernanceGapsRepository {
        private final CallRecorder calls;
        private final AISystemGovernanceGaps gaps;
        private final RuntimeException failure;
        private int finds;
        private int findOrder;
        private AISystemId requestedId;

        private RecordingGapsRepository(CallRecorder calls, AISystemGovernanceGaps gaps) {
            this.calls = calls;
            this.gaps = gaps;
            this.failure = null;
        }

        private RecordingGapsRepository(CallRecorder calls, RuntimeException failure) {
            this.calls = calls;
            this.gaps = null;
            this.failure = failure;
        }

        @Override
        public AISystemGovernanceGaps findByAISystemId(AISystemId aiSystemId) {
            finds++;
            findOrder = calls.nextOrder();
            requestedId = aiSystemId;
            if (failure != null) {
                throw failure;
            }
            return gaps;
        }
    }
}
