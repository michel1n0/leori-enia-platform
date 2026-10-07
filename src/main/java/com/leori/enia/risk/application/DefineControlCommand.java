package com.leori.enia.risk.application;

import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;

import java.util.Objects;

public record DefineControlCommand(
        RiskAssessmentId riskAssessmentId,
        RiskFindingId riskFindingId,
        String name,
        String description
) {

    public DefineControlCommand {
        Objects.requireNonNull(riskAssessmentId, "Risk assessment id is required");
        Objects.requireNonNull(riskFindingId, "Risk finding id is required");
    }
}
