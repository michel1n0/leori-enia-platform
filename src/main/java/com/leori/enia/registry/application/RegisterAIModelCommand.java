package com.leori.enia.registry.application;

import com.leori.enia.governance.domain.AISystemId;

import java.util.Objects;

public record RegisterAIModelCommand(
        AISystemId systemId,
        String name,
        String description,
        String provider
) {

    public RegisterAIModelCommand {
        Objects.requireNonNull(systemId, "System id is required");
    }
}
