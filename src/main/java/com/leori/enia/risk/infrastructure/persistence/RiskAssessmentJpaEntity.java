package com.leori.enia.risk.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "risk_assessments")
class RiskAssessmentJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "system_id", nullable = false)
    private UUID systemId;

    @Column(name = "purpose", nullable = false, columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "deployment_context", nullable = false, columnDefinition = "TEXT")
    private String deploymentContext;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(
            name = "risk_assessment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_risk_assessment_findings_assessment")
    )
    @OrderColumn(name = "position", nullable = false)
    private List<RiskAssessmentFindingJpaEntity> findings = new ArrayList<>();

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    protected RiskAssessmentJpaEntity() {
    }

    RiskAssessmentJpaEntity(
            UUID id,
            UUID systemId,
            String purpose,
            String deploymentContext,
            List<RiskAssessmentFindingJpaEntity> findings,
            Instant assessedAt
    ) {
        this.id = id;
        this.systemId = systemId;
        this.purpose = purpose;
        this.deploymentContext = deploymentContext;
        this.findings = new ArrayList<>(findings);
        this.assessedAt = assessedAt;
    }

    UUID id() {
        return id;
    }

    UUID systemId() {
        return systemId;
    }

    String purpose() {
        return purpose;
    }

    String deploymentContext() {
        return deploymentContext;
    }

    List<RiskAssessmentFindingJpaEntity> findings() {
        return List.copyOf(findings);
    }

    Instant assessedAt() {
        return assessedAt;
    }
}
