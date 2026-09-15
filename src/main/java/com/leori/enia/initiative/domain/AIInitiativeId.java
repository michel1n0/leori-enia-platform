package com.leori.enia.initiative.domain;

import java.util.Objects;
import java.util.UUID;

public record AIInitiativeId(UUID value) {

    public AIInitiativeId {
        Objects.requireNonNull(value, "AI initiative id is required");
    }

    public static AIInitiativeId generate() {
        return new AIInitiativeId(UUID.randomUUID());
    }

    public static AIInitiativeId of(String value) {
        return new AIInitiativeId(UUID.fromString(value));
    }
}
