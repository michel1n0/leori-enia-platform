package com.leori.enia.registry.domain.event;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.domain.AIModelId;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;

public record AIModelRegistered(
        AIModelId modelId,
        AISystemId systemId,
        Instant occurredAt
) implements DomainEvent {
}
