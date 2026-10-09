package com.leori.enia.registry.application.exception;

public final class DatasetAlreadyAssociatedWithAISystemException extends RuntimeException {

    public DatasetAlreadyAssociatedWithAISystemException(Throwable cause) {
        super("Dataset already associated with AI system", cause);
    }
}
