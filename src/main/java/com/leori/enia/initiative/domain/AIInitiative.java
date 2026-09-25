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
      private String rejectionReason;

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

      /**
       * Restores persisted state without events. A REJECTED initiative with a
       * null reason represents a historical rejection whose explanation was
       * not stored; new rejections must always supply a valid reason.
       */
      public static AIInitiative rehydrate(
              AIInitiativeId id,
              OrganizationId organizationId,
              String name,
              String description,
              InitiativeStatus status,
              RiskLevel preliminaryRisk,
              boolean usesPersonalData,
              boolean impactsRights,
              Instant createdAt,
              String rejectionReason
      ) {
          validateRestoredLifecycle(status, preliminaryRisk);
          if (status != InitiativeStatus.REJECTED && rejectionReason != null) {
              throw new IllegalArgumentException("Rejection reason requires REJECTED status");
          }
          String restoredReason = rejectionReason == null
                  ? null : normalizeRejectionReason(rejectionReason);

          AIInitiative initiative = builder()
                  .id(id)
                  .organizationId(organizationId)
                  .name(name)
                  .description(description)
                  .usesPersonalData(usesPersonalData)
                  .impactsRights(impactsRights)
                  .createdAt(createdAt)
                  .build();

          initiative.status = status;
          initiative.preliminaryRisk = preliminaryRisk;
          initiative.rejectionReason = restoredReason;

          return initiative;
      }

      public void submit(Instant occurredAt) {
          requireStatus(InitiativeStatus.DRAFT);
          Objects.requireNonNull(occurredAt, "OccurredAt is required");

          status = InitiativeStatus.SUBMITTED;

          registerEvent(
                  new AIInitiativeSubmitted(
                                id,
                                occurredAt
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
          Objects.requireNonNull(occurredAt, "OccurredAt is required");

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

      private static void validateRestoredLifecycle(
              InitiativeStatus status,
              RiskLevel preliminaryRisk
      ) {
          Objects.requireNonNull(status, "Initiative status is required");
          Objects.requireNonNull(preliminaryRisk, "Preliminary risk is required");

          switch (status) {
              case DRAFT, SUBMITTED, UNDER_ASSESSMENT -> {
                  if (preliminaryRisk != RiskLevel.NOT_ASSESSED) {
                      throw new IllegalArgumentException(
                              "Preliminary risk must be NOT_ASSESSED for status "
                                      + status
                      );
                  }
              }
              case RISK_ASSESSED, APPROVED, REJECTED -> {
                  if (preliminaryRisk == RiskLevel.NOT_ASSESSED) {
                      throw new IllegalArgumentException(
                              "Preliminary risk must be assessed for status "
                                      + status
                      );
                  }
              }
              case EXPERIMENTATION, READY_FOR_DEPLOYMENT, ACTIVE, SUSPENDED,
                   RETIRED -> throw new IllegalArgumentException(
                      "Unsupported initiative status for rehydration: " + status
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

      public String rejectionReason() {
          return rejectionReason;
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
          Objects.requireNonNull(occurredAt, "OccurredAt is required");

          status = InitiativeStatus.APPROVED;

          registerEvent(
              new AIInitiativeApproved(id, occurredAt)
          );
    }

    public void reject(String reason, Instant occurredAt) {
        requireStatus(InitiativeStatus.RISK_ASSESSED);
        String normalizedReason = normalizeRejectionReason(reason);
        Objects.requireNonNull(occurredAt, "OccurredAt is required");
        AIInitiativeRejected event = new AIInitiativeRejected(id, normalizedReason, occurredAt);

        rejectionReason = normalizedReason;
        status = InitiativeStatus.REJECTED;

        registerEvent(event);
  }

    private static String normalizeRejectionReason(String reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        return normalizedReason;
  }
}
