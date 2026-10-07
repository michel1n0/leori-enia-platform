package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JpaControlRepositoryAdapterTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final JpaControlRepositoryAdapter adapter =
            new JpaControlRepositoryAdapter(entityManager, new ControlPersistenceMapper());

    @Test
    void persists_flushes_and_returns_same_aggregate_without_consuming_events() {
        Control control = control();
        var events = control.domainEvents();

        assertSame(control, adapter.create(control));

        var calls = inOrder(entityManager);
        calls.verify(entityManager).persist(any(ControlJpaEntity.class));
        calls.verify(entityManager).flush();
        calls.verifyNoMoreInteractions();
        assertEquals(1, events.size());
        assertEquals(events, control.domainEvents());
    }

    @Test
    void finds_existing_control_by_id_through_entity_manager_find_and_mapper_rehydrates_without_events() {
        Control control = control();
        ControlJpaEntity entity = new ControlPersistenceMapper().toEntity(control);
        when(entityManager.find(ControlJpaEntity.class, control.id().value())).thenReturn(entity);

        var result = adapter.findById(control.id());

        assertTrue(result.isPresent());
        assertState(control, result.get());
        assertTrue(result.get().domainEvents().isEmpty());
        verify(entityManager).find(ControlJpaEntity.class, control.id().value());
    }

    @Test
    void missing_control_returns_empty_optional() {
        ControlId id = ControlId.generate();
        when(entityManager.find(ControlJpaEntity.class, id.value())).thenReturn(null);

        var result = adapter.findById(id);

        assertTrue(result.isEmpty());
        verify(entityManager).find(ControlJpaEntity.class, id.value());
    }

    @Test
    void rejects_null_find_id_before_accessing_persistence() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> adapter.findById(null));

        assertEquals("Control id is required", exception.getMessage());
        verifyNoInteractions(entityManager);
    }

    @Test
    void rejects_null_create_before_accessing_persistence() {
        assertThrows(NullPointerException.class, () -> adapter.create(null));
        verifyNoInteractions(entityManager);
    }

    @Test
    void propagates_persistence_failure_unchanged() {
        RuntimeException failure = new RuntimeException("Storage unavailable");
        doThrow(failure).when(entityManager).flush();

        assertSame(failure, assertThrows(RuntimeException.class, () -> adapter.create(control())));
    }

    private Control control() {
        return Control.builder()
                .id(ControlId.generate())
                .riskAssessmentId(RiskAssessmentId.generate())
                .riskFindingId(RiskFindingId.generate())
                .name("Human review gate")
                .description("Require documented human approval before deployment.")
                .createdAt(Instant.parse("2026-10-02T10:15:30.123456Z"))
                .build();
    }

    private void assertState(Control expected, Control actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.riskAssessmentId(), actual.riskAssessmentId());
        assertEquals(expected.riskFindingId(), actual.riskFindingId());
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.description(), actual.description());
        assertEquals(expected.createdAt(), actual.createdAt());
    }
}
