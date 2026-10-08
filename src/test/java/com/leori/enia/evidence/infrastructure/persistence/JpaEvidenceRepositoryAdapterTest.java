package com.leori.enia.evidence.infrastructure.persistence;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.evidence.domain.event.EvidenceRecorded;
import com.leori.enia.risk.domain.ControlImplementationId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class JpaEvidenceRepositoryAdapterTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T12:30:45.123456Z");

    private final EntityManager entityManager = mock(EntityManager.class);
    private final JpaEvidenceRepositoryAdapter adapter =
            new JpaEvidenceRepositoryAdapter(entityManager, new EvidencePersistenceMapper());

    @Test
    void persists_flushes_and_returns_same_aggregate_without_consuming_events() {
        Evidence evidence = evidence();
        var events = evidence.domainEvents();

        Evidence result = adapter.create(evidence);

        assertSame(evidence, result);
        var calls = inOrder(entityManager);
        calls.verify(entityManager).persist(any(EvidenceJpaEntity.class));
        calls.verify(entityManager).flush();
        calls.verifyNoMoreInteractions();
        assertEquals(1, events.size());
        assertInstanceOf(EvidenceRecorded.class, events.getFirst());
        assertEquals(events, evidence.domainEvents());
        assertState(evidence, result);
    }

    @Test
    void finds_existing_evidence_with_all_fields_without_emitting_events_or_mutating() {
        Evidence expected = Evidence.rehydrate(
                EvidenceId.generate(),
                ControlImplementationId.generate(),
                "Signed approval minutes",
                "evidence-vault:item-123",
                RECORDED_AT
        );
        EvidenceJpaEntity entity = new EvidenceJpaEntity(
                expected.id().value(),
                expected.controlImplementationId().value(),
                expected.description(),
                expected.reference(),
                expected.recordedAt()
        );
        when(entityManager.find(EvidenceJpaEntity.class, expected.id().value())).thenReturn(entity);

        var result = adapter.findById(expected.id());

        assertTrue(result.isPresent());
        assertState(expected, result.orElseThrow());
        assertEquals(0, result.orElseThrow().domainEvents().size());
        verify(entityManager).find(EvidenceJpaEntity.class, expected.id().value());
        verifyNoMoreInteractions(entityManager);
    }

    @Test
    void missing_evidence_returns_empty() {
        EvidenceId id = EvidenceId.generate();
        when(entityManager.find(EvidenceJpaEntity.class, id.value())).thenReturn(null);

        var result = adapter.findById(id);

        assertFalse(result.isPresent());
        verify(entityManager).find(EvidenceJpaEntity.class, id.value());
        verifyNoMoreInteractions(entityManager);
    }

    @Test
    void rejects_null_find_id_before_accessing_persistence() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> adapter.findById(null));

        assertEquals("Evidence id is required", exception.getMessage());
        verifyNoInteractions(entityManager);
    }

    @Test
    void rejects_null_aggregate_before_accessing_persistence() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> adapter.create(null));

        assertEquals("Evidence is required", exception.getMessage());
        verifyNoInteractions(entityManager);
    }

    private Evidence evidence() {
        return Evidence.builder()
                .id(EvidenceId.generate())
                .controlImplementationId(ControlImplementationId.generate())
                .description("Signed approval minutes")
                .reference("evidence-vault:item-123")
                .recordedAt(RECORDED_AT)
                .build();
    }

    private void assertState(Evidence expected, Evidence actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.controlImplementationId(), actual.controlImplementationId());
        assertEquals(expected.description(), actual.description());
        assertEquals(expected.reference(), actual.reference());
        assertEquals(expected.recordedAt(), actual.recordedAt());
    }
}
