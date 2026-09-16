package com.leori.enia.shared.domain;

import java.time.Instant;

public interface DomainEvent {

    Instant occurredAt();
}
