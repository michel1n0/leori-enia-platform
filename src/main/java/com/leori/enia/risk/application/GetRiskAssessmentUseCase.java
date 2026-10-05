package com.leori.enia.risk.application;

import com.leori.enia.risk.application.exception.RiskAssessmentNotFoundException;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;

import java.util.Objects;

public class GetRiskAssessmentUseCase {

    private final RiskAssessmentRepository repository;

    public GetRiskAssessmentUseCase(RiskAssessmentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Risk assessment repository is required");
    }

    public RiskAssessment execute(RiskAssessmentId id) {
        Objects.requireNonNull(id, "Risk assessment id is required");
        return repository.findById(id)
                .orElseThrow(() -> new RiskAssessmentNotFoundException(id));
    }
}
