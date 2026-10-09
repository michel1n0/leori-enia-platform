package com.leori.enia.registry.infrastructure.web;

import com.leori.enia.registry.domain.AISystemDataset;

import java.time.Instant;
import java.util.UUID;

public record AISystemDatasetResponse(
        UUID aiSystemId,
        UUID datasetId,
        Instant associatedAt
) {
    static AISystemDatasetResponse from(AISystemDataset association) {
        return new AISystemDatasetResponse(
                association.aiSystemId().value(),
                association.datasetId().value(),
                association.associatedAt()
        );
    }
}
