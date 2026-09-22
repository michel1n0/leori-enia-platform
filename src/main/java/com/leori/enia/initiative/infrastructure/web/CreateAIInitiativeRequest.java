package com.leori.enia.initiative.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateAIInitiativeRequest(
        @NotNull UUID organizationId,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 4000) String description,
        @NotNull Boolean usesPersonalData,
        @NotNull Boolean impactsRights
) {
    public CreateAIInitiativeRequest {
        // Match the aggregate's trim behavior before checking storage capacity.
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
    }
}
