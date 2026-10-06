package com.leori.enia.risk.domain;

import java.util.Objects;
import java.util.UUID;

public record RiskFindingId(UUID value) {

    public RiskFindingId {
        Objects.requireNonNull(value, "Risk finding id is required");
    }

    public static RiskFindingId generate() {
        return new RiskFindingId(UUID.randomUUID());
    }

    public static RiskFindingId of(String value) {
        return new RiskFindingId(UUID.fromString(value));
    }
}
