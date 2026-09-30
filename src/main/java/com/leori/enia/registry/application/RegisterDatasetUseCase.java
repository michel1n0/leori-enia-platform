package com.leori.enia.registry.application;

import com.leori.enia.registry.application.port.DatasetRepository;
import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;

import java.time.Clock;
import java.util.Objects;

public class RegisterDatasetUseCase {

    private final DatasetRepository datasetRepository;
    private final Clock clock;

    public RegisterDatasetUseCase(DatasetRepository datasetRepository, Clock clock) {
        this.datasetRepository = Objects.requireNonNull(datasetRepository, "Dataset repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public Dataset execute(RegisterDatasetCommand command) {
        Objects.requireNonNull(command, "Register dataset command is required");

        Dataset dataset = Dataset.builder()
                .id(DatasetId.generate())
                .name(command.name())
                .description(command.description())
                .createdAt(clock.instant())
                .build();

        return datasetRepository.create(dataset);
    }
}
