package com.leori.enia.risk.application;

import com.leori.enia.risk.application.exception.ControlImplementationNotFoundException;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;

import java.util.Objects;

public class GetControlImplementationUseCase {

    private final ControlImplementationRepository repository;

    public GetControlImplementationUseCase(ControlImplementationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Control implementation repository is required");
    }

    public ControlImplementation execute(ControlImplementationId id) {
        Objects.requireNonNull(id, "Control implementation id is required");
        return repository.findById(id)
                .orElseThrow(() -> new ControlImplementationNotFoundException(id));
    }
}
