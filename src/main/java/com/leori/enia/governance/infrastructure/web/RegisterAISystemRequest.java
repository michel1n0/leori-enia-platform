package com.leori.enia.governance.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RegisterAISystemRequest(
        @NotNull UUID sourceInitiativeId,
        @NotBlank String name,
        @NotBlank String description
) {
    public RegisterAISystemRequest {
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
    }
}
