package com.leori.enia.organization.domain;

import java.util.Objects;
import java.util.UUID;

public record OrganizationId(UUID value) {

    public OrganizationId {
        Objects.requireNonNull(value, "Organization id is required");
    }

    public static OrganizationId generate() {
        return new OrganizationId(UUID.randomUUID());
    }
}
