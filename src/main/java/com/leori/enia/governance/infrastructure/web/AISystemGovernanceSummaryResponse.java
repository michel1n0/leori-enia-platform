package com.leori.enia.governance.infrastructure.web;

import com.leori.enia.governance.application.AISystemGovernanceSummary;

import java.util.UUID;

public record AISystemGovernanceSummaryResponse(
        UUID aiSystemId,
        long riskAssessmentCount,
        long findingCount,
        long controlCount,
        long implementedControlCount,
        long controlImplementationCount,
        long evidenceCount,
        long findingsWithoutControls,
        long controlsWithoutImplementation,
        long implementationsWithoutEvidence,
        long registeredModelCount,
        long datasetCount
) {
    static AISystemGovernanceSummaryResponse from(AISystemGovernanceSummary summary) {
        return new AISystemGovernanceSummaryResponse(
                summary.aiSystemId().value(),
                summary.riskAssessmentCount(),
                summary.findingCount(),
                summary.controlCount(),
                summary.implementedControlCount(),
                summary.controlImplementationCount(),
                summary.evidenceCount(),
                summary.findingsWithoutControls(),
                summary.controlsWithoutImplementation(),
                summary.implementationsWithoutEvidence(),
                summary.registeredModelCount(),
                summary.datasetCount()
        );
    }
}
