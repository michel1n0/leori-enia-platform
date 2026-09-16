package com.leori.enia.initiative.application.port;

import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;

import java.util.Optional;

public interface AIInitiativeRepository {

    AIInitiative save(AIInitiative initiative);

    Optional<AIInitiative> findById(AIInitiativeId id);
}
