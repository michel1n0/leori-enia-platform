package com.leori.enia.risk.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RecordControlImplementationRequest(
        @NotNull UUID controlId,
        @NotBlank String description
) {
    public RecordControlImplementationRequest {
        description = description == null ? null : description.trim();
    }
}
