package com.leori.enia.risk.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "control_implementations")
class ControlImplementationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "control_id", nullable = false)
    private UUID controlId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "implemented_at", nullable = false)
    private Instant implementedAt;

    protected ControlImplementationJpaEntity() {
    }

    ControlImplementationJpaEntity(UUID id, UUID controlId, String description, Instant implementedAt) {
        this.id = id;
        this.controlId = controlId;
        this.description = description;
        this.implementedAt = implementedAt;
    }

    UUID id() {
        return id;
    }

    UUID controlId() {
        return controlId;
    }

    String description() {
        return description;
    }

    Instant implementedAt() {
        return implementedAt;
    }
}
