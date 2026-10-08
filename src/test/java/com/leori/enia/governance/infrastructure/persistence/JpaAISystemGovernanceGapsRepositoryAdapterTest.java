package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.AISystemGovernanceGaps;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JpaAISystemGovernanceGapsRepositoryAdapterTest {

    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-06T10:15:30.123456Z");

    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query findingsQuery = mock(Query.class);
    private final Query controlsQuery = mock(Query.class);
    private final Query implementationsQuery = mock(Query.class);
    private final JpaAISystemGovernanceGapsRepositoryAdapter adapter =
            new JpaAISystemGovernanceGapsRepositoryAdapter(entityManager);

    @Test
    void native_rows_map_all_gap_item_types_and_preserve_values() {
        AISystemId id = AISystemId.generate();
        UUID assessmentId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        UUID controlId = UUID.randomUUID();
        UUID implementationId = UUID.randomUUID();
        prepareQueries(id);
        when(findingsQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{
                assessmentId,
                findingId,
                "Uncontrolled model drift",
                "HIGH",
                "MEDIUM"
        }));
        when(controlsQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{
                assessmentId,
                findingId,
                controlId,
                "Review gate",
                "Human review before deployment"
        }));
        when(implementationsQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{
                controlId,
                implementationId,
                "Implemented review workflow",
                Timestamp.from(IMPLEMENTED_AT)
        }));

        AISystemGovernanceGaps gaps = adapter.findByAISystemId(id);

        var finding = gaps.findingsWithoutControls().getFirst();
        var control = gaps.controlsWithoutImplementation().getFirst();
        var implementation = gaps.implementationsWithoutEvidence().getFirst();
        assertAll(
                () -> assertEquals(id, gaps.aiSystemId()),
                () -> assertEquals(assessmentId, finding.riskAssessmentId().value()),
                () -> assertEquals(findingId, finding.riskFindingId().value()),
                () -> assertEquals("Uncontrolled model drift", finding.description()),
                () -> assertEquals(Likelihood.HIGH, finding.likelihood()),
                () -> assertEquals(ImpactMagnitude.MEDIUM, finding.impactMagnitude()),
                () -> assertEquals(assessmentId, control.riskAssessmentId().value()),
                () -> assertEquals(findingId, control.riskFindingId().value()),
                () -> assertEquals(controlId, control.controlId().value()),
                () -> assertEquals("Review gate", control.name()),
                () -> assertEquals("Human review before deployment", control.description()),
                () -> assertEquals(controlId, implementation.controlId().value()),
                () -> assertEquals(implementationId, implementation.controlImplementationId().value()),
                () -> assertEquals("Implemented review workflow", implementation.description()),
                () -> assertEquals(IMPLEMENTED_AT, implementation.implementedAt())
        );
        verify(findingsQuery).setParameter(1, id.value());
        verify(controlsQuery).setParameter(1, id.value());
        verify(implementationsQuery).setParameter(1, id.value());
    }

    @Test
    void empty_query_results_return_empty_lists() {
        AISystemId id = AISystemId.generate();
        prepareQueries(id);
        when(findingsQuery.getResultList()).thenReturn(List.of());
        when(controlsQuery.getResultList()).thenReturn(List.of());
        when(implementationsQuery.getResultList()).thenReturn(List.of());

        AISystemGovernanceGaps gaps = adapter.findByAISystemId(id);

        assertAll(
                () -> assertEquals(List.of(), gaps.findingsWithoutControls()),
                () -> assertEquals(List.of(), gaps.controlsWithoutImplementation()),
                () -> assertEquals(List.of(), gaps.implementationsWithoutEvidence())
        );
    }

    @Test
    void maps_string_uuid_projection_when_provider_does_not_return_uuid_instance() {
        AISystemId id = AISystemId.generate();
        UUID assessmentId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        UUID controlId = UUID.randomUUID();
        UUID implementationId = UUID.randomUUID();
        prepareQueries(id);
        when(findingsQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{
                assessmentId.toString(), findingId.toString(), "Finding", "LOW", "HIGH"
        }));
        when(controlsQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{
                assessmentId.toString(), findingId.toString(), controlId.toString(), "Control", "Description"
        }));
        when(implementationsQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{
                controlId.toString(), implementationId.toString(), "Implementation", IMPLEMENTED_AT
        }));

        AISystemGovernanceGaps gaps = adapter.findByAISystemId(id);

        assertAll(
                () -> assertEquals(assessmentId, gaps.findingsWithoutControls().getFirst().riskAssessmentId().value()),
                () -> assertEquals(findingId, gaps.findingsWithoutControls().getFirst().riskFindingId().value()),
                () -> assertEquals(controlId, gaps.controlsWithoutImplementation().getFirst().controlId().value()),
                () -> assertEquals(implementationId,
                        gaps.implementationsWithoutEvidence().getFirst().controlImplementationId().value())
        );
    }

    @Test
    void each_sql_uses_not_exists_filters_and_deterministic_ordering() {
        AISystemId id = AISystemId.generate();
        prepareQueries(id);
        when(findingsQuery.getResultList()).thenReturn(List.of());
        when(controlsQuery.getResultList()).thenReturn(List.of());
        when(implementationsQuery.getResultList()).thenReturn(List.of());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        adapter.findByAISystemId(id);

        verify(entityManager, times(3)).createNativeQuery(sql.capture());
        List<String> statements = sql.getAllValues();
        assertAll(
                () -> assertTrue(statements.get(0).contains("not exists")),
                () -> assertTrue(statements.get(0).contains("order by\n    ra.assessed_at asc")),
                () -> assertTrue(statements.get(0).contains("f.position asc")),
                () -> assertTrue(statements.get(0).contains("f.id asc")),
                () -> assertTrue(statements.get(1).contains("not exists")),
                () -> assertTrue(statements.get(1).contains("order by\n    ra.assessed_at asc")),
                () -> assertTrue(statements.get(1).contains("c.created_at asc")),
                () -> assertTrue(statements.get(1).contains("c.id asc")),
                () -> assertTrue(statements.get(2).contains("not exists")),
                () -> assertTrue(statements.get(2).contains("order by\n    ra.assessed_at asc")),
                () -> assertTrue(statements.get(2).contains("ci.implemented_at asc")),
                () -> assertTrue(statements.get(2).contains("ci.id asc"))
        );
    }

    @Test
    void null_id_fails_before_accessing_persistence() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> adapter.findByAISystemId(null));

        assertEquals("AI system id is required", exception.getMessage());
        verifyNoInteractions(entityManager);
    }

    @Test
    void constructor_rejects_null_entity_manager() {
        assertThrows(NullPointerException.class, () -> new JpaAISystemGovernanceGapsRepositoryAdapter(null));
    }

    private void prepareQueries(AISystemId id) {
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(findingsQuery, controlsQuery, implementationsQuery);
        when(findingsQuery.setParameter(1, id.value())).thenReturn(findingsQuery);
        when(controlsQuery.setParameter(1, id.value())).thenReturn(controlsQuery);
        when(implementationsQuery.setParameter(1, id.value())).thenReturn(implementationsQuery);
    }
}
