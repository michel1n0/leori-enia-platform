package com.leori.enia.risk.domain.event;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;

public record RiskAssessmentRecorded(
        RiskAssessmentId riskAssessmentId,
        AISystemId systemId,
        Instant occurredAt
) implements DomainEvent {
}
