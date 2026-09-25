package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_systems")
class AISystemJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "source_initiative_id", nullable = false)
    private UUID sourceInitiativeId;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AISystemStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AISystemJpaEntity() {
    }

    AISystemJpaEntity(
            UUID id,
            UUID organizationId,
            UUID sourceInitiativeId,
            String name,
            String description,
            AISystemStatus status,
            Instant createdAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.sourceInitiativeId = sourceInitiativeId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
    }

    UUID id() {
        return id;
    }

    UUID organizationId() {
        return organizationId;
    }

    UUID sourceInitiativeId() {
        return sourceInitiativeId;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    AISystemStatus status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }
}
