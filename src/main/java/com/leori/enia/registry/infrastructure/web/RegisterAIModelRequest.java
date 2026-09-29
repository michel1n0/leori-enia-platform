package com.leori.enia.registry.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RegisterAIModelRequest(
        @NotNull UUID systemId,
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String provider
) {
    public RegisterAIModelRequest {
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
        provider = provider == null ? null : provider.trim();
    }
}
