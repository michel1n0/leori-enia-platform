package com.leori.enia.governance.application.exception;

import com.leori.enia.initiative.domain.AIInitiativeId;

import java.util.Objects;

public final class AISystemAlreadyRegisteredException extends RuntimeException {

    private final AIInitiativeId sourceInitiativeId;

    public AISystemAlreadyRegisteredException(AIInitiativeId sourceInitiativeId, Throwable cause) {
        super("AI system already registered for source initiative: " + sourceInitiativeId, cause);
        this.sourceInitiativeId = Objects.requireNonNull(sourceInitiativeId, "Source initiative id is required");
    }

    public AIInitiativeId sourceInitiativeId() {
        return sourceInitiativeId;
    }
}
