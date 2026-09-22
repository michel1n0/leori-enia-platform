package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;

import java.util.Objects;

public class StartAssessmentAIInitiativeUseCase {

    private final AIInitiativeRepository repository;

    public StartAssessmentAIInitiativeUseCase(
            AIInitiativeRepository repository
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "AI initiative repository is required"
        );
    }

    public AIInitiative execute(StartAssessmentAIInitiativeCommand command) {
        Objects.requireNonNull(
                command,
                "Start assessment AI initiative command is required"
        );

        LoadedAIInitiative loaded = repository.findById(command.initiativeId())
                .orElseThrow(() -> new AIInitiativeNotFoundException(
                        command.initiativeId()
                ));
        AIInitiative initiative = loaded.initiative();

        initiative.startAssessment();

        return repository.save(loaded).initiative();
    }
}
