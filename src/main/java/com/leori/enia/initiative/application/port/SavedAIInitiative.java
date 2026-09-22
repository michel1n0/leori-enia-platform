package com.leori.enia.initiative.application.port;

import com.leori.enia.initiative.domain.AIInitiative;

import java.util.Objects;

public record SavedAIInitiative(AIInitiative initiative, long version) {
    public SavedAIInitiative {
        Objects.requireNonNull(initiative, "AI initiative is required");
        if (version < 0) {
            throw new IllegalArgumentException("Saved version must not be negative");
        }
    }
}
