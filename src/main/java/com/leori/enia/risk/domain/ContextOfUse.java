package com.leori.enia.risk.domain;

public record ContextOfUse(String purpose, String deploymentContext) {

    public ContextOfUse {
        purpose = requireText(purpose, "Purpose is required");
        deploymentContext = requireText(deploymentContext, "Deployment context is required");
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
