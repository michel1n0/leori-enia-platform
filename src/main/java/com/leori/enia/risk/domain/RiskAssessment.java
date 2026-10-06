package com.leori.enia.risk.domain;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.domain.event.RiskAssessmentRecorded;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A completed risk assessment for an AI system in a specific context of use. */
public final class RiskAssessment {

    private final RiskAssessmentId id;
    private final AISystemId systemId;
    private final ContextOfUse contextOfUse;
    private final List<RiskFinding> findings;
    private final Instant assessedAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private RiskAssessment(Builder builder) {
        this(
                builder.id,
                builder.systemId,
                builder.contextOfUse,
                builder.findings,
                builder.assessedAt
        );

        domainEvents.add(new RiskAssessmentRecorded(id, systemId, assessedAt));
    }

    private RiskAssessment(
            RiskAssessmentId id,
            AISystemId systemId,
            ContextOfUse contextOfUse,
            List<RiskFinding> findings,
            Instant assessedAt
    ) {
        this.id = Objects.requireNonNull(id, "Risk assessment id is required");
        this.systemId = Objects.requireNonNull(systemId, "AI system id is required");
        this.contextOfUse = normalizeContextOfUse(contextOfUse);
        this.findings = normalizeFindings(findings);
        this.assessedAt = Objects.requireNonNull(assessedAt, "AssessedAt is required");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Restores persisted business state without recording a new assessment or emitting events. */
    public static RiskAssessment rehydrate(
            RiskAssessmentId id,
            AISystemId systemId,
            ContextOfUse contextOfUse,
            List<RiskFinding> findings,
            Instant assessedAt
    ) {
        return new RiskAssessment(id, systemId, contextOfUse, findings, assessedAt);
    }

    private static ContextOfUse normalizeContextOfUse(ContextOfUse contextOfUse) {
        Objects.requireNonNull(contextOfUse, "Context of use is required");
        return new ContextOfUse(contextOfUse.purpose(), contextOfUse.deploymentContext());
    }

    private static List<RiskFinding> normalizeFindings(List<RiskFinding> findings) {
        Objects.requireNonNull(findings, "Findings are required");
        if (findings.isEmpty()) {
            throw new IllegalArgumentException("At least one risk finding is required");
        }
        List<RiskFinding> normalized = new ArrayList<>(findings.size());
        Set<RiskFindingId> findingIds = new HashSet<>();
        for (RiskFinding finding : findings) {
            Objects.requireNonNull(finding, "Risk finding is required");
            if (!findingIds.add(finding.id())) {
                throw new IllegalArgumentException("Risk finding ids must be unique within an assessment");
            }
            normalized.add(new RiskFinding(
                    finding.id(),
                    finding.description(),
                    finding.likelihood(),
                    finding.impactMagnitude()
            ));
        }
        return List.copyOf(normalized);
    }

    public RiskAssessmentId id() {
        return id;
    }

    public AISystemId systemId() {
        return systemId;
    }

    public ContextOfUse contextOfUse() {
        return contextOfUse;
    }

    public List<RiskFinding> findings() {
        return findings;
    }

    public Instant assessedAt() {
        return assessedAt;
    }

    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public static final class Builder {

        private RiskAssessmentId id;
        private AISystemId systemId;
        private ContextOfUse contextOfUse;
        private List<RiskFinding> findings;
        private Instant assessedAt;

        private Builder() {
        }

        public Builder id(RiskAssessmentId id) {
            this.id = id;
            return this;
        }

        public Builder systemId(AISystemId systemId) {
            this.systemId = systemId;
            return this;
        }

        public Builder contextOfUse(ContextOfUse contextOfUse) {
            this.contextOfUse = contextOfUse;
            return this;
        }

        public Builder findings(List<RiskFinding> findings) {
            this.findings = findings;
            return this;
        }

        public Builder assessedAt(Instant assessedAt) {
            this.assessedAt = assessedAt;
            return this;
        }

        public RiskAssessment build() {
            return new RiskAssessment(this);
        }
    }
}
