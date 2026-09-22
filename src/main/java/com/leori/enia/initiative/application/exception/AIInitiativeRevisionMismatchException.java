package com.leori.enia.initiative.application.exception;

public final class AIInitiativeRevisionMismatchException extends RuntimeException {

    public AIInitiativeRevisionMismatchException() {
        super("AI initiative revision no longer matches");
    }

    public AIInitiativeRevisionMismatchException(Throwable cause) {
        super("AI initiative revision no longer matches", cause);
    }
}
