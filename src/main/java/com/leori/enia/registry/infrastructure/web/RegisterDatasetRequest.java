package com.leori.enia.registry.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RegisterDatasetRequest(
        @NotBlank String name,
        @NotBlank String description
) {
    public RegisterDatasetRequest {
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
    }
}
