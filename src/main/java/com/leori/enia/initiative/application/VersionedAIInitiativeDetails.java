package com.leori.enia.initiative.application;

import java.util.Objects;

public record VersionedAIInitiativeDetails(AIInitiativeDetails details, long revision) {
    public VersionedAIInitiativeDetails {
        Objects.requireNonNull(details, "AI initiative details are required");
        if (revision < 0) {
            throw new IllegalArgumentException("Revision must not be negative");
        }
    }
}
