package com.leori.enia.initiative.application.port;

import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;

import java.util.Optional;

public interface AIInitiativeRepository {

    /** Inserts a new initiative. An existing ID must not be overwritten. */
    AIInitiative create(AIInitiative initiative);

    /**
     * Saves changes only if the stored version still matches the loaded version.
     * A stale or deleted row must fail, never be overwritten or reinserted.
     * Reload explicitly before a subsequent edit; this does not advance the
     * supplied revision. Implementations may report conflicts at commit time.
     */
    SavedAIInitiative save(LoadedAIInitiative loaded);

    Optional<LoadedAIInitiative> findById(AIInitiativeId id);
}
