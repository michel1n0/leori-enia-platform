package com.leori.enia.governance.application;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AISystemGovernanceGaps(
        AISystemId aiSystemId,
        List<FindingWithoutControl> findingsWithoutControls,
        List<ControlWithoutImplementation> controlsWithoutImplementation,
        List<ImplementationWithoutEvidence> implementationsWithoutEvidence
) {
    public AISystemGovernanceGaps {
        Objects.requireNonNull(aiSystemId, "AI system id is required");
        findingsWithoutControls = List.copyOf(Objects.requireNonNull(
                findingsWithoutControls,
                "Findings without controls are required"));
        controlsWithoutImplementation = List.copyOf(Objects.requireNonNull(
                controlsWithoutImplementation,
                "Controls without implementation are required"));
        implementationsWithoutEvidence = List.copyOf(Objects.requireNonNull(
                implementationsWithoutEvidence,
                "Implementations without evidence are required"));
    }

    public record FindingWithoutControl(
            RiskAssessmentId riskAssessmentId,
            RiskFindingId riskFindingId,
            String description,
            Likelihood likelihood,
            ImpactMagnitude impactMagnitude
    ) {
    }

    public record ControlWithoutImplementation(
            RiskAssessmentId riskAssessmentId,
            RiskFindingId riskFindingId,
            ControlId controlId,
            String name,
            String description
    ) {
    }

    public record ImplementationWithoutEvidence(
            ControlId controlId,
            ControlImplementationId controlImplementationId,
            String description,
            Instant implementedAt
    ) {
    }
}
