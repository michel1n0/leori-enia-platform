package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.domain.AIModel;
import com.leori.enia.registry.domain.AIModelId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JpaAIModelRepositoryAdapterTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final JpaAIModelRepositoryAdapter adapter =
            new JpaAIModelRepositoryAdapter(entityManager, new AIModelPersistenceMapper());

    @Test
    void inserts_then_flushes_and_returns_same_aggregate_with_pending_event() {
        AIModel model = model();
        var events = model.domainEvents();

        assertSame(model, adapter.create(model));

        var calls = inOrder(entityManager);
        calls.verify(entityManager).persist(any(AIModelJpaEntity.class));
        calls.verify(entityManager).flush();
        calls.verifyNoMoreInteractions();
        assertEquals(1, events.size());
        assertEquals(events, model.domainEvents());
    }

    @Test
    void rejects_null_before_accessing_persistence() {
        assertThrows(NullPointerException.class, () -> adapter.create(null));
        verifyNoInteractions(entityManager);
    }

    @Test
    void propagates_persist_failure_unchanged() {
        PersistenceException failure = new PersistenceException("Storage unavailable");
        doThrow(failure).when(entityManager).persist(any(AIModelJpaEntity.class));

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(model())));
    }

    @Test
    void propagates_flush_failure_unchanged() {
        PersistenceException failure = new PersistenceException("Storage unavailable");
        doThrow(failure).when(entityManager).flush();

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(model())));
    }

    @Test
    void find_by_id_returns_rehydrated_model_without_events() {
        AIModel persisted = model();
        when(entityManager.find(AIModelJpaEntity.class, persisted.id().value()))
                .thenReturn(new AIModelPersistenceMapper().toEntity(persisted));

        var result = adapter.findById(persisted.id());

        assertTrue(result.isPresent());
        assertState(persisted, result.orElseThrow());
        assertTrue(result.orElseThrow().domainEvents().isEmpty());
    }

    @Test
    void find_by_id_returns_empty_when_missing() {
        AIModelId missingId = AIModelId.generate();
        when(entityManager.find(AIModelJpaEntity.class, missingId.value())).thenReturn(null);

        assertFalse(adapter.findById(missingId).isPresent());
    }

    private void assertState(AIModel expected, AIModel actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.systemId(), actual.systemId());
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.description(), actual.description());
        assertEquals(expected.provider(), actual.provider());
        assertEquals(expected.createdAt(), actual.createdAt());
    }

    private AIModel model() {
        return AIModel.builder()
                .id(AIModelId.generate())
                .systemId(AISystemId.generate())
                .name("Model")
                .description("Description")
                .provider("OpenAI")
                .createdAt(Instant.parse("2026-09-28T14:00:00.123456Z"))
                .build();
    }
}
