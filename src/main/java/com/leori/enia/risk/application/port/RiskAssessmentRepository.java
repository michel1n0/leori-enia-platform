package com.leori.enia.risk.application.port;

import com.leori.enia.risk.domain.RiskAssessment;

public interface RiskAssessmentRepository {

    /**
     * Inserts a new risk assessment, never updating, merging, replacing or upserting a row.
     * Returns the same aggregate with its business state and pending events unchanged.
     * The configured repository starts a REQUIRED transaction or joins the caller's;
     * returning from a joined transaction does not imply that it has committed.
     */
    RiskAssessment create(RiskAssessment assessment);
}
