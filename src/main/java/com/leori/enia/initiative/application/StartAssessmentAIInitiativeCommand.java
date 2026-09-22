package com.leori.enia.initiative.application;

import com.leori.enia.initiative.domain.AIInitiativeId;

public record StartAssessmentAIInitiativeCommand(
        AIInitiativeId initiativeId,
        ExpectedRevision expectedRevision
) {
}
