package com.leori.enia.governance.infrastructure.web;

import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemStatus;

import java.time.Instant;
import java.util.UUID;

public record AISystemResponse(
        UUID id,
        UUID organizationId,
        UUID sourceInitiativeId,
        String name,
        String description,
        AISystemStatus status,
        Instant createdAt
) {
    static AISystemResponse from(AISystem system) {
        return new AISystemResponse(
                system.id().value(),
                system.organizationId().value(),
                system.sourceInitiativeId().value(),
                system.name(),
                system.description(),
                system.status(),
                system.createdAt()
        );
    }
}
