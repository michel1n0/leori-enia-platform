package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;

import java.util.Objects;

public class GetAIInitiativeUseCase {

    private final AIInitiativeRepository repository;

    public GetAIInitiativeUseCase(AIInitiativeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "AI initiative repository is required");
    }

    public AIInitiativeDetails execute(AIInitiativeId id) {
        Objects.requireNonNull(id, "AI initiative id is required");

        AIInitiative initiative = repository.findById(id)
                .orElseThrow(() -> new AIInitiativeNotFoundException(id))
                .initiative();

        return new AIInitiativeDetails(
                initiative.id(),
                initiative.organizationId(),
                initiative.name(),
                initiative.description(),
                initiative.status(),
                initiative.preliminaryRisk(),
                initiative.usesPersonalData(),
                initiative.impactsRights(),
                initiative.createdAt()
        );
    }
}
