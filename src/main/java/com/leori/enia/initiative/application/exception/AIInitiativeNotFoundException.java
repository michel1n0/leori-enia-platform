package com.leori.enia.initiative.application.exception;

import com.leori.enia.initiative.domain.AIInitiativeId;

public final class AIInitiativeNotFoundException extends RuntimeException {

    public AIInitiativeNotFoundException(AIInitiativeId id) {
        super("AI initiative not found: " + id);
    }
}
