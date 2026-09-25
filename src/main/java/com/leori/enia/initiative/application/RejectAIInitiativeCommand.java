package com.leori.enia.initiative.application;

import com.leori.enia.initiative.domain.AIInitiativeId;

public record RejectAIInitiativeCommand(
        AIInitiativeId initiativeId,
        String reason,
        ExpectedRevision expectedRevision
) {
}
