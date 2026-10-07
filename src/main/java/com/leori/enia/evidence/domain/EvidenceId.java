package com.leori.enia.evidence.domain;

import java.util.Objects;
import java.util.UUID;

public record EvidenceId(UUID value) {

    public EvidenceId {
        Objects.requireNonNull(value, "Evidence id is required");
    }

    public static EvidenceId generate() {
        return new EvidenceId(UUID.randomUUID());
    }

    public static EvidenceId of(String value) {
        return new EvidenceId(UUID.fromString(value));
    }
}
