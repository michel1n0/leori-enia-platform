package com.leori.enia.governance.application;

import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiative;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class RegisterAISystemUseCase {

    private final AIInitiativeRepository initiativeRepository;
    private final AISystemRepository systemRepository;
    private final Clock clock;

    public RegisterAISystemUseCase(
            AIInitiativeRepository initiativeRepository,
            AISystemRepository systemRepository,
            Clock clock
    ) {
        this.initiativeRepository = Objects.requireNonNull(
                initiativeRepository,
                "AI initiative repository is required"
        );
        this.systemRepository = Objects.requireNonNull(
                systemRepository,
                "AI system repository is required"
        );
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public AISystem execute(RegisterAISystemCommand command) {
        Objects.requireNonNull(command, "Register AI system command is required");

        AIInitiative initiative = initiativeRepository.findById(command.sourceInitiativeId())
                .orElseThrow(() -> new AIInitiativeNotFoundException(command.sourceInitiativeId()))
                .initiative();
        initiative.requireApprovedForSystemRegistration();

        Instant registeredAt = clock.instant();
        AISystem system = AISystem.builder()
                .id(AISystemId.generate())
                .organizationId(initiative.organizationId())
                .sourceInitiativeId(initiative.id())
                .name(command.name())
                .description(command.description())
                .createdAt(registeredAt)
                .build();

        return systemRepository.create(system);
    }
}
