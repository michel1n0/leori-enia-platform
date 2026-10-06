package com.leori.enia.risk.domain;

import java.util.Objects;
import java.util.UUID;

public record ControlId(UUID value) {

    public ControlId {
        Objects.requireNonNull(value, "Control id is required");
    }

    public static ControlId generate() {
        return new ControlId(UUID.randomUUID());
    }

    public static ControlId of(String value) {
        return new ControlId(UUID.fromString(value));
    }
}
