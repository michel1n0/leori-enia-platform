package com.leori.enia.risk.infrastructure.web;

import com.leori.enia.risk.domain.ControlImplementation;

import java.time.Instant;
import java.util.UUID;

public record ControlImplementationResponse(
        UUID id,
        UUID controlId,
        String description,
        Instant implementedAt
) {
    static ControlImplementationResponse from(ControlImplementation implementation) {
        return new ControlImplementationResponse(
                implementation.id().value(),
                implementation.controlId().value(),
                implementation.description(),
                implementation.implementedAt()
        );
    }
}
