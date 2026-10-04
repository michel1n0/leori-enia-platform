package com.leori.enia.risk.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RecordRiskAssessmentRequest(
        @NotNull UUID systemId,
        @NotBlank String purpose,
        @NotBlank String deploymentContext,
        @NotEmpty List<@Valid @NotNull RiskFindingRequest> findings
) {
    public RecordRiskAssessmentRequest {
        purpose = purpose == null ? null : purpose.trim();
        deploymentContext = deploymentContext == null ? null : deploymentContext.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record RiskFindingRequest(
            @NotBlank String description,
            @NotNull Likelihood likelihood,
            @NotNull ImpactMagnitude impactMagnitude
    ) {
        public RiskFindingRequest {
            description = description == null ? null : description.trim();
        }
    }
}
