package com.leori.enia.risk.application;

import com.leori.enia.risk.application.exception.RiskAssessmentNotFoundException;
import com.leori.enia.risk.application.exception.RiskFindingNotFoundException;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlRepository;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class DefineControlUseCase {

    private final RiskAssessmentRepository assessmentRepository;
    private final ControlRepository controlRepository;
    private final Clock clock;

    public DefineControlUseCase(
            RiskAssessmentRepository assessmentRepository,
            ControlRepository controlRepository,
            Clock clock
    ) {
        this.assessmentRepository = Objects.requireNonNull(
                assessmentRepository,
                "Risk assessment repository is required"
        );
        this.controlRepository = Objects.requireNonNull(controlRepository, "Control repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public Control execute(DefineControlCommand command) {
        Objects.requireNonNull(command, "Define control command is required");

        RiskAssessmentId assessmentId = command.riskAssessmentId();
        RiskAssessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new RiskAssessmentNotFoundException(assessmentId));
        if (!assessment.containsFinding(command.riskFindingId())) {
            throw new RiskFindingNotFoundException(command.riskFindingId(), assessmentId);
        }

        Instant createdAt = clock.instant();
        Control control = Control.builder()
                .id(ControlId.generate())
                .riskAssessmentId(assessmentId)
                .riskFindingId(command.riskFindingId())
                .name(command.name())
                .description(command.description())
                .createdAt(createdAt)
                .build();

        return controlRepository.create(control);
    }
}
