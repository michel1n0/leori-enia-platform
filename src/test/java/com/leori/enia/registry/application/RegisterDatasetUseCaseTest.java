package com.leori.enia.registry.application;

import com.leori.enia.registry.application.port.DatasetRepository;
import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.event.DatasetRegistered;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RegisterDatasetUseCaseTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-29T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(REGISTERED_AT, ZoneOffset.UTC);

    @Test
    void registers_dataset_with_domain_normalization_and_one_repository_call() {
        RecordingDatasetRepository repository = new RecordingDatasetRepository();
        RegisterDatasetUseCase useCase = new RegisterDatasetUseCase(repository, CLOCK);

        Dataset result = useCase.execute(new RegisterDatasetCommand(
                "  Training data  ", "  Curated observations  "));

        assertSame(repository.createdDataset, result);
        assertNotNull(result.id());
        assertNotNull(result.id().value());
        assertEquals("Training data", result.name());
        assertEquals("Curated observations", result.description());
        assertEquals(REGISTERED_AT, result.createdAt());
        assertEquals(1, repository.creates);
    }

    @Test
    void generates_a_new_dataset_id_per_call() {
        RecordingDatasetRepository repository = new RecordingDatasetRepository();
        RegisterDatasetUseCase useCase = new RegisterDatasetUseCase(repository, CLOCK);

        Dataset first = useCase.execute(new RegisterDatasetCommand("Dataset", "Description"));
        Dataset second = useCase.execute(new RegisterDatasetCommand("Dataset", "Description"));

        assertNotEquals(first.id(), second.id());
        assertEquals(2, repository.creates);
    }

    @Test
    void timestamp_comes_from_supplied_clock() {
        RecordingDatasetRepository repository = new RecordingDatasetRepository();
        Instant expected = Instant.parse("2026-09-29T12:34:56.789Z");
        RegisterDatasetUseCase useCase = new RegisterDatasetUseCase(
                repository, Clock.fixed(expected, ZoneOffset.UTC));

        Dataset result = useCase.execute(new RegisterDatasetCommand("Dataset", "Description"));

        assertEquals(expected, result.createdAt());
    }

    @Test
    void preserves_pending_registration_event() {
        RecordingDatasetRepository repository = new RecordingDatasetRepository();
        RegisterDatasetUseCase useCase = new RegisterDatasetUseCase(repository, CLOCK);

        Dataset result = useCase.execute(new RegisterDatasetCommand("Dataset", "Description"));

        assertEquals(1, result.domainEvents().size());
        DatasetRegistered event = assertInstanceOf(DatasetRegistered.class, result.domainEvents().getFirst());
        assertSame(repository.pendingEvent, event);
        assertEquals(result.id(), event.datasetId());
        assertEquals(result.createdAt(), event.occurredAt());
    }

    @Test
    void null_command_fails_before_repository_or_clock_access() {
        DatasetRepository repository = mock(DatasetRepository.class);
        Clock clock = mock(Clock.class);
        RegisterDatasetUseCase useCase = new RegisterDatasetUseCase(repository, clock);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Register dataset command is required", exception.getMessage());
        verifyNoInteractions(repository, clock);
    }

    @Test
    void invalid_domain_input_propagates_without_persistence() {
        DatasetRepository repository = mock(DatasetRepository.class);
        RegisterDatasetUseCase useCase = new RegisterDatasetUseCase(repository, CLOCK);

        for (String invalid : new String[]{null, "", "   "}) {
            IllegalArgumentException nameFailure = assertThrows(IllegalArgumentException.class,
                    () -> useCase.execute(new RegisterDatasetCommand(invalid, "Description")));
            assertEquals("Name is required", nameFailure.getMessage());

            IllegalArgumentException descriptionFailure = assertThrows(IllegalArgumentException.class,
                    () -> useCase.execute(new RegisterDatasetCommand("Dataset", invalid)));
            assertEquals("Description is required", descriptionFailure.getMessage());
        }

        verifyNoInteractions(repository);
    }

    @Test
    void repository_failure_propagates_unchanged() {
        RecordingDatasetRepository repository = new RecordingDatasetRepository();
        RuntimeException failure = new RuntimeException("Storage unavailable");
        repository.failure = failure;
        RegisterDatasetUseCase useCase = new RegisterDatasetUseCase(repository, CLOCK);

        assertSame(failure, assertThrows(RuntimeException.class,
                () -> useCase.execute(new RegisterDatasetCommand("Dataset", "Description"))));
        assertEquals(1, repository.creates);
    }

    private static final class RecordingDatasetRepository implements DatasetRepository {
        private Dataset createdDataset;
        private DatasetRegistered pendingEvent;
        private RuntimeException failure;
        private int creates;

        @Override
        public Dataset create(Dataset dataset) {
            creates++;
            createdDataset = dataset;
            pendingEvent = assertInstanceOf(DatasetRegistered.class, dataset.domainEvents().getFirst());
            if (failure != null) {
                throw failure;
            }
            return dataset;
        }

        @Override
        public Optional<Dataset> findById(com.leori.enia.registry.domain.DatasetId id) {
            throw new AssertionError("Register use case must not find a dataset");
        }
    }
}
