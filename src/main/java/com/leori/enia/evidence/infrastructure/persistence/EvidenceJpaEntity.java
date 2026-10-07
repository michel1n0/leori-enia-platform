package com.leori.enia.evidence.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence")
class EvidenceJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "control_implementation_id", nullable = false)
    private UUID controlImplementationId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "reference", nullable = false, columnDefinition = "TEXT")
    private String reference;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected EvidenceJpaEntity() {
    }

    EvidenceJpaEntity(UUID id, UUID controlImplementationId, String description, String reference, Instant recordedAt) {
        this.id = id;
        this.controlImplementationId = controlImplementationId;
        this.description = description;
        this.reference = reference;
        this.recordedAt = recordedAt;
    }

    UUID id() {
        return id;
    }

    UUID controlImplementationId() {
        return controlImplementationId;
    }

    String description() {
        return description;
    }

    String reference() {
        return reference;
    }

    Instant recordedAt() {
        return recordedAt;
    }
}
