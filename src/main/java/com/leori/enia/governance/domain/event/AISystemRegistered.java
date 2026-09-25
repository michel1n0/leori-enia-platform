package com.leori.enia.governance.domain.event;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;

public record AISystemRegistered(
        AISystemId systemId,
        OrganizationId organizationId,
        AIInitiativeId sourceInitiativeId,
        Instant occurredAt
) implements DomainEvent {
}
