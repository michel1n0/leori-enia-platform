package com.leori.enia.risk.application.exception;

import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;

public final class RiskFindingNotFoundException extends RuntimeException {

    public RiskFindingNotFoundException(RiskFindingId findingId, RiskAssessmentId assessmentId) {
        super("Risk finding not found: " + findingId + " in risk assessment: " + assessmentId);
    }
}
