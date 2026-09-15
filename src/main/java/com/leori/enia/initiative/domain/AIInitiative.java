package com.leori.enia.initiative.domain;

import com.leori.enia.organization.domain.OrganizationId;

import java.time.Instant;
import java.util.Objects;

public final class AIInitiative {

    private final AIInitiativeId id;
    private final OrganizationId organizationId;
    private final String name;
    private final String description;

    private InitiativeStatus status;
    private RiskLevel preliminaryRisk;

    private final boolean usesPersonalData;
    private final boolean impactsRights;

    private final Instant createdAt;

    private AIInitiative(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Initiative id is required");
        this.organizationId = Objects.requireNonNull(
                builder.organizationId,
                "Organization id is required"
        );

        this.name = requireText(builder.name, "Name is required");
        this.description = requireText(
                builder.description,
                "Description is required"
        );

        this.usesPersonalData = builder.usesPersonalData;
        this.impactsRights = builder.impactsRights;

        this.status = InitiativeStatus.DRAFT;
        this.preliminaryRisk = RiskLevel.NOT_ASSESSED;
        this.createdAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public void submit() {
        requireStatus(InitiativeStatus.DRAFT);

        status = InitiativeStatus.SUBMITTED;
    }

    public void startAssessment() {
        requireStatus(InitiativeStatus.SUBMITTED);

        status = InitiativeStatus.UNDER_ASSESSMENT;
    }

    public void assessRisk(RiskLevel riskLevel) {
        requireStatus(InitiativeStatus.UNDER_ASSESSMENT);

        this.preliminaryRisk =
                Objects.requireNonNull(riskLevel, "Risk level is required");

        if (riskLevel == RiskLevel.NOT_ASSESSED) {
            throw new IllegalArgumentException(
                    "Risk assessment must have a valid risk level"
            );
        }

        status = InitiativeStatus.RISK_ASSESSED;
    }

    private void requireStatus(InitiativeStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected initiative status %s but was %s"
                            .formatted(expected, status)
            );
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    public AIInitiativeId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public InitiativeStatus status() {
        return status;
    }

    public RiskLevel preliminaryRisk() {
        return preliminaryRisk;
    }

    public boolean usesPersonalData() {
        return usesPersonalData;
    }

    public boolean impactsRights() {
        return impactsRights;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public static final class Builder {

        private AIInitiativeId id;
        private OrganizationId organizationId;
        private String name;
        private String description;
        private boolean usesPersonalData;
        private boolean impactsRights;

        public Builder id(AIInitiativeId id) {
            this.id = id;
            return this;
        }

        public Builder organizationId(OrganizationId organizationId) {
            this.organizationId = organizationId;
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

        public Builder usesPersonalData(boolean value) {
            this.usesPersonalData = value;
            return this;
        }

        public Builder impactsRights(boolean value) {
            this.impactsRights = value;
            return this;
        }

        public AIInitiative build() {
            return new AIInitiative(this);
        }
    }
}
