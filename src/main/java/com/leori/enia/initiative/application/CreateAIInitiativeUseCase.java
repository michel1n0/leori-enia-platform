package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;

import java.time.Clock;
import java.util.Objects;

public final class CreateAIInitiativeUseCase {

    private final AIInitiativeRepository repository;
    private final Clock clock;

    public CreateAIInitiativeUseCase(
            AIInitiativeRepository repository,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "AI initiative repository is required"
        );
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public AIInitiative execute(CreateAIInitiativeCommand command) {
        Objects.requireNonNull(command, "Create AI initiative command is required");

        AIInitiative initiative = AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(command.organizationId())
                .name(command.name())
                .description(command.description())
                .usesPersonalData(command.usesPersonalData())
                .impactsRights(command.impactsRights())
                .createdAt(clock.instant())
                .build();

        return repository.save(initiative);
    }
}
