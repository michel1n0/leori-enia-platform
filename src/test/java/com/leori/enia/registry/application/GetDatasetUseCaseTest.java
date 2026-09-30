package com.leori.enia.registry.application;

import com.leori.enia.registry.application.exception.DatasetNotFoundException;
import com.leori.enia.registry.application.port.DatasetRepository;
import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetDatasetUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-29T10:00:00Z");

    @Test
    void returns_existing_dataset() {
        Dataset dataset = dataset();
        InMemoryDatasetRepository repository = new InMemoryDatasetRepository(dataset);
        GetDatasetUseCase useCase = new GetDatasetUseCase(repository);

        DatasetId requestedId = dataset.id();

        Dataset result = useCase.execute(requestedId);

        assertSame(dataset, result);
        assertEquals(requestedId, repository.lastFindId);
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void missing_dataset_fails() {
        InMemoryDatasetRepository repository = new InMemoryDatasetRepository();
        GetDatasetUseCase useCase = new GetDatasetUseCase(repository);
        DatasetId missingId = DatasetId.generate();

        DatasetNotFoundException exception = assertThrows(
                DatasetNotFoundException.class,
                () -> useCase.execute(missingId)
        );

        assertEquals("Dataset not found: " + missingId, exception.getMessage());
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void null_id_fails_before_repository_access() {
        InMemoryDatasetRepository repository = new InMemoryDatasetRepository();
        GetDatasetUseCase useCase = new GetDatasetUseCase(repository);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Dataset id is required", exception.getMessage());
        assertEquals(0, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void read_does_not_write_or_mutate_events() {
        Dataset dataset = dataset();
        var events = dataset.domainEvents();
        InMemoryDatasetRepository repository = new InMemoryDatasetRepository(dataset);
        GetDatasetUseCase useCase = new GetDatasetUseCase(repository);

        useCase.execute(dataset.id());

        assertEquals(0, repository.creates);
        assertEquals(events, dataset.domainEvents());
    }

    private Dataset dataset() {
        return Dataset.builder()
                .id(DatasetId.generate())
                .name("Training data")
                .description("Curated observations")
                .createdAt(CREATED_AT)
                .build();
    }

    private static final class InMemoryDatasetRepository implements DatasetRepository {
        private final Map<DatasetId, Dataset> datasets = new HashMap<>();
        private DatasetId lastFindId;
        private int finds;
        private int creates;

        private InMemoryDatasetRepository(Dataset... datasets) {
            for (Dataset dataset : datasets) {
                this.datasets.put(dataset.id(), dataset);
            }
        }

        @Override
        public Dataset create(Dataset dataset) {
            creates++;
            throw new AssertionError("Read use case must not create a dataset");
        }

        @Override
        public Optional<Dataset> findById(DatasetId id) {
            finds++;
            lastFindId = id;
            return Optional.ofNullable(datasets.get(id));
        }
    }
}
