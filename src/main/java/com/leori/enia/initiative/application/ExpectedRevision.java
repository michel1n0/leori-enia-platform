package com.leori.enia.initiative.application;

import com.leori.enia.initiative.domain.AIInitiativeId;

import java.util.Objects;

public record ExpectedRevision(AIInitiativeId initiativeId, long value) {
    public ExpectedRevision {
        Objects.requireNonNull(initiativeId, "Initiative id is required");
        if (value < 0) {
            throw new IllegalArgumentException("Expected revision must not be negative");
        }
    }
}
