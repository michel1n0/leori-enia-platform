package com.leori.enia.governance.domain;

import com.leori.enia.governance.domain.event.AISystemRegistered;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An identifiable AI capability registered for ongoing governance. */
public final class AISystem {

    private final AISystemId id;
    private final OrganizationId organizationId;
    private final AIInitiativeId sourceInitiativeId;
    private final String name;
    private final String description;
    private final AISystemStatus status;
    private final Instant createdAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private AISystem(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "AI system id is required");
        this.organizationId = Objects.requireNonNull(builder.organizationId, "Organization id is required");
        this.sourceInitiativeId = Objects.requireNonNull(builder.sourceInitiativeId, "Source initiative id is required");
        this.name = requireText(builder.name, "Name is required");
        this.description = requireText(builder.description, "Description is required");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "CreatedAt is required");
        this.status = AISystemStatus.REGISTERED;

        domainEvents.add(new AISystemRegistered(id, organizationId, sourceInitiativeId, createdAt));
    }

    public static Builder builder() {
        return new Builder();
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

    public AISystemId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public AIInitiativeId sourceInitiativeId() {
        return sourceInitiativeId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public AISystemStatus status() {
        return status;
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

        private AISystemId id;
        private OrganizationId organizationId;
        private AIInitiativeId sourceInitiativeId;
        private String name;
        private String description;
        private Instant createdAt;

        private Builder() {
        }

        public Builder id(AISystemId id) {
            this.id = id;
            return this;
        }

        public Builder organizationId(OrganizationId organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder sourceInitiativeId(AIInitiativeId sourceInitiativeId) {
            this.sourceInitiativeId = sourceInitiativeId;
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

        public AISystem build() {
            return new AISystem(this);
        }
    }
}
