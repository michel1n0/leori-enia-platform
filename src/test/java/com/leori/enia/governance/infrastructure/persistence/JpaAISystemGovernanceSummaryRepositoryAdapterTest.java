package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.AISystemGovernanceSummary;
import com.leori.enia.governance.domain.AISystemId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JpaAISystemGovernanceSummaryRepositoryAdapterTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query query = mock(Query.class);
    private final JpaAISystemGovernanceSummaryRepositoryAdapter adapter =
            new JpaAISystemGovernanceSummaryRepositoryAdapter(entityManager);

    @Test
    void native_query_result_maps_counts_and_preserves_system_id() {
        AISystemId id = AISystemId.generate();
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(1, id.value())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{
                id.value(),
                BigInteger.valueOf(1),
                Long.valueOf(2),
                Integer.valueOf(3),
                Short.valueOf((short) 4),
                BigDecimal.valueOf(5),
                BigInteger.valueOf(6),
                Long.valueOf(7),
                Integer.valueOf(8),
                BigInteger.valueOf(9),
                Long.valueOf(10),
                BigDecimal.valueOf(11)
        });

        AISystemGovernanceSummary summary = adapter.summarize(id);

        assertAll(
                () -> assertEquals(id, summary.aiSystemId()),
                () -> assertEquals(1, summary.riskAssessmentCount()),
                () -> assertEquals(2, summary.findingCount()),
                () -> assertEquals(3, summary.controlCount()),
                () -> assertEquals(4, summary.implementedControlCount()),
                () -> assertEquals(5, summary.controlImplementationCount()),
                () -> assertEquals(6, summary.evidenceCount()),
                () -> assertEquals(7, summary.findingsWithoutControls()),
                () -> assertEquals(8, summary.controlsWithoutImplementation()),
                () -> assertEquals(9, summary.implementationsWithoutEvidence()),
                () -> assertEquals(10, summary.registeredModelCount()),
                () -> assertEquals(11, summary.datasetCount())
        );
        verify(query).setParameter(1, id.value());
    }

    @Test
    void maps_string_uuid_projection_when_provider_does_not_return_uuid_instance() {
        AISystemId id = AISystemId.generate();
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(1, id.value())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{
                id.value().toString(), 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, BigInteger.ZERO
        });

        AISystemGovernanceSummary summary = adapter.summarize(id);

        assertAll(
                () -> assertEquals(id, summary.aiSystemId()),
                () -> assertEquals(0, summary.datasetCount())
        );
    }

    @Test
    void null_id_fails_before_accessing_persistence() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> adapter.summarize(null));

        assertEquals("AI system id is required", exception.getMessage());
        verifyNoInteractions(entityManager);
    }

    @Test
    void constructor_rejects_null_entity_manager() {
        assertThrows(NullPointerException.class, () -> new JpaAISystemGovernanceSummaryRepositoryAdapter(null));
    }
}
