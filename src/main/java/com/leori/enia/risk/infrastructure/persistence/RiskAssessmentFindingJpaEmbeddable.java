package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
class RiskAssessmentFindingJpaEmbeddable {

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "likelihood", nullable = false, length = 16)
    private Likelihood likelihood;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_magnitude", nullable = false, length = 16)
    private ImpactMagnitude impactMagnitude;

    protected RiskAssessmentFindingJpaEmbeddable() {
    }

    RiskAssessmentFindingJpaEmbeddable(
            String description,
            Likelihood likelihood,
            ImpactMagnitude impactMagnitude
    ) {
        this.description = description;
        this.likelihood = likelihood;
        this.impactMagnitude = impactMagnitude;
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
