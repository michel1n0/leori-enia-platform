package com.leori.enia.initiative.application.exception;

public final class AIInitiativeInvalidTransitionException extends IllegalStateException {

    public AIInitiativeInvalidTransitionException(IllegalStateException cause) {
        super(cause.getMessage(), cause);
    }
}
