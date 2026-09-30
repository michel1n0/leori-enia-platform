package com.leori.enia.registry.infrastructure.web;

import com.leori.enia.registry.domain.Dataset;

import java.time.Instant;
import java.util.UUID;

public record DatasetResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt
) {
    static DatasetResponse from(Dataset dataset) {
        return new DatasetResponse(
                dataset.id().value(),
                dataset.name(),
                dataset.description(),
                dataset.createdAt()
        );
    }
}
