package com.leori.enia.registry.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.registry.application.port.AIModelRepository;
import com.leori.enia.registry.domain.AIModel;
import com.leori.enia.registry.domain.AIModelId;

import java.time.Clock;
import java.util.Objects;

public class RegisterAIModelUseCase {

    private final AISystemRepository systemRepository;
    private final AIModelRepository modelRepository;
    private final Clock clock;

    public RegisterAIModelUseCase(
            AISystemRepository systemRepository,
            AIModelRepository modelRepository,
            Clock clock
    ) {
        this.systemRepository = Objects.requireNonNull(
                systemRepository,
                "AI system repository is required"
        );
        this.modelRepository = Objects.requireNonNull(
                modelRepository,
                "AI model repository is required"
        );
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public AIModel execute(RegisterAIModelCommand command) {
        Objects.requireNonNull(command, "Register AI model command is required");

        systemRepository.findById(command.systemId())
                .orElseThrow(() -> new AISystemNotFoundException(command.systemId()));

        AIModel model = AIModel.builder()
                .id(AIModelId.generate())
                .systemId(command.systemId())
                .name(command.name())
                .description(command.description())
                .provider(command.provider())
                .createdAt(clock.instant())
                .build();

        return modelRepository.create(model);
    }
}
