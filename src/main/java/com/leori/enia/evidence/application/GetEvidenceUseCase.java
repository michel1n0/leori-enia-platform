package com.leori.enia.evidence.application;

import com.leori.enia.evidence.application.exception.EvidenceNotFoundException;
import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.evidence.domain.EvidenceRepository;

import java.util.Objects;

public class GetEvidenceUseCase {

    private final EvidenceRepository repository;

    public GetEvidenceUseCase(EvidenceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Evidence repository is required");
    }

    public Evidence execute(EvidenceId id) {
        Objects.requireNonNull(id, "Evidence id is required");
        return repository.findById(id)
                .orElseThrow(() -> new EvidenceNotFoundException(id));
    }
}
