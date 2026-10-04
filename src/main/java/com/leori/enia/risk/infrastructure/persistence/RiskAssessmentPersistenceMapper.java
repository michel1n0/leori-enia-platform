package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.domain.ContextOfUse;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFinding;

class RiskAssessmentPersistenceMapper {

    RiskAssessmentJpaEntity toEntity(RiskAssessment assessment) {
        return new RiskAssessmentJpaEntity(
                assessment.id().value(),
                assessment.systemId().value(),
                assessment.contextOfUse().purpose(),
                assessment.contextOfUse().deploymentContext(),
                assessment.findings().stream()
                        .map(finding -> new RiskAssessmentFindingJpaEmbeddable(
                                finding.description(),
                                finding.likelihood(),
                                finding.impactMagnitude()))
                        .toList(),
                assessment.assessedAt()
        );
    }

    RiskAssessment toDomain(RiskAssessmentJpaEntity entity) {
        return RiskAssessment.rehydrate(
                new RiskAssessmentId(entity.id()),
                new AISystemId(entity.systemId()),
                new ContextOfUse(entity.purpose(), entity.deploymentContext()),
                entity.findings().stream()
                        .map(finding -> new RiskFinding(
                                finding.description(),
                                finding.likelihood(),
                                finding.impactMagnitude()))
                        .toList(),
                entity.assessedAt()
        );
    }
}
