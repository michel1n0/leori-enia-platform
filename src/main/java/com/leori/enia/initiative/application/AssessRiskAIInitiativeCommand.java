package com.leori.enia.initiative.application;

import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.RiskLevel;

public record AssessRiskAIInitiativeCommand(
        AIInitiativeId initiativeId,
        RiskLevel riskLevel
) {
}
