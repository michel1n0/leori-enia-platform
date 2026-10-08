package com.leori.enia.governance.infrastructure.web;

import com.leori.enia.governance.application.AISystemGovernanceGaps;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AISystemGovernanceGapsResponse(
        UUID aiSystemId,
        List<FindingWithoutControlResponse> findingsWithoutControls,
        List<ControlWithoutImplementationResponse> controlsWithoutImplementation,
        List<ImplementationWithoutEvidenceResponse> implementationsWithoutEvidence
) {
    static AISystemGovernanceGapsResponse from(AISystemGovernanceGaps gaps) {
        return new AISystemGovernanceGapsResponse(
                gaps.aiSystemId().value(),
                gaps.findingsWithoutControls().stream()
                        .map(FindingWithoutControlResponse::from)
                        .toList(),
                gaps.controlsWithoutImplementation().stream()
                        .map(ControlWithoutImplementationResponse::from)
                        .toList(),
                gaps.implementationsWithoutEvidence().stream()
                        .map(ImplementationWithoutEvidenceResponse::from)
                        .toList()
        );
    }

    public record FindingWithoutControlResponse(
            UUID riskAssessmentId,
            UUID riskFindingId,
            String description,
            Likelihood likelihood,
            ImpactMagnitude impactMagnitude
    ) {
        static FindingWithoutControlResponse from(AISystemGovernanceGaps.FindingWithoutControl finding) {
            return new FindingWithoutControlResponse(
                    finding.riskAssessmentId().value(),
                    finding.riskFindingId().value(),
                    finding.description(),
                    finding.likelihood(),
                    finding.impactMagnitude()
            );
        }
    }

    public record ControlWithoutImplementationResponse(
            UUID riskAssessmentId,
            UUID riskFindingId,
            UUID controlId,
            String name,
            String description
    ) {
        static ControlWithoutImplementationResponse from(AISystemGovernanceGaps.ControlWithoutImplementation control) {
            return new ControlWithoutImplementationResponse(
                    control.riskAssessmentId().value(),
                    control.riskFindingId().value(),
                    control.controlId().value(),
                    control.name(),
                    control.description()
            );
        }
    }

    public record ImplementationWithoutEvidenceResponse(
            UUID controlId,
            UUID controlImplementationId,
            String description,
            Instant implementedAt
    ) {
        static ImplementationWithoutEvidenceResponse from(
                AISystemGovernanceGaps.ImplementationWithoutEvidence implementation
        ) {
            return new ImplementationWithoutEvidenceResponse(
                    implementation.controlId().value(),
                    implementation.controlImplementationId().value(),
                    implementation.description(),
                    implementation.implementedAt()
            );
        }
    }
}
