package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.AISystemGovernanceSummary;
import com.leori.enia.governance.application.port.AISystemGovernanceSummaryRepository;
import com.leori.enia.governance.domain.AISystemId;
import jakarta.persistence.EntityManager;

import java.util.Objects;
import java.util.UUID;

/** Transaction advice is supplied by GovernancePersistenceConfiguration. */
public final class JpaAISystemGovernanceSummaryRepositoryAdapter implements AISystemGovernanceSummaryRepository {

    private static final String SQL = """
            select
                s.id as ai_system_id,
                (select count(*)
                 from risk_assessments ra
                 where ra.system_id = s.id) as risk_assessment_count,
                (select count(*)
                 from risk_assessment_findings f
                 where exists (
                     select 1 from risk_assessments ra
                     where ra.id = f.risk_assessment_id and ra.system_id = s.id
                 )) as finding_count,
                (select count(*)
                 from controls c
                 where exists (
                     select 1 from risk_assessments ra
                     where ra.id = c.risk_assessment_id and ra.system_id = s.id
                 )) as control_count,
                (select count(*)
                 from controls c
                 where exists (
                     select 1 from risk_assessments ra
                     where ra.id = c.risk_assessment_id and ra.system_id = s.id
                 ) and exists (
                     select 1 from control_implementations ci
                     where ci.control_id = c.id
                 )) as implemented_control_count,
                (select count(*)
                 from control_implementations ci
                 where exists (
                     select 1 from controls c
                     where c.id = ci.control_id and exists (
                         select 1 from risk_assessments ra
                         where ra.id = c.risk_assessment_id and ra.system_id = s.id
                     )
                 )) as control_implementation_count,
                (select count(*)
                 from evidence e
                 where exists (
                     select 1 from control_implementations ci
                     where ci.id = e.control_implementation_id and exists (
                         select 1 from controls c
                         where c.id = ci.control_id and exists (
                             select 1 from risk_assessments ra
                             where ra.id = c.risk_assessment_id and ra.system_id = s.id
                         )
                     )
                 )) as evidence_count,
                (select count(*)
                 from risk_assessment_findings f
                 where exists (
                     select 1 from risk_assessments ra
                     where ra.id = f.risk_assessment_id and ra.system_id = s.id
                 ) and not exists (
                     select 1 from controls c
                     where c.risk_assessment_id = f.risk_assessment_id
                         and c.risk_finding_id = f.id
                 )) as findings_without_controls,
                (select count(*)
                 from controls c
                 where exists (
                     select 1 from risk_assessments ra
                     where ra.id = c.risk_assessment_id and ra.system_id = s.id
                 ) and not exists (
                     select 1 from control_implementations ci
                     where ci.control_id = c.id
                 )) as controls_without_implementation,
                (select count(*)
                 from control_implementations ci
                 where exists (
                     select 1 from controls c
                     where c.id = ci.control_id and exists (
                         select 1 from risk_assessments ra
                         where ra.id = c.risk_assessment_id and ra.system_id = s.id
                     )
                 ) and not exists (
                     select 1 from evidence e
                     where e.control_implementation_id = ci.id
                 )) as implementations_without_evidence,
                (select count(*)
                 from ai_models m
                 where m.system_id = s.id) as registered_model_count,
                (select count(*)
                 from ai_system_datasets sd
                 where sd.system_id = s.id) as dataset_count
            from ai_systems s
            where s.id = ?
            """;

    private final EntityManager entityManager;

    public JpaAISystemGovernanceSummaryRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "Entity manager is required");
    }

    @Override
    public AISystemGovernanceSummary summarize(AISystemId aiSystemId) {
        Objects.requireNonNull(aiSystemId, "AI system id is required");
        Object[] row = (Object[]) entityManager.createNativeQuery(SQL)
                .setParameter(1, aiSystemId.value())
                .getSingleResult();
        return new AISystemGovernanceSummary(
                new AISystemId(uuid(row[0])),
                count(row[1]),
                count(row[2]),
                count(row[3]),
                count(row[4]),
                count(row[5]),
                count(row[6]),
                count(row[7]),
                count(row[8]),
                count(row[9]),
                count(row[10]),
                count(row[11])
        );
    }

    private static long count(Object value) {
        return ((Number) value).longValue();
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }
}
