package com.leori.enia.registry.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import com.leori.enia.registry.application.exception.DatasetAlreadyAssociatedWithAISystemException;
import com.leori.enia.registry.application.exception.DatasetNotFoundException;
import com.leori.enia.registry.application.port.AISystemDatasetRepository;
import com.leori.enia.registry.application.port.DatasetRepository;
import com.leori.enia.registry.domain.AISystemDataset;
import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AssociateDatasetWithAISystemUseCaseTest {

    private static final Instant ASSOCIATED_AT = Instant.parse("2026-10-06T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(ASSOCIATED_AT, ZoneOffset.UTC);

    private final RecordingAISystemRepository systems = new RecordingAISystemRepository();
    private final RecordingDatasetRepository datasets = new RecordingDatasetRepository();
    private final RecordingAssociationRepository associations = new RecordingAssociationRepository();

    @Test
    void returns_created_association_for_existing_system_and_dataset() {
        AISystem system = system();
        Dataset dataset = dataset();
        systems.system = system;
        datasets.dataset = dataset;
        var useCase = useCase();

        AISystemDataset result = useCase.execute(new AssociateDatasetWithAISystemCommand(system.id(), dataset.id()));

        assertSame(associations.created, result);
        assertEquals(system.id(), result.aiSystemId());
        assertEquals(dataset.id(), result.datasetId());
        assertEquals(ASSOCIATED_AT, result.associatedAt());
        assertEquals(1, systems.finds);
        assertEquals(1, datasets.finds);
        assertEquals(1, associations.creates);
    }

    @Test
    void fixed_clock_determines_associated_at() {
        Instant expected = Instant.parse("2026-10-07T08:00:00Z");
        systems.system = system();
        datasets.dataset = dataset();
        var useCase = new AssociateDatasetWithAISystemUseCase(
                systems,
                datasets,
                associations,
                Clock.fixed(expected, ZoneOffset.UTC)
        );

        AISystemDataset result = useCase.execute(new AssociateDatasetWithAISystemCommand(
                systems.system.id(),
                datasets.dataset.id()
        ));

        assertEquals(expected, result.associatedAt());
    }

    @Test
    void validates_ai_system_before_dataset() {
        AISystem system = system();
        Dataset dataset = dataset();
        systems.system = system;
        datasets.dataset = dataset;

        useCase().execute(new AssociateDatasetWithAISystemCommand(system.id(), dataset.id()));

        assertEquals(List.of("system", "dataset", "association"), associations.calls);
    }

    @Test
    void missing_ai_system_fails_before_dataset_lookup() {
        AISystemId missingId = AISystemId.generate();
        Dataset dataset = dataset();
        datasets.dataset = dataset;

        AISystemNotFoundException exception = assertThrows(AISystemNotFoundException.class,
                () -> useCase().execute(new AssociateDatasetWithAISystemCommand(missingId, dataset.id())));

        assertEquals("AI system not found: " + missingId, exception.getMessage());
        assertEquals(1, systems.finds);
        assertEquals(0, datasets.finds);
        assertEquals(0, associations.creates);
    }

    @Test
    void missing_dataset_fails_before_association_persistence() {
        AISystem system = system();
        DatasetId missingId = DatasetId.generate();
        systems.system = system;

        DatasetNotFoundException exception = assertThrows(DatasetNotFoundException.class,
                () -> useCase().execute(new AssociateDatasetWithAISystemCommand(system.id(), missingId)));

        assertEquals("Dataset not found: " + missingId, exception.getMessage());
        assertEquals(1, systems.finds);
        assertEquals(1, datasets.finds);
        assertEquals(0, associations.creates);
    }

    @Test
    void repository_duplicate_exception_propagates_unchanged() {
        systems.system = system();
        datasets.dataset = dataset();
        DatasetAlreadyAssociatedWithAISystemException failure =
                new DatasetAlreadyAssociatedWithAISystemException(new RuntimeException("duplicate"));
        associations.failure = failure;

        assertSame(failure, assertThrows(DatasetAlreadyAssociatedWithAISystemException.class,
                () -> useCase().execute(new AssociateDatasetWithAISystemCommand(
                        systems.system.id(),
                        datasets.dataset.id()
                ))));
    }

    @Test
    void generic_repository_failure_propagates_unchanged() {
        systems.system = system();
        datasets.dataset = dataset();
        RuntimeException failure = new RuntimeException("Storage unavailable");
        associations.failure = failure;

        assertSame(failure, assertThrows(RuntimeException.class,
                () -> useCase().execute(new AssociateDatasetWithAISystemCommand(
                        systems.system.id(),
                        datasets.dataset.id()
                ))));
    }

    @Test
    void null_command_fails_before_repository_or_clock_access() {
        AISystemRepository systemRepository = mock(AISystemRepository.class);
        DatasetRepository datasetRepository = mock(DatasetRepository.class);
        AISystemDatasetRepository associationRepository = mock(AISystemDatasetRepository.class);
        Clock clock = mock(Clock.class);
        var useCase = new AssociateDatasetWithAISystemUseCase(
                systemRepository,
                datasetRepository,
                associationRepository,
                clock
        );

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Associate dataset with AI system command is required", exception.getMessage());
        verifyNoInteractions(systemRepository, datasetRepository, associationRepository, clock);
    }

    private AssociateDatasetWithAISystemUseCase useCase() {
        return new AssociateDatasetWithAISystemUseCase(systems, datasets, associations, CLOCK);
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

    private Dataset dataset() {
        return Dataset.builder()
                .id(DatasetId.generate())
                .name("Dataset")
                .description("Description")
                .createdAt(Instant.parse("2026-09-29T10:00:00Z"))
                .build();
    }

    private final class RecordingAISystemRepository implements AISystemRepository {
        private AISystem system;
        private int finds;

        @Override
        public AISystem create(AISystem system) {
            throw new AssertionError("Association use case must not create an AI system");
        }

        @Override
        public Optional<AISystem> findById(AISystemId id) {
            associations.calls.add("system");
            finds++;
            return Optional.ofNullable(system).filter(candidate -> candidate.id().equals(id));
        }
    }

    private final class RecordingDatasetRepository implements DatasetRepository {
        private Dataset dataset;
        private int finds;

        @Override
        public Dataset create(Dataset dataset) {
            throw new AssertionError("Association use case must not create a dataset");
        }

        @Override
        public Optional<Dataset> findById(DatasetId id) {
            associations.calls.add("dataset");
            finds++;
            return Optional.ofNullable(dataset).filter(candidate -> candidate.id().equals(id));
        }
    }

    private static final class RecordingAssociationRepository implements AISystemDatasetRepository {
        private final List<String> calls = new ArrayList<>();
        private AISystemDataset created;
        private RuntimeException failure;
        private int creates;

        @Override
        public AISystemDataset create(AISystemDataset association) {
            calls.add("association");
            creates++;
            created = association;
            if (failure != null) {
                throw failure;
            }
            return association;
        }

        @Override
        public List<Dataset> findDatasetsByAISystemId(AISystemId aiSystemId) {
            throw new AssertionError("Association write use case must not list datasets");
        }
    }
}
