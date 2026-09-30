package com.leori.enia.registry.application;

import com.leori.enia.registry.application.exception.DatasetNotFoundException;
import com.leori.enia.registry.application.port.DatasetRepository;
import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;

import java.util.Objects;

public class GetDatasetUseCase {

    private final DatasetRepository repository;

    public GetDatasetUseCase(DatasetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Dataset repository is required");
    }

    public Dataset execute(DatasetId id) {
        Objects.requireNonNull(id, "Dataset id is required");
        return repository.findById(id)
                .orElseThrow(() -> new DatasetNotFoundException(id));
    }
}
