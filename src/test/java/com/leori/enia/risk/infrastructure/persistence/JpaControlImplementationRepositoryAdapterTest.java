package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.event.ControlImplementationRecorded;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JpaControlImplementationRepositoryAdapterTest {

    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T12:30:45.123456Z");

    private final EntityManager entityManager = mock(EntityManager.class);
    private final JpaControlImplementationRepositoryAdapter adapter =
            new JpaControlImplementationRepositoryAdapter(entityManager, new ControlImplementationPersistenceMapper());

    @Test
    void persists_flushes_and_returns_same_aggregate_without_consuming_events() {
        ControlImplementation implementation = implementation();
        var events = implementation.domainEvents();

        ControlImplementation result = adapter.create(implementation);

        assertSame(implementation, result);
        var calls = inOrder(entityManager);
        calls.verify(entityManager).persist(any(ControlImplementationJpaEntity.class));
        calls.verify(entityManager).flush();
        calls.verifyNoMoreInteractions();
        assertEquals(1, events.size());
        assertInstanceOf(ControlImplementationRecorded.class, events.getFirst());
        assertEquals(events, implementation.domainEvents());
        assertState(implementation, result);
    }

    @Test
    void rejects_null_aggregate_before_accessing_persistence() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> adapter.create(null));

        assertEquals("Control implementation is required", exception.getMessage());
        verifyNoInteractions(entityManager);
    }

    private ControlImplementation implementation() {
        return ControlImplementation.builder()
                .id(ControlImplementationId.generate())
                .controlId(ControlId.generate())
                .description("Evidence package uploaded and reviewed.")
                .implementedAt(IMPLEMENTED_AT)
                .build();
    }

    private void assertState(ControlImplementation expected, ControlImplementation actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.controlId(), actual.controlId());
        assertEquals(expected.description(), actual.description());
        assertEquals(expected.implementedAt(), actual.implementedAt());
    }
}
