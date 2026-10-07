package com.leori.enia.evidence.domain.event;

import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;

public record EvidenceRecorded(
        EvidenceId evidenceId,
        ControlImplementationId controlImplementationId,
        Instant occurredAt
) implements DomainEvent {
}
