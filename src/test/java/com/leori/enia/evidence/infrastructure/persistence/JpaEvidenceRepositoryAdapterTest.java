package com.leori.enia.evidence.infrastructure.persistence;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.evidence.domain.event.EvidenceRecorded;
import com.leori.enia.risk.domain.ControlImplementationId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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
    void lists_evidence_by_control_implementation_with_expected_query_order_and_mapping() {
        ControlImplementationId controlImplementationId = ControlImplementationId.generate();
        Evidence first = Evidence.rehydrate(
                EvidenceId.generate(), controlImplementationId, "Signed approval minutes", "evidence-vault:item-123",
                RECORDED_AT);
        Evidence second = Evidence.rehydrate(
                EvidenceId.generate(), controlImplementationId, "Monitoring report", "evidence-vault:item-456",
                RECORDED_AT.plusSeconds(60));
        EvidenceJpaEntity firstEntity = entity(first);
        EvidenceJpaEntity secondEntity = entity(second);
        TypedQuery<EvidenceJpaEntity> query = evidenceListQuery(List.of(firstEntity, secondEntity), controlImplementationId);

        List<Evidence> result = adapter.findByControlImplementationId(controlImplementationId);

        assertEquals(2, result.size());
        assertState(first, result.get(0));
        assertState(second, result.get(1));
        assertEquals(0, result.get(0).domainEvents().size());
        assertEquals(0, result.get(1).domainEvents().size());
        assertThrows(UnsupportedOperationException.class, () -> result.add(first));
        verify(entityManager).createQuery(expectedListJpql(), EvidenceJpaEntity.class);
        verify(query).setParameter("controlImplementationId", controlImplementationId.value());
        verify(query).getResultList();
        verifyNoMoreInteractions(entityManager, query);
    }

    @Test
    void list_by_control_implementation_returns_empty_result() {
        ControlImplementationId controlImplementationId = ControlImplementationId.generate();
        TypedQuery<EvidenceJpaEntity> query = evidenceListQuery(List.of(), controlImplementationId);

        List<Evidence> result = adapter.findByControlImplementationId(controlImplementationId);

        assertTrue(result.isEmpty());
        verify(entityManager).createQuery(expectedListJpql(), EvidenceJpaEntity.class);
        verify(query).setParameter("controlImplementationId", controlImplementationId.value());
        verify(query).getResultList();
        verifyNoMoreInteractions(entityManager, query);
    }

    @Test
    void rejects_null_control_implementation_id_before_accessing_persistence() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> adapter.findByControlImplementationId(null));

        assertEquals("Control implementation id is required", exception.getMessage());
        verifyNoInteractions(entityManager);
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

    private EvidenceJpaEntity entity(Evidence evidence) {
        return new EvidenceJpaEntity(
                evidence.id().value(),
                evidence.controlImplementationId().value(),
                evidence.description(),
                evidence.reference(),
                evidence.recordedAt()
        );
    }

    @SuppressWarnings("unchecked")
    private TypedQuery<EvidenceJpaEntity> evidenceListQuery(
            List<EvidenceJpaEntity> rows,
            ControlImplementationId controlImplementationId
    ) {
        TypedQuery<EvidenceJpaEntity> query = mock(TypedQuery.class);
        when(entityManager.createQuery(expectedListJpql(), EvidenceJpaEntity.class)).thenReturn(query);
        when(query.setParameter("controlImplementationId", controlImplementationId.value())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);
        return query;
    }

    private String expectedListJpql() {
        return """
                select evidence
                from EvidenceJpaEntity evidence
                where evidence.controlImplementationId = :controlImplementationId
                order by evidence.recordedAt asc, evidence.id asc
                """;
    }

    private void assertState(Evidence expected, Evidence actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.controlImplementationId(), actual.controlImplementationId());
        assertEquals(expected.description(), actual.description());
        assertEquals(expected.reference(), actual.reference());
        assertEquals(expected.recordedAt(), actual.recordedAt());
    }
}
