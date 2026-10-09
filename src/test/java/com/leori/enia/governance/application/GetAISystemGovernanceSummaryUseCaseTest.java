package com.leori.enia.governance.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemGovernanceSummaryRepository;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetAISystemGovernanceSummaryUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");

    @Test
    void existing_system_calls_both_repositories_and_returns_summary_unchanged() {
        AISystem system = system();
        AISystemGovernanceSummary summary = summary(system.id());
        InMemoryAISystemRepository systems = new InMemoryAISystemRepository(system);
        RecordingSummaryRepository summaries = new RecordingSummaryRepository(summary);
        GetAISystemGovernanceSummaryUseCase useCase = new GetAISystemGovernanceSummaryUseCase(systems, summaries);

        AISystemGovernanceSummary result = useCase.execute(system.id());

        assertSame(summary, result);
        assertEquals(11, result.datasetCount());
        assertEquals(1, systems.finds);
        assertEquals(0, systems.creates);
        assertEquals(1, summaries.summarizes);
        assertEquals(system.id(), summaries.requestedId);
    }

    @Test
    void missing_system_fails_without_calling_summary_repository() {
        InMemoryAISystemRepository systems = new InMemoryAISystemRepository();
        RecordingSummaryRepository summaries = new RecordingSummaryRepository(summary(AISystemId.generate()));
        GetAISystemGovernanceSummaryUseCase useCase = new GetAISystemGovernanceSummaryUseCase(systems, summaries);
        AISystemId missingId = AISystemId.generate();

        AISystemNotFoundException exception = assertThrows(
                AISystemNotFoundException.class,
                () -> useCase.execute(missingId)
        );

        assertEquals("AI system not found: " + missingId, exception.getMessage());
        assertEquals(1, systems.finds);
        assertEquals(0, systems.creates);
        assertEquals(0, summaries.summarizes);
    }

    @Test
    void null_id_fails_before_repository_access() {
        InMemoryAISystemRepository systems = new InMemoryAISystemRepository();
        RecordingSummaryRepository summaries = new RecordingSummaryRepository(summary(AISystemId.generate()));
        GetAISystemGovernanceSummaryUseCase useCase = new GetAISystemGovernanceSummaryUseCase(systems, summaries);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("AI system id is required", exception.getMessage());
        assertEquals(0, systems.finds);
        assertEquals(0, systems.creates);
        assertEquals(0, summaries.summarizes);
    }

    @Test
    void projection_repository_failure_propagates_after_system_exists() {
        AISystem system = system();
        InMemoryAISystemRepository systems = new InMemoryAISystemRepository(system);
        RecordingSummaryRepository summaries = new RecordingSummaryRepository(new IllegalStateException("projection failed"));
        GetAISystemGovernanceSummaryUseCase useCase = new GetAISystemGovernanceSummaryUseCase(systems, summaries);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> useCase.execute(system.id()));

        assertEquals("projection failed", exception.getMessage());
        assertEquals(1, systems.finds);
        assertEquals(1, summaries.summarizes);
    }

    private AISystemGovernanceSummary summary(AISystemId id) {
        return new AISystemGovernanceSummary(id, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
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

    private static final class InMemoryAISystemRepository implements AISystemRepository {
        private final Map<AISystemId, AISystem> systems = new HashMap<>();
        private int finds;
        private int creates;

        private InMemoryAISystemRepository(AISystem... systems) {
            for (AISystem system : systems) {
                this.systems.put(system.id(), system);
            }
        }

        @Override
        public AISystem create(AISystem system) {
            creates++;
            throw new AssertionError("Read use case must not create an AI system");
        }

        @Override
        public Optional<AISystem> findById(AISystemId id) {
            finds++;
            return Optional.ofNullable(systems.get(id));
        }
    }

    private static final class RecordingSummaryRepository implements AISystemGovernanceSummaryRepository {
        private final AISystemGovernanceSummary summary;
        private final RuntimeException failure;
        private int summarizes;
        private AISystemId requestedId;

        private RecordingSummaryRepository(AISystemGovernanceSummary summary) {
            this.summary = summary;
            this.failure = null;
        }

        private RecordingSummaryRepository(RuntimeException failure) {
            this.summary = null;
            this.failure = failure;
        }

        @Override
        public AISystemGovernanceSummary summarize(AISystemId aiSystemId) {
            summarizes++;
            requestedId = aiSystemId;
            if (failure != null) {
                throw failure;
            }
            return summary;
        }
    }
}
