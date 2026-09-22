package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.exception.AIInitiativeInvalidTransitionException;
import com.leori.enia.initiative.application.exception.AIInitiativeRevisionMismatchException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.application.port.SavedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;

import java.time.Clock;
import java.time.Instant;
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

    public VersionedAIInitiativeDetails execute(AssessRiskAIInitiativeCommand command) {
        Objects.requireNonNull(
                command,
                "Assess risk AI initiative command is required"
        );
        Objects.requireNonNull(command.expectedRevision(), "Expected revision is required");

        LoadedAIInitiative loaded = repository.findById(command.initiativeId())
                .orElseThrow(() -> new AIInitiativeNotFoundException(
                        command.initiativeId()
                ));
        AIInitiative initiative = loaded.initiative();
        if (!command.expectedRevision().matches(loaded.initiative().id(), loaded.version())) {
            throw new AIInitiativeRevisionMismatchException();
        }

        Instant occurredAt = clock.instant();
        try {
            initiative.assessRisk(command.riskLevel(), occurredAt);
        } catch (IllegalStateException exception) {
            throw new AIInitiativeInvalidTransitionException(exception);
        }

        SavedAIInitiative saved = repository.save(loaded);
        return new VersionedAIInitiativeDetails(
                AIInitiativeDetails.from(saved.initiative()), saved.version());
    }
}
