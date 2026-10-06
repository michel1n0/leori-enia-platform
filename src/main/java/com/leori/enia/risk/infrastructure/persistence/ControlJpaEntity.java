package com.leori.enia.risk.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "controls")
class ControlJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "risk_assessment_id", nullable = false)
    private UUID riskAssessmentId;

    @Column(name = "risk_finding_id", nullable = false)
    private UUID riskFindingId;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ControlJpaEntity() {
    }

    ControlJpaEntity(
            UUID id,
            UUID riskAssessmentId,
            UUID riskFindingId,
            String name,
            String description,
            Instant createdAt
    ) {
        this.id = id;
        this.riskAssessmentId = riskAssessmentId;
        this.riskFindingId = riskFindingId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    UUID id() {
        return id;
    }

    UUID riskAssessmentId() {
        return riskAssessmentId;
    }

    UUID riskFindingId() {
        return riskFindingId;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    Instant createdAt() {
        return createdAt;
    }
}
