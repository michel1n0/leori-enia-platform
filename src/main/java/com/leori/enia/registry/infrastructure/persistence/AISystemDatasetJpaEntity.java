package com.leori.enia.registry.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_system_datasets")
class AISystemDatasetJpaEntity {

    @EmbeddedId
    private AISystemDatasetJpaId id;

    @Column(name = "associated_at", nullable = false)
    private Instant associatedAt;

    protected AISystemDatasetJpaEntity() {
    }

    AISystemDatasetJpaEntity(UUID systemId, UUID datasetId, Instant associatedAt) {
        this.id = new AISystemDatasetJpaId(systemId, datasetId);
        this.associatedAt = associatedAt;
    }

    AISystemDatasetJpaId id() {
        return id;
    }

    UUID systemId() {
        return id.systemId();
    }

    UUID datasetId() {
        return id.datasetId();
    }

    Instant associatedAt() {
        return associatedAt;
    }
}
