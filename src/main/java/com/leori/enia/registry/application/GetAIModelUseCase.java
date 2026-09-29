package com.leori.enia.registry.application;

import com.leori.enia.registry.application.exception.AIModelNotFoundException;
import com.leori.enia.registry.application.port.AIModelRepository;
import com.leori.enia.registry.domain.AIModel;
import com.leori.enia.registry.domain.AIModelId;

import java.util.Objects;

public class GetAIModelUseCase {

    private final AIModelRepository repository;

    public GetAIModelUseCase(AIModelRepository repository) {
        this.repository = Objects.requireNonNull(repository, "AI model repository is required");
    }

    public AIModel execute(AIModelId id) {
        Objects.requireNonNull(id, "AI model id is required");
        return repository.findById(id)
                .orElseThrow(() -> new AIModelNotFoundException(id));
    }
}
