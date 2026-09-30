package com.leori.enia.registry.application.exception;

import com.leori.enia.registry.domain.DatasetId;

public final class DatasetNotFoundException extends RuntimeException {

    public DatasetNotFoundException(DatasetId id) {
        super("Dataset not found: " + id);
    }
}
