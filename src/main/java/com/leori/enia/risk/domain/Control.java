package com.leori.enia.risk.domain;

import com.leori.enia.risk.domain.event.ControlDefined;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A governance control defined to address an individual risk finding. */
public final class Control {

    private final ControlId id;
    private final RiskAssessmentId riskAssessmentId;
    private final RiskFindingId riskFindingId;
    private final String name;
    private final String description;
    private final Instant createdAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Control(Builder builder) {
        this(builder.id, builder.riskAssessmentId, builder.riskFindingId,
                builder.name, builder.description, builder.createdAt);

        domainEvents.add(new ControlDefined(id, riskAssessmentId, riskFindingId, createdAt));
    }

    private Control(
            ControlId id,
            RiskAssessmentId riskAssessmentId,
            RiskFindingId riskFindingId,
            String name,
            String description,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "Control id is required");
        this.riskAssessmentId = Objects.requireNonNull(riskAssessmentId, "Risk assessment id is required");
        this.riskFindingId = Objects.requireNonNull(riskFindingId, "Risk finding id is required");
        this.name = requireText(name, "Name is required");
        this.description = requireText(description, "Description is required");
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt is required");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Restores persisted business state without defining a new control or emitting events. */
    public static Control rehydrate(
            ControlId id,
            RiskAssessmentId riskAssessmentId,
            RiskFindingId riskFindingId,
            String name,
            String description,
            Instant createdAt
    ) {
        return new Control(id, riskAssessmentId, riskFindingId, name, description, createdAt);
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

    public ControlId id() {
        return id;
    }

    public RiskAssessmentId riskAssessmentId() {
        return riskAssessmentId;
    }

    public RiskFindingId riskFindingId() {
        return riskFindingId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public static final class Builder {

        private ControlId id;
        private RiskAssessmentId riskAssessmentId;
        private RiskFindingId riskFindingId;
        private String name;
        private String description;
        private Instant createdAt;

        private Builder() {
        }

        public Builder id(ControlId id) {
            this.id = id;
            return this;
        }

        public Builder riskAssessmentId(RiskAssessmentId riskAssessmentId) {
            this.riskAssessmentId = riskAssessmentId;
            return this;
        }

        public Builder riskFindingId(RiskFindingId riskFindingId) {
            this.riskFindingId = riskFindingId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Control build() {
            return new Control(this);
        }
    }
}
