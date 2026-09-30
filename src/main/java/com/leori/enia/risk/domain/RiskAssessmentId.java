package com.leori.enia.risk.domain;

import java.util.Objects;
import java.util.UUID;

public record RiskAssessmentId(UUID value) {

    public RiskAssessmentId {
        Objects.requireNonNull(value, "Risk assessment id is required");
    }

    public static RiskAssessmentId generate() {
        return new RiskAssessmentId(UUID.randomUUID());
    }

    public static RiskAssessmentId of(String value) {
        return new RiskAssessmentId(UUID.fromString(value));
    }
}
