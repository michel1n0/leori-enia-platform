package com.leori.enia.risk.application;

import com.leori.enia.risk.application.exception.ControlNotFoundException;
import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlRepository;

import java.util.Objects;

public class GetControlUseCase {

    private final ControlRepository repository;

    public GetControlUseCase(ControlRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Control repository is required");
    }

    public Control execute(ControlId id) {
        Objects.requireNonNull(id, "Control id is required");
        return repository.findById(id)
                .orElseThrow(() -> new ControlNotFoundException(id));
    }
}
