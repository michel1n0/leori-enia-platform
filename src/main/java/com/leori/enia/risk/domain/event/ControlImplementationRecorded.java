package com.leori.enia.risk.domain.event;

import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;

public record ControlImplementationRecorded(
        ControlImplementationId controlImplementationId,
        ControlId controlId,
        Instant occurredAt
) implements DomainEvent {
}
