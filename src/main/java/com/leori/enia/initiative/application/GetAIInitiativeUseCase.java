package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;

import java.util.Objects;

public class GetAIInitiativeUseCase {

    private final AIInitiativeRepository repository;

    public GetAIInitiativeUseCase(AIInitiativeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "AI initiative repository is required");
    }

    public VersionedAIInitiativeDetails execute(AIInitiativeId id) {
        Objects.requireNonNull(id, "AI initiative id is required");

        LoadedAIInitiative loaded = repository.findById(id)
                .orElseThrow(() -> new AIInitiativeNotFoundException(id));

        return new VersionedAIInitiativeDetails(
                AIInitiativeDetails.from(loaded.initiative()), loaded.version());
    }
}
