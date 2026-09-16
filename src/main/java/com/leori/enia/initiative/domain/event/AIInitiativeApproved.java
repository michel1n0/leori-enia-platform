package com.leori.enia.initiative.domain.event;

import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;

public record AIInitiativeApproved(
        AIInitiativeId initiativeId,
        Instant occurredAt
) implements DomainEvent {
}
