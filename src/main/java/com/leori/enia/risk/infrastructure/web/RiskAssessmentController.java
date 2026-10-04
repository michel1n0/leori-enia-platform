package com.leori.enia.risk.infrastructure.web;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.application.RecordRiskAssessmentCommand;
import com.leori.enia.risk.application.RecordRiskAssessmentUseCase;
import com.leori.enia.risk.application.RecordRiskFindingCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/risk-assessments")
public class RiskAssessmentController {

    private final RecordRiskAssessmentUseCase recordRiskAssessment;

    public RiskAssessmentController(RecordRiskAssessmentUseCase recordRiskAssessment) {
        this.recordRiskAssessment = recordRiskAssessment;
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
