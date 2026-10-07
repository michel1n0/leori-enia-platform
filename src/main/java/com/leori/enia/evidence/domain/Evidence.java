package com.leori.enia.evidence.domain;

import com.leori.enia.evidence.domain.event.EvidenceRecorded;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Evidence recorded for an existing control implementation. */
public final class Evidence {

    private final EvidenceId id;
    private final ControlImplementationId controlImplementationId;
    private final String description;
    private final String reference;
    private final Instant recordedAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Evidence(Builder builder) {
        this(builder.id, builder.controlImplementationId, builder.description, builder.reference, builder.recordedAt);

        domainEvents.add(new EvidenceRecorded(id, controlImplementationId, recordedAt));
    }

    private Evidence(
            EvidenceId id,
            ControlImplementationId controlImplementationId,
            String description,
            String reference,
            Instant recordedAt
    ) {
        this.id = Objects.requireNonNull(id, "Evidence id is required");
        this.controlImplementationId = Objects.requireNonNull(
                controlImplementationId, "Control implementation id is required");
        this.description = requireText(description, "Description is required");
        this.reference = requireText(reference, "Reference is required");
        this.recordedAt = Objects.requireNonNull(recordedAt, "RecordedAt is required");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Restores persisted business state without recording new evidence or emitting events. */
    public static Evidence rehydrate(
            EvidenceId id,
            ControlImplementationId controlImplementationId,
            String description,
            String reference,
            Instant recordedAt
    ) {
        return new Evidence(id, controlImplementationId, description, reference, recordedAt);
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

    public EvidenceId id() {
        return id;
    }

    public ControlImplementationId controlImplementationId() {
        return controlImplementationId;
    }

    public String description() {
        return description;
    }

    public String reference() {
        return reference;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public static final class Builder {

        private EvidenceId id;
        private ControlImplementationId controlImplementationId;
        private String description;
        private String reference;
        private Instant recordedAt;

        private Builder() {
        }

        public Builder id(EvidenceId id) {
            this.id = id;
            return this;
        }

        public Builder controlImplementationId(ControlImplementationId controlImplementationId) {
            this.controlImplementationId = controlImplementationId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public Builder recordedAt(Instant recordedAt) {
            this.recordedAt = recordedAt;
            return this;
        }

        public Evidence build() {
            return new Evidence(this);
        }
    }
}
