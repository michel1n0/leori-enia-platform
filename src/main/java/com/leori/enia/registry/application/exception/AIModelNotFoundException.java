package com.leori.enia.registry.application.exception;

import com.leori.enia.registry.domain.AIModelId;

public final class AIModelNotFoundException extends RuntimeException {

    public AIModelNotFoundException(AIModelId id) {
        super("AI model not found: " + id);
    }
}
