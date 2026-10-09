package com.leori.enia.governance.application;

import com.leori.enia.governance.domain.AISystemId;

public record AISystemGovernanceSummary(
        AISystemId aiSystemId,
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
}
