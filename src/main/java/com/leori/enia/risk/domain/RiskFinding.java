package com.leori.enia.risk.domain;

import java.util.Objects;

public record RiskFinding(String description, Likelihood likelihood, ImpactMagnitude impactMagnitude) {

    public RiskFinding {
        description = requireText(description, "Description is required");
        Objects.requireNonNull(likelihood, "Likelihood is required");
        Objects.requireNonNull(impactMagnitude, "Impact magnitude is required");
    }

    private static String requireText(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
