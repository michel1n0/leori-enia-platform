package com.leori.enia.registry.domain;

import java.util.Objects;
import java.util.UUID;

public record DatasetId(UUID value) {

    public DatasetId {
        Objects.requireNonNull(value, "Dataset id is required");
    }

    public static DatasetId generate() {
        return new DatasetId(UUID.randomUUID());
    }

    public static DatasetId of(String value) {
        return new DatasetId(UUID.fromString(value));
    }
}
