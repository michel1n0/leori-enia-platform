package com.leori.enia.risk.application.exception;

import com.leori.enia.risk.domain.RiskAssessmentId;

public final class RiskAssessmentNotFoundException extends RuntimeException {

    public RiskAssessmentNotFoundException(RiskAssessmentId id) {
        super("Risk assessment not found: " + id);
    }
}
