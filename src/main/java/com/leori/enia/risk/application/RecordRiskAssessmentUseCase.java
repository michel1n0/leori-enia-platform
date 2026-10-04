package com.leori.enia.risk.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.ContextOfUse;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFinding;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class RecordRiskAssessmentUseCase {

    private final AISystemRepository systemRepository;
    private final RiskAssessmentRepository assessmentRepository;
    private final Clock clock;

    public RecordRiskAssessmentUseCase(
            AISystemRepository systemRepository,
            RiskAssessmentRepository assessmentRepository,
            Clock clock
    ) {
        this.systemRepository = Objects.requireNonNull(
                systemRepository,
                "AI system repository is required"
        );
        this.assessmentRepository = Objects.requireNonNull(
                assessmentRepository,
                "Risk assessment repository is required"
        );
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public RiskAssessment execute(RecordRiskAssessmentCommand command) {
        Objects.requireNonNull(command, "Record risk assessment command is required");

        AISystemId systemId = command.systemId();
        systemRepository.findById(systemId)
                .orElseThrow(() -> new AISystemNotFoundException(systemId));

        Instant assessedAt = clock.instant();
        List<RiskFinding> findings = command.findings() == null
                ? null
                : command.findings().stream()
                        .map(finding -> finding == null
                                ? null
                                : new RiskFinding(
                                        finding.description(),
                                        finding.likelihood(),
                                        finding.impactMagnitude()
                                ))
                        .toList();

        RiskAssessment assessment = RiskAssessment.builder()
                .id(RiskAssessmentId.generate())
                .systemId(systemId)
                .contextOfUse(new ContextOfUse(command.purpose(), command.deploymentContext()))
                .findings(findings)
                .assessedAt(assessedAt)
                .build();

        return assessmentRepository.create(assessment);
    }
}
