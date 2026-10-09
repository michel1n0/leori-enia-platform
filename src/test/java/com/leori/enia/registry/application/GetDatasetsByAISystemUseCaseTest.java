package com.leori.enia.registry.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import com.leori.enia.registry.application.port.AISystemDatasetRepository;
import com.leori.enia.registry.domain.AISystemDataset;
import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetDatasetsByAISystemUseCaseTest {

    private final RecordingAISystemRepository systems = new RecordingAISystemRepository();
    private final RecordingAssociationRepository associations = new RecordingAssociationRepository();

    @Test
    void returns_list_for_existing_parent_unchanged() {
        AISystem system = system();
        List<Dataset> expected = List.of(dataset("First"), dataset("Second"));
        systems.system = system;
        associations.datasets = expected;

        List<Dataset> result = useCase().execute(system.id());

        assertSame(expected, result);
        assertEquals(system.id(), associations.lastSystemId);
        assertEquals(1, systems.finds);
        assertEquals(1, associations.finds);
    }

    @Test
    void validates_parent_before_association_query() {
        AISystem system = system();
        systems.system = system;
        associations.datasets = List.of(dataset("Dataset"));

        useCase().execute(system.id());

        assertEquals(List.of("system", "association"), associations.calls);
    }

    @Test
    void missing_ai_system_fails_before_association_query() {
        AISystemId missingId = AISystemId.generate();

        AISystemNotFoundException exception = assertThrows(AISystemNotFoundException.class,
                () -> useCase().execute(missingId));

        assertEquals("AI system not found: " + missingId, exception.getMessage());
        assertEquals(1, systems.finds);
        assertEquals(0, associations.finds);
    }

    @Test
    void existing_system_with_no_datasets_returns_empty_list() {
        AISystem system = system();
        systems.system = system;
        associations.datasets = List.of();

        List<Dataset> result = useCase().execute(system.id());

        assertSame(associations.datasets, result);
        assertEquals(1, associations.finds);
    }

    @Test
    void null_ai_system_id_fails_before_repository_access() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase().execute(null));

        assertEquals("AI system id is required", exception.getMessage());
        assertEquals(0, systems.finds);
        assertEquals(0, associations.finds);
    }

    @Test
    void association_repository_failure_propagates_unchanged() {
        AISystem system = system();
        RuntimeException failure = new RuntimeException("Storage unavailable");
        systems.system = system;
        associations.failure = failure;

        assertSame(failure, assertThrows(RuntimeException.class, () -> useCase().execute(system.id())));
        assertEquals(1, systems.finds);
        assertEquals(1, associations.finds);
    }

    private GetDatasetsByAISystemUseCase useCase() {
        return new GetDatasetsByAISystemUseCase(systems, associations);
    }

    private AISystem system() {
        return AISystem.builder()
                .id(AISystemId.generate())
                .organizationId(OrganizationId.generate())
                .sourceInitiativeId(AIInitiativeId.generate())
                .name("System")
                .description("Description")
                .createdAt(Instant.parse("2026-09-25T14:00:00Z"))
                .build();
    }

    private Dataset dataset(String name) {
        return Dataset.builder()
                .id(DatasetId.generate())
                .name(name)
                .description("Description")
                .createdAt(Instant.parse("2026-09-29T10:00:00Z"))
                .build();
    }

    private final class RecordingAISystemRepository implements AISystemRepository {
        private AISystem system;
        private int finds;

        @Override
        public AISystem create(AISystem system) {
            throw new AssertionError("List use case must not create an AI system");
        }

        @Override
        public Optional<AISystem> findById(AISystemId id) {
            associations.calls.add("system");
            finds++;
            return Optional.ofNullable(system).filter(candidate -> candidate.id().equals(id));
        }
    }

    private static final class RecordingAssociationRepository implements AISystemDatasetRepository {
        private final List<String> calls = new ArrayList<>();
        private List<Dataset> datasets = List.of();
        private RuntimeException failure;
        private AISystemId lastSystemId;
        private int finds;

        @Override
        public AISystemDataset create(AISystemDataset association) {
            throw new AssertionError("List use case must not create an association");
        }

        @Override
        public List<Dataset> findDatasetsByAISystemId(AISystemId aiSystemId) {
            calls.add("association");
            finds++;
            lastSystemId = aiSystemId;
            if (failure != null) {
                throw failure;
            }
            return datasets;
        }
    }
}
