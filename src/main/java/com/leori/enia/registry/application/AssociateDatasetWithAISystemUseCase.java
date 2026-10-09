package com.leori.enia.registry.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.registry.application.exception.DatasetNotFoundException;
import com.leori.enia.registry.application.port.AISystemDatasetRepository;
import com.leori.enia.registry.application.port.DatasetRepository;
import com.leori.enia.registry.domain.AISystemDataset;

import java.time.Clock;
import java.util.Objects;

public class AssociateDatasetWithAISystemUseCase {

    private final AISystemRepository systemRepository;
    private final DatasetRepository datasetRepository;
    private final AISystemDatasetRepository associationRepository;
    private final Clock registryClock;

    public AssociateDatasetWithAISystemUseCase(
            AISystemRepository systemRepository,
            DatasetRepository datasetRepository,
            AISystemDatasetRepository associationRepository,
            Clock registryClock
    ) {
        this.systemRepository = Objects.requireNonNull(systemRepository, "AI system repository is required");
        this.datasetRepository = Objects.requireNonNull(datasetRepository, "Dataset repository is required");
        this.associationRepository = Objects.requireNonNull(
                associationRepository,
                "AI system dataset repository is required"
        );
        this.registryClock = Objects.requireNonNull(registryClock, "Clock is required");
    }

    public AISystemDataset execute(AssociateDatasetWithAISystemCommand command) {
        Objects.requireNonNull(command, "Associate dataset with AI system command is required");

        systemRepository.findById(command.aiSystemId())
                .orElseThrow(() -> new AISystemNotFoundException(command.aiSystemId()));
        datasetRepository.findById(command.datasetId())
                .orElseThrow(() -> new DatasetNotFoundException(command.datasetId()));

        return associationRepository.create(new AISystemDataset(
                command.aiSystemId(),
                command.datasetId(),
                registryClock.instant()
        ));
    }
}
