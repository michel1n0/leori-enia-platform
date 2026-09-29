package com.leori.enia.registry.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_models")
class AIModelJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "system_id", nullable = false)
    private UUID systemId;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "provider", nullable = false, columnDefinition = "TEXT")
    private String provider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AIModelJpaEntity() {
    }

    AIModelJpaEntity(
            UUID id,
            UUID systemId,
            String name,
            String description,
            String provider,
            Instant createdAt
    ) {
        this.id = id;
        this.systemId = systemId;
        this.name = name;
        this.description = description;
        this.provider = provider;
        this.createdAt = createdAt;
    }

    UUID id() {
        return id;
    }

    UUID systemId() {
        return systemId;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    String provider() {
        return provider;
    }

    Instant createdAt() {
        return createdAt;
    }
}
