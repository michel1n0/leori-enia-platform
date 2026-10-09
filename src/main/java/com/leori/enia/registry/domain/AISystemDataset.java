package com.leori.enia.registry.domain;

import com.leori.enia.governance.domain.AISystemId;

import java.time.Instant;
import java.util.Objects;

/** Association between an AI system and a dataset used by that system. */
public final class AISystemDataset {

    private final AISystemId aiSystemId;
    private final DatasetId datasetId;
    private final Instant associatedAt;

    public AISystemDataset(AISystemId aiSystemId, DatasetId datasetId, Instant associatedAt) {
        this.aiSystemId = Objects.requireNonNull(aiSystemId, "AI system id is required");
        this.datasetId = Objects.requireNonNull(datasetId, "Dataset id is required");
        this.associatedAt = Objects.requireNonNull(associatedAt, "Associated at is required");
    }

    public AISystemId aiSystemId() {
        return aiSystemId;
    }

    public DatasetId datasetId() {
        return datasetId;
    }

    public Instant associatedAt() {
        return associatedAt;
    }
}
