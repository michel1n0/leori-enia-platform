package com.leori.enia.governance.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemGovernanceSummaryRepository;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystemId;

import java.util.Objects;

public class GetAISystemGovernanceSummaryUseCase {

    private final AISystemRepository aiSystemRepository;
    private final AISystemGovernanceSummaryRepository summaryRepository;

    public GetAISystemGovernanceSummaryUseCase(
            AISystemRepository aiSystemRepository,
            AISystemGovernanceSummaryRepository summaryRepository
    ) {
        this.aiSystemRepository = Objects.requireNonNull(aiSystemRepository, "AI system repository is required");
        this.summaryRepository = Objects.requireNonNull(
                summaryRepository,
                "AI system governance summary repository is required");
    }

    public AISystemGovernanceSummary execute(AISystemId aiSystemId) {
        Objects.requireNonNull(aiSystemId, "AI system id is required");
        aiSystemRepository.findById(aiSystemId)
                .orElseThrow(() -> new AISystemNotFoundException(aiSystemId));
        return summaryRepository.summarize(aiSystemId);
    }
}
