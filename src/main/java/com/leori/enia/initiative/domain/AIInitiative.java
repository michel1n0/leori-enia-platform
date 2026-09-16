package com.leori.enia.initiative.domain;

import com.leori.enia.initiative.domain.event.AIInitiativeRiskAssessed;
import com.leori.enia.initiative.domain.event.AIInitiativeSubmitted;
import com.leori.enia.organization.domain.OrganizationId;
import com.leori.enia.shared.domain.DomainEvent;
import com.leori.enia.initiative.domain.event.AIInitiativeApproved;
import com.leori.enia.initiative.domain.event.AIInitiativeRejected;

  import java.time.Instant;
  import java.util.Objects;

  import java.util.ArrayList;
  import java.util.List;

  public final class AIInitiative {

      private final AIInitiativeId id;
      private final OrganizationId organizationId;
      private final String name;
      private final String description;
      private final List<DomainEvent> domainEvents = new ArrayList<>();

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
          this.createdAt = Objects.requireNonNull(
                  builder.createdAt,
                  "CreatedAt is required"
          );
      }

      public static Builder builder() {
          return new Builder();
      }

      public void submit(Instant ocurredAt) {
          requireStatus(InitiativeStatus.DRAFT);

          status = InitiativeStatus.SUBMITTED;

          registerEvent(
                  new AIInitiativeSubmitted(
                                id,
                                Objects.requireNonNull(ocurredAt)
                  )
          );
      }

      public void startAssessment() {
          requireStatus(InitiativeStatus.SUBMITTED);

          status = InitiativeStatus.UNDER_ASSESSMENT;
      }

      public void assessRisk(RiskLevel riskLevel, Instant occurredAt) {
          requireStatus(InitiativeStatus.UNDER_ASSESSMENT);

          Objects.requireNonNull(riskLevel, "Risk level is required");
          Objects.requireNonNull(occurredAt, "ocurredAt is required");

          if (riskLevel == RiskLevel.NOT_ASSESSED) {
              throw new IllegalArgumentException(
                      "Risk assessment must have a valid risk level"
              );
          }

          preliminaryRisk = riskLevel;
          status = InitiativeStatus.RISK_ASSESSED;

          registerEvent(new AIInitiativeRiskAssessed(id, riskLevel, occurredAt));
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
          private Instant createdAt;

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

          public Builder createdAt(Instant createdAt) {
              this.createdAt = createdAt;
              return this;
          }

          public AIInitiative build() {
              return new AIInitiative(this);
          }
      }

      private void registerEvent(DomainEvent event) {
          domainEvents.add(event);
      }

      public List<DomainEvent> domainEvents() {
          return List.copyOf(domainEvents);
      }

      public void clearDomainEvents() {
          domainEvents.clear();
      }

      public void approve(Instant occurredAt) {
          requireStatus(InitiativeStatus.RISK_ASSESSED);
          Objects.requireNonNull(occurredAt, "OcurredAt is required");

          status = InitiativeStatus.APPROVED;

          registerEvent(
              new AIInitiativeApproved(id, occurredAt)
          );
    }

    public void reject(String reason, Instant occurredAt) {
        requireStatus(InitiativeStatus.RISK_ASSESSED);
        
        if(reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        Objects.requireNonNull(occurredAt, "OccurredAt is required");

        status = InitiativeStatus.REJECTED;

        registerEvent(new AIInitiativeRejected(id, reason.trim(), occurredAt));
  }
}
