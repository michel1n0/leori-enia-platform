package com.leori.enia.risk.application.exception;

import com.leori.enia.risk.domain.ControlId;

public final class ControlNotFoundException extends RuntimeException {

    public ControlNotFoundException(ControlId id) {
        super("Control not found: " + id);
    }
}
