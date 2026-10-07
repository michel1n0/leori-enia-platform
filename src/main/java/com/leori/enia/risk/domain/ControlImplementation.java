package com.leori.enia.risk.domain;

import com.leori.enia.risk.domain.event.ControlImplementationRecorded;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A historical record that an existing governance control was implemented. */
public final class ControlImplementation {

    private final ControlImplementationId id;
    private final ControlId controlId;
    private final String description;
    private final Instant implementedAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private ControlImplementation(Builder builder) {
        this(builder.id, builder.controlId, builder.description, builder.implementedAt);

        domainEvents.add(new ControlImplementationRecorded(id, controlId, implementedAt));
    }

    private ControlImplementation(
            ControlImplementationId id,
            ControlId controlId,
            String description,
            Instant implementedAt
    ) {
        this.id = Objects.requireNonNull(id, "Control implementation id is required");
        this.controlId = Objects.requireNonNull(controlId, "Control id is required");
        this.description = requireText(description, "Description is required");
        this.implementedAt = Objects.requireNonNull(implementedAt, "ImplementedAt is required");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Restores persisted business state without recording a new implementation or emitting events. */
    public static ControlImplementation rehydrate(
            ControlImplementationId id,
            ControlId controlId,
            String description,
            Instant implementedAt
    ) {
        return new ControlImplementation(id, controlId, description, implementedAt);
    }

    private static String requireText(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    public ControlImplementationId id() {
        return id;
    }

    public ControlId controlId() {
        return controlId;
    }

    public String description() {
        return description;
    }

    public Instant implementedAt() {
        return implementedAt;
    }

    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public static final class Builder {

        private ControlImplementationId id;
        private ControlId controlId;
        private String description;
        private Instant implementedAt;

        private Builder() {
        }

        public Builder id(ControlImplementationId id) {
            this.id = id;
            return this;
        }

        public Builder controlId(ControlId controlId) {
            this.controlId = controlId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder implementedAt(Instant implementedAt) {
            this.implementedAt = implementedAt;
            return this;
        }

        public ControlImplementation build() {
            return new ControlImplementation(this);
        }
    }
}
