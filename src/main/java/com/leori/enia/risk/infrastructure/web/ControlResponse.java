package com.leori.enia.risk.infrastructure.web;

import com.leori.enia.risk.domain.Control;

import java.time.Instant;
import java.util.UUID;

public record ControlResponse(
        UUID id,
        UUID riskAssessmentId,
        UUID riskFindingId,
        String name,
        String description,
        Instant createdAt
) {
    static ControlResponse from(Control control) {
        return new ControlResponse(
                control.id().value(),
                control.riskAssessmentId().value(),
                control.riskFindingId().value(),
                control.name(),
                control.description(),
                control.createdAt()
        );
    }
}
