package com.leori.enia.governance.application.exception;

import com.leori.enia.governance.domain.AISystemId;

public final class AISystemNotFoundException extends RuntimeException {

    public AISystemNotFoundException(AISystemId id) {
        super("AI system not found: " + id);
    }
}
