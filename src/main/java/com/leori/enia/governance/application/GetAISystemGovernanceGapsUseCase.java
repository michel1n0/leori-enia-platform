package com.leori.enia.governance.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemGovernanceGapsRepository;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystemId;

import java.util.Objects;

public class GetAISystemGovernanceGapsUseCase {

    private final AISystemRepository aiSystemRepository;
    private final AISystemGovernanceGapsRepository gapsRepository;

    public GetAISystemGovernanceGapsUseCase(
            AISystemRepository aiSystemRepository,
            AISystemGovernanceGapsRepository gapsRepository
    ) {
        this.aiSystemRepository = Objects.requireNonNull(aiSystemRepository, "AI system repository is required");
        this.gapsRepository = Objects.requireNonNull(
                gapsRepository,
                "AI system governance gaps repository is required");
    }

    public AISystemGovernanceGaps execute(AISystemId aiSystemId) {
        Objects.requireNonNull(aiSystemId, "AI system id is required");
        aiSystemRepository.findById(aiSystemId)
                .orElseThrow(() -> new AISystemNotFoundException(aiSystemId));
        return gapsRepository.findByAISystemId(aiSystemId);
    }
}
