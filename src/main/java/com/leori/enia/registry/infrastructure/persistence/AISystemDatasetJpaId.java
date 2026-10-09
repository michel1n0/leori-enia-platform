package com.leori.enia.registry.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class AISystemDatasetJpaId implements Serializable {

    @Column(name = "system_id", nullable = false, updatable = false)
    private UUID systemId;

    @Column(name = "dataset_id", nullable = false, updatable = false)
    private UUID datasetId;

    protected AISystemDatasetJpaId() {
    }

    AISystemDatasetJpaId(UUID systemId, UUID datasetId) {
        this.systemId = systemId;
        this.datasetId = datasetId;
    }

    UUID systemId() {
        return systemId;
    }

    UUID datasetId() {
        return datasetId;
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof AISystemDatasetJpaId other)) {
            return false;
        }
        return Objects.equals(systemId, other.systemId)
                && Objects.equals(datasetId, other.datasetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(systemId, datasetId);
    }
}
