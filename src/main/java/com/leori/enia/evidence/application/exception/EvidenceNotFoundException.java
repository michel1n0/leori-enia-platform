package com.leori.enia.evidence.application.exception;

import com.leori.enia.evidence.domain.EvidenceId;

public final class EvidenceNotFoundException extends RuntimeException {

    public EvidenceNotFoundException(EvidenceId id) {
        super("Evidence not found: " + id);
    }
}
