package com.leori.enia.risk.infrastructure.web;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.application.GetRiskAssessmentUseCase;
import com.leori.enia.risk.application.RecordRiskAssessmentCommand;
import com.leori.enia.risk.application.RecordRiskAssessmentUseCase;
import com.leori.enia.risk.application.RecordRiskFindingCommand;
import com.leori.enia.risk.domain.RiskAssessmentId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/risk-assessments")
public class RiskAssessmentController {

    private final RecordRiskAssessmentUseCase recordRiskAssessment;
    private final GetRiskAssessmentUseCase getRiskAssessment;

    public RiskAssessmentController(
            RecordRiskAssessmentUseCase recordRiskAssessment,
            GetRiskAssessmentUseCase getRiskAssessment
    ) {
        this.recordRiskAssessment = recordRiskAssessment;
        this.getRiskAssessment = getRiskAssessment;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RiskAssessmentResponse> get(@PathVariable UUID id) {
        var assessment = getRiskAssessment.execute(new RiskAssessmentId(id));
        return ResponseEntity.ok(RiskAssessmentResponse.from(assessment));
    }

    @PostMapping
    public ResponseEntity<RiskAssessmentResponse> record(@Valid @RequestBody RecordRiskAssessmentRequest request) {
        var recorded = recordRiskAssessment.execute(new RecordRiskAssessmentCommand(
                new AISystemId(request.systemId()),
                request.purpose(),
                request.deploymentContext(),
                request.findings().stream()
                        .map(finding -> new RecordRiskFindingCommand(
                                finding.description(),
                                finding.likelihood(),
                                finding.impactMagnitude()
                        ))
                        .toList()
        ));
        return ResponseEntity
                .created(URI.create("/api/v1/risk-assessments/" + recorded.id().value()))
                .body(RiskAssessmentResponse.from(recorded));
    }
}
