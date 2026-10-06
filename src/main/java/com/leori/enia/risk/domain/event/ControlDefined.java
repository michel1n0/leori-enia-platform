package com.leori.enia.risk.domain.event;

import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;

public record ControlDefined(
        ControlId controlId,
        RiskAssessmentId riskAssessmentId,
        RiskFindingId riskFindingId,
        Instant occurredAt
) implements DomainEvent {
}
