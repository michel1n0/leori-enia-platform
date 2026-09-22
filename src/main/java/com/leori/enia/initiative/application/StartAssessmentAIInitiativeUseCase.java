package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.exception.AIInitiativeInvalidTransitionException;
import com.leori.enia.initiative.application.exception.AIInitiativeRevisionMismatchException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.application.port.SavedAIInitiative;
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

    public VersionedAIInitiativeDetails execute(StartAssessmentAIInitiativeCommand command) {
        Objects.requireNonNull(
                command,
                "Start assessment AI initiative command is required"
        );
        Objects.requireNonNull(command.expectedRevision(), "Expected revision is required");

        LoadedAIInitiative loaded = repository.findById(command.initiativeId())
                .orElseThrow(() -> new AIInitiativeNotFoundException(
                        command.initiativeId()
                ));
        AIInitiative initiative = loaded.initiative();
        if (!loaded.initiative().id().equals(command.expectedRevision().initiativeId())
                || loaded.version() != command.expectedRevision().value()) {
            throw new AIInitiativeRevisionMismatchException();
        }

        try {
            initiative.startAssessment();
        } catch (IllegalStateException exception) {
            throw new AIInitiativeInvalidTransitionException(exception);
        }

        SavedAIInitiative saved = repository.save(loaded);
        return new VersionedAIInitiativeDetails(
                AIInitiativeDetails.from(saved.initiative()), saved.version());
    }
}
