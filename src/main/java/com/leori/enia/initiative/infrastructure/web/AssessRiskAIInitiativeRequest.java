package com.leori.enia.initiative.infrastructure.web;

import com.leori.enia.initiative.domain.RiskLevel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AssessRiskAIInitiativeRequest(@NotNull RiskLevel riskLevel) {

    @AssertTrue(message = "Risk level must be LOW, MEDIUM or HIGH")
    public boolean isAssessedRiskLevel() {
        return riskLevel != RiskLevel.NOT_ASSESSED;
    }
}
