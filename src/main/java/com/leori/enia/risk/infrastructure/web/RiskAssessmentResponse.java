package com.leori.enia.risk.infrastructure.web;

import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RiskAssessmentResponse(
        UUID id,
        UUID systemId,
        String purpose,
        String deploymentContext,
        List<RiskFindingResponse> findings,
        Instant assessedAt
) {
    static RiskAssessmentResponse from(RiskAssessment assessment) {
        return new RiskAssessmentResponse(
                assessment.id().value(),
                assessment.systemId().value(),
                assessment.contextOfUse().purpose(),
                assessment.contextOfUse().deploymentContext(),
                assessment.findings().stream()
                        .map(finding -> new RiskFindingResponse(
                                finding.description(),
                                finding.likelihood(),
                                finding.impactMagnitude()
                        ))
                        .toList(),
                assessment.assessedAt()
        );
    }

    public record RiskFindingResponse(
            String description,
            Likelihood likelihood,
            ImpactMagnitude impactMagnitude
    ) {
    }
}
