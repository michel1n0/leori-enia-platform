package com.leori.enia.risk.domain;

import java.util.Objects;
import java.util.UUID;

public record ControlImplementationId(UUID value) {

    public ControlImplementationId {
        Objects.requireNonNull(value, "Control implementation id is required");
    }

    public static ControlImplementationId generate() {
        return new ControlImplementationId(UUID.randomUUID());
    }

    public static ControlImplementationId of(String value) {
        return new ControlImplementationId(UUID.fromString(value));
    }
}
