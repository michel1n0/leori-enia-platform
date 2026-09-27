package com.leori.enia.governance.application;

import com.leori.enia.initiative.domain.AIInitiativeId;

import java.util.Objects;

public record RegisterAISystemCommand(
        AIInitiativeId sourceInitiativeId,
        String name,
        String description
) {

    public RegisterAISystemCommand {
        Objects.requireNonNull(sourceInitiativeId, "Source initiative id is required");
    }
}
