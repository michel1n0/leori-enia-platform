package com.leori.enia.initiative.infrastructure.persistence;

import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_initiatives")
class AIInitiativeJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InitiativeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "preliminary_risk", nullable = false, length = 32)
    private RiskLevel preliminaryRisk;

    @Column(name = "uses_personal_data", nullable = false)
    private boolean usesPersonalData;

    @Column(name = "impacts_rights", nullable = false)
    private boolean impactsRights;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AIInitiativeJpaEntity() {
    }

    AIInitiativeJpaEntity(
            UUID id,
            UUID organizationId,
            String name,
            String description,
            InitiativeStatus status,
            RiskLevel preliminaryRisk,
            boolean usesPersonalData,
            boolean impactsRights,
            Instant createdAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.preliminaryRisk = preliminaryRisk;
        this.usesPersonalData = usesPersonalData;
        this.impactsRights = impactsRights;
        this.createdAt = createdAt;
    }

    UUID id() {
        return id;
    }

    UUID organizationId() {
        return organizationId;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    InitiativeStatus status() {
        return status;
    }

    RiskLevel preliminaryRisk() {
        return preliminaryRisk;
    }

    boolean usesPersonalData() {
        return usesPersonalData;
    }

    boolean impactsRights() {
        return impactsRights;
    }

    Instant createdAt() {
        return createdAt;
    }
}
