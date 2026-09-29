package com.leori.enia.registry.infrastructure.web;

import com.leori.enia.registry.domain.AIModel;

import java.time.Instant;
import java.util.UUID;

public record AIModelResponse(
        UUID id,
        UUID systemId,
        String name,
        String description,
        String provider,
        Instant createdAt
) {
    static AIModelResponse from(AIModel model) {
        return new AIModelResponse(
                model.id().value(),
                model.systemId().value(),
                model.name(),
                model.description(),
                model.provider(),
                model.createdAt()
        );
    }
}
