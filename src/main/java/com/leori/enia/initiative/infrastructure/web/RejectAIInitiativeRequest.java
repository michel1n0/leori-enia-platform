package com.leori.enia.initiative.infrastructure.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record RejectAIInitiativeRequest(@NotBlank String reason) {

    @AssertTrue(message = "Rejection reason must not be blank after normalization")
    public boolean isAcceptableReason() {
        return reason == null || !reason.trim().isBlank();
    }
}
