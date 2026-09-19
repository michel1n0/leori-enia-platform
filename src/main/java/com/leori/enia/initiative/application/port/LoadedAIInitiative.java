package com.leori.enia.initiative.application.port;

import com.leori.enia.initiative.domain.AIInitiative;

import java.util.Objects;

/**
 * A mutable aggregate paired with the immutable revision it was loaded from.
 * Domain behavior changes the aggregate, never the expected revision.
 */
public record LoadedAIInitiative(AIInitiative initiative, long version) {

    public LoadedAIInitiative {
        Objects.requireNonNull(initiative, "AI initiative is required");
        if (version < 0) {
            throw new IllegalArgumentException("Loaded version must not be negative");
        }
    }
}
