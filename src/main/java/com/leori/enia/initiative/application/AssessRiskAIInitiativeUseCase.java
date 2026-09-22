package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;

import java.time.Clock;
import java.util.Objects;

public class AssessRiskAIInitiativeUseCase {

    private final AIInitiativeRepository repository;
    private final Clock clock;

    public AssessRiskAIInitiativeUseCase(
            AIInitiativeRepository repository,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "AI initiative repository is required"
        );
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public AIInitiative execute(AssessRiskAIInitiativeCommand command) {
        Objects.requireNonNull(
                command,
                "Assess risk AI initiative command is required"
        );

        LoadedAIInitiative loaded = repository.findById(command.initiativeId())
                .orElseThrow(() -> new AIInitiativeNotFoundException(
                        command.initiativeId()
                ));
        AIInitiative initiative = loaded.initiative();

        initiative.assessRisk(command.riskLevel(), clock.instant());

        return repository.save(loaded).initiative();
    }
}
