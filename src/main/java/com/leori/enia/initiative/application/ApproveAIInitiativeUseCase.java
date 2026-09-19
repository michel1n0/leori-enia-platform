package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiative;

import java.time.Clock;
import java.util.Objects;

public class ApproveAIInitiativeUseCase {

    private final AIInitiativeRepository repository;
    private final Clock clock;

    public ApproveAIInitiativeUseCase(
            AIInitiativeRepository repository,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "AI initiative repository is required"
        );
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public AIInitiative execute(ApproveAIInitiativeCommand command) {
        Objects.requireNonNull(
                command,
                "Approve AI initiative command is required"
        );

        AIInitiative initiative = repository.findById(command.initiativeId())
                .orElseThrow(() -> new AIInitiativeNotFoundException(
                        command.initiativeId()
                ));

        initiative.approve(clock.instant());

        return repository.save(initiative);
    }
}
