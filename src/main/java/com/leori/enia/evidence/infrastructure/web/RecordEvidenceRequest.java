package com.leori.enia.evidence.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RecordEvidenceRequest(
        @NotNull UUID controlImplementationId,
        @NotBlank String description,
        @NotBlank String reference
) {
    public RecordEvidenceRequest {
        description = description == null ? null : description.trim();
        reference = reference == null ? null : reference.trim();
    }
}
