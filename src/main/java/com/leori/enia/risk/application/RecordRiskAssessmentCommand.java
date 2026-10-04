package com.leori.enia.risk.application;

import com.leori.enia.governance.domain.AISystemId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record RecordRiskAssessmentCommand(
        AISystemId systemId,
        String purpose,
        String deploymentContext,
        List<RecordRiskFindingCommand> findings
) {

    public RecordRiskAssessmentCommand {
        Objects.requireNonNull(systemId, "System id is required");
        findings = findings == null ? null : Collections.unmodifiableList(new ArrayList<>(findings));
    }
}
