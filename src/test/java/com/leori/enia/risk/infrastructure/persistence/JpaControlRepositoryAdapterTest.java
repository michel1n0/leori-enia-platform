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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
}
