package com.leori.enia.initiative.domain.event;

import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;

public record AIInitiativeRiskAssessed(
        AIInitiativeId initiativeId,
        RiskLevel riskLevel,
        Instant occurredAt
) implements DomainEvent {
}
