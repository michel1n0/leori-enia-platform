package com.leori.enia.governance.domain;

import java.util.Objects;
import java.util.UUID;

public record AISystemId(UUID value) {

    public AISystemId {
        Objects.requireNonNull(value, "AI system id is required");
    }

    public static AISystemId generate() {
        return new AISystemId(UUID.randomUUID());
    }

    public static AISystemId of(String value) {
        return new AISystemId(UUID.fromString(value));
    }
}
