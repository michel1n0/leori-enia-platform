package com.leori.enia.initiative.application;

import com.leori.enia.initiative.domain.AIInitiativeId;

public record SubmitAIInitiativeCommand(
        AIInitiativeId initiativeId,
        ExpectedRevision expectedRevision
) {
}
