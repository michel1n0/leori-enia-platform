package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JpaDatasetRepositoryAdapterTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final JpaDatasetRepositoryAdapter adapter =
            new JpaDatasetRepositoryAdapter(entityManager, new DatasetPersistenceMapper());

    @Test
    void persists_then_flushes_and_returns_same_aggregate_without_consuming_events() {
        Dataset dataset = dataset();
        var events = dataset.domainEvents();

        assertSame(dataset, adapter.create(dataset));

        var calls = inOrder(entityManager);
        calls.verify(entityManager).persist(any(DatasetJpaEntity.class));
        calls.verify(entityManager).flush();
        calls.verifyNoMoreInteractions();
        assertEquals(1, events.size());
        assertEquals(events, dataset.domainEvents());
    }

    @Test
    void rejects_null_before_accessing_persistence() {
        assertThrows(NullPointerException.class, () -> adapter.create(null));
        verifyNoInteractions(entityManager);
    }

    @Test
    void propagates_persist_failure_unchanged() {
        PersistenceException failure = new PersistenceException("Storage unavailable");
        doThrow(failure).when(entityManager).persist(any(DatasetJpaEntity.class));

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(dataset())));
    }

    @Test
    void propagates_flush_failure_unchanged() {
        PersistenceException failure = new PersistenceException("Storage unavailable");
        doThrow(failure).when(entityManager).flush();

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(dataset())));
    }

    private Dataset dataset() {
        return Dataset.builder()
                .id(DatasetId.generate())
                .name("Dataset")
                .description("Description")
                .createdAt(Instant.parse("2026-09-28T14:00:00.123456Z"))
                .build();
    }
}
