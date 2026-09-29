package com.leori.enia.registry.domain.event;

import com.leori.enia.registry.domain.DatasetId;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;

public record DatasetRegistered(
        DatasetId datasetId,
        Instant occurredAt
) implements DomainEvent {
}
