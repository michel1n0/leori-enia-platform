package com.leori.enia.risk.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record DefineControlRequest(
        @NotNull UUID riskAssessmentId,
        @NotNull UUID riskFindingId,
        @NotBlank String name,
        @NotBlank String description
) {
    public DefineControlRequest {
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
    }
}
