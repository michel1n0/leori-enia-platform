package com.leori.enia.risk.application;

import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;

public record RecordRiskFindingCommand(
        String description,
        Likelihood likelihood,
        ImpactMagnitude impactMagnitude
) {
}
