package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.domain.ContextOfUse;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFinding;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JpaRiskAssessmentRepositoryAdapterTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final JpaRiskAssessmentRepositoryAdapter adapter =
            new JpaRiskAssessmentRepositoryAdapter(entityManager, new RiskAssessmentPersistenceMapper());

    @Test
    void persists_then_flushes_and_returns_same_aggregate_without_consuming_events() {
        RiskAssessment assessment = assessment();
        var events = assessment.domainEvents();

        assertSame(assessment, adapter.create(assessment));

        var calls = inOrder(entityManager);
        calls.verify(entityManager).persist(any(RiskAssessmentJpaEntity.class));
        calls.verify(entityManager).flush();
        calls.verifyNoMoreInteractions();
        assertEquals(1, events.size());
        assertEquals(events, assessment.domainEvents());
    }

    @Test
    void rejects_null_before_accessing_persistence() {
        assertThrows(NullPointerException.class, () -> adapter.create(null));
        verifyNoInteractions(entityManager);
    }

    @Test
    void propagates_persist_failure_unchanged() {
        PersistenceException failure = new PersistenceException("Storage unavailable");
        doThrow(failure).when(entityManager).persist(any(RiskAssessmentJpaEntity.class));

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(assessment())));
    }

    @Test
    void propagates_flush_failure_unchanged() {
        PersistenceException failure = new PersistenceException("Storage unavailable");
        doThrow(failure).when(entityManager).flush();

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(assessment())));
    }

    private RiskAssessment assessment() {
        return RiskAssessment.builder()
                .id(RiskAssessmentId.generate())
                .systemId(AISystemId.generate())
                .contextOfUse(new ContextOfUse("Governance approval", "Public sector deployment"))
                .findings(List.of(new RiskFinding("Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH)))
                .assessedAt(Instant.parse("2026-10-01T14:00:00.123456Z"))
                .build();
    }
}
