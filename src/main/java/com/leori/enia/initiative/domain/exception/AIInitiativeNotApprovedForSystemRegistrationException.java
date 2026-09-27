package com.leori.enia.initiative.domain.exception;

import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;

import java.util.Objects;

public final class AIInitiativeNotApprovedForSystemRegistrationException extends RuntimeException {

    private final AIInitiativeId initiativeId;
    private final InitiativeStatus actualStatus;

    public AIInitiativeNotApprovedForSystemRegistrationException(
            AIInitiativeId initiativeId,
            InitiativeStatus actualStatus
    ) {
        super("AI initiative must be APPROVED before registering an AI system: "
                + initiativeId + " was " + actualStatus);
        this.initiativeId = Objects.requireNonNull(initiativeId, "AI initiative id is required");
        this.actualStatus = Objects.requireNonNull(actualStatus, "Initiative status is required");
    }

    public AIInitiativeId initiativeId() {
        return initiativeId;
    }

    public InitiativeStatus actualStatus() {
        return actualStatus;
    }
}
