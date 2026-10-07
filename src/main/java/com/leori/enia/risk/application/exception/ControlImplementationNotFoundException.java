package com.leori.enia.risk.application.exception;

import com.leori.enia.risk.domain.ControlImplementationId;

public final class ControlImplementationNotFoundException extends RuntimeException {

    public ControlImplementationNotFoundException(ControlImplementationId id) {
        super("Control implementation not found: " + id);
    }
}
