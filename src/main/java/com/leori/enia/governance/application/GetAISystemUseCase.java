package com.leori.enia.governance.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;

import java.util.Objects;

public class GetAISystemUseCase {

    private final AISystemRepository repository;

    public GetAISystemUseCase(AISystemRepository repository) {
        this.repository = Objects.requireNonNull(repository, "AI system repository is required");
    }

    public AISystem execute(AISystemId id) {
        Objects.requireNonNull(id, "AI system id is required");
        return repository.findById(id)
                .orElseThrow(() -> new AISystemNotFoundException(id));
    }
}
