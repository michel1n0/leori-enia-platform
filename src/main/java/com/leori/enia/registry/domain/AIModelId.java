package com.leori.enia.registry.domain;

import java.util.Objects;
import java.util.UUID;

public record AIModelId(UUID value) {

    public AIModelId {
        Objects.requireNonNull(value, "AI model id is required");
    }

    public static AIModelId generate() {
        return new AIModelId(UUID.randomUUID());
    }

    public static AIModelId of(String value) {
        return new AIModelId(UUID.fromString(value));
    }
}
