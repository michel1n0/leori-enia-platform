package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "risk_assessment_findings")
class RiskAssessmentFindingJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "likelihood", nullable = false, length = 16)
    private Likelihood likelihood;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_magnitude", nullable = false, length = 16)
    private ImpactMagnitude impactMagnitude;

    protected RiskAssessmentFindingJpaEntity() {
    }

    RiskAssessmentFindingJpaEntity(
            UUID id,
            String description,
            Likelihood likelihood,
            ImpactMagnitude impactMagnitude
    ) {
        this.id = id;
        this.description = description;
        this.likelihood = likelihood;
        this.impactMagnitude = impactMagnitude;
    }

    UUID id() {
        return id;
    }

    String description() {
        return description;
    }

    Likelihood likelihood() {
        return likelihood;
    }

    ImpactMagnitude impactMagnitude() {
        return impactMagnitude;
    }
}
