package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.AISystemGovernanceGaps;
import com.leori.enia.governance.application.AISystemGovernanceGaps.ControlWithoutImplementation;
import com.leori.enia.governance.application.AISystemGovernanceGaps.FindingWithoutControl;
import com.leori.enia.governance.application.AISystemGovernanceGaps.ImplementationWithoutEvidence;
import com.leori.enia.governance.application.port.AISystemGovernanceGapsRepository;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;
import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Transaction advice is supplied by GovernancePersistenceConfiguration. */
public final class JpaAISystemGovernanceGapsRepositoryAdapter implements AISystemGovernanceGapsRepository {

    private static final String FINDINGS_WITHOUT_CONTROLS_SQL = """
            select
                f.risk_assessment_id,
                f.id,
                f.description,
                f.likelihood,
                f.impact_magnitude
            from risk_assessment_findings f
            join risk_assessments ra
              on ra.id = f.risk_assessment_id
            where ra.system_id = ?
              and not exists (
                  select 1
                  from controls c
                  where c.risk_assessment_id = f.risk_assessment_id
                    and c.risk_finding_id = f.id
              )
            order by
                ra.assessed_at asc,
                f.risk_assessment_id asc,
                f.position asc,
                f.id asc
            """;

    private static final String CONTROLS_WITHOUT_IMPLEMENTATION_SQL = """
            select
                c.risk_assessment_id,
                c.risk_finding_id,
                c.id,
                c.name,
                c.description
            from controls c
            join risk_assessments ra
              on ra.id = c.risk_assessment_id
            join risk_assessment_findings f
              on f.risk_assessment_id = c.risk_assessment_id
             and f.id = c.risk_finding_id
            where ra.system_id = ?
              and not exists (
                  select 1
                  from control_implementations ci
                  where ci.control_id = c.id
              )
            order by
                ra.assessed_at asc,
                c.risk_assessment_id asc,
                f.position asc,
                c.risk_finding_id asc,
                c.created_at asc,
                c.id asc
            """;

    private static final String IMPLEMENTATIONS_WITHOUT_EVIDENCE_SQL = """
            select
                ci.control_id,
                ci.id,
                ci.description,
                ci.implemented_at
            from control_implementations ci
            join controls c
              on c.id = ci.control_id
            join risk_assessments ra
              on ra.id = c.risk_assessment_id
            join risk_assessment_findings f
              on f.risk_assessment_id = c.risk_assessment_id
             and f.id = c.risk_finding_id
            where ra.system_id = ?
              and not exists (
                  select 1
                  from evidence e
                  where e.control_implementation_id = ci.id
              )
            order by
                ra.assessed_at asc,
                c.risk_assessment_id asc,
                f.position asc,
                c.risk_finding_id asc,
                c.created_at asc,
                ci.control_id asc,
                ci.implemented_at asc,
                ci.id asc
            """;

    private final EntityManager entityManager;

    public JpaAISystemGovernanceGapsRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "Entity manager is required");
    }

    @Override
    public AISystemGovernanceGaps findByAISystemId(AISystemId aiSystemId) {
        Objects.requireNonNull(aiSystemId, "AI system id is required");
        return new AISystemGovernanceGaps(
                aiSystemId,
                findingsWithoutControls(aiSystemId),
                controlsWithoutImplementation(aiSystemId),
                implementationsWithoutEvidence(aiSystemId)
        );
    }

    private List<FindingWithoutControl> findingsWithoutControls(AISystemId aiSystemId) {
        return entityManager.createNativeQuery(FINDINGS_WITHOUT_CONTROLS_SQL)
                .setParameter(1, aiSystemId.value())
                .getResultList()
                .stream()
                .map(JpaAISystemGovernanceGapsRepositoryAdapter::findingWithoutControl)
                .toList();
    }

    private List<ControlWithoutImplementation> controlsWithoutImplementation(AISystemId aiSystemId) {
        return entityManager.createNativeQuery(CONTROLS_WITHOUT_IMPLEMENTATION_SQL)
                .setParameter(1, aiSystemId.value())
                .getResultList()
                .stream()
                .map(JpaAISystemGovernanceGapsRepositoryAdapter::controlWithoutImplementation)
                .toList();
    }

    private List<ImplementationWithoutEvidence> implementationsWithoutEvidence(AISystemId aiSystemId) {
        return entityManager.createNativeQuery(IMPLEMENTATIONS_WITHOUT_EVIDENCE_SQL)
                .setParameter(1, aiSystemId.value())
                .getResultList()
                .stream()
                .map(JpaAISystemGovernanceGapsRepositoryAdapter::implementationWithoutEvidence)
                .toList();
    }

    private static FindingWithoutControl findingWithoutControl(Object value) {
        Object[] row = (Object[]) value;
        return new FindingWithoutControl(
                new RiskAssessmentId(uuid(row[0])),
                new RiskFindingId(uuid(row[1])),
                row[2].toString(),
                Likelihood.valueOf(row[3].toString()),
                ImpactMagnitude.valueOf(row[4].toString())
        );
    }

    private static ControlWithoutImplementation controlWithoutImplementation(Object value) {
        Object[] row = (Object[]) value;
        return new ControlWithoutImplementation(
                new RiskAssessmentId(uuid(row[0])),
                new RiskFindingId(uuid(row[1])),
                new ControlId(uuid(row[2])),
                row[3].toString(),
                row[4].toString()
        );
    }

    private static ImplementationWithoutEvidence implementationWithoutEvidence(Object value) {
        Object[] row = (Object[]) value;
        return new ImplementationWithoutEvidence(
                new ControlId(uuid(row[0])),
                new ControlImplementationId(uuid(row[1])),
                row[2].toString(),
                instant(row[3])
        );
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(value.toString());
    }
}
