package com.leori.enia.registry.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.application.port.AISystemDatasetRepository;
import com.leori.enia.registry.domain.Dataset;

import java.util.List;
import java.util.Objects;

public class GetDatasetsByAISystemUseCase {

    private final AISystemRepository systemRepository;
    private final AISystemDatasetRepository associationRepository;

    public GetDatasetsByAISystemUseCase(
            AISystemRepository systemRepository,
            AISystemDatasetRepository associationRepository
    ) {
        this.systemRepository = Objects.requireNonNull(systemRepository, "AI system repository is required");
        this.associationRepository = Objects.requireNonNull(
                associationRepository,
                "AI system dataset repository is required"
        );
    }

    public List<Dataset> execute(AISystemId aiSystemId) {
        Objects.requireNonNull(aiSystemId, "AI system id is required");

        systemRepository.findById(aiSystemId)
                .orElseThrow(() -> new AISystemNotFoundException(aiSystemId));

        return associationRepository.findDatasetsByAISystemId(aiSystemId);
    }
}
