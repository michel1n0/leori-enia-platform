package com.leori.enia.initiative.infrastructure.web;

import com.leori.enia.initiative.application.ApproveAIInitiativeCommand;
import com.leori.enia.initiative.application.ApproveAIInitiativeUseCase;
import com.leori.enia.initiative.application.AssessRiskAIInitiativeCommand;
import com.leori.enia.initiative.application.AssessRiskAIInitiativeUseCase;
import com.leori.enia.initiative.application.CreateAIInitiativeCommand;
import com.leori.enia.initiative.application.CreateAIInitiativeUseCase;
import com.leori.enia.initiative.application.GetAIInitiativeUseCase;
import com.leori.enia.initiative.application.RejectAIInitiativeCommand;
import com.leori.enia.initiative.application.RejectAIInitiativeUseCase;
import com.leori.enia.initiative.application.StartAssessmentAIInitiativeCommand;
import com.leori.enia.initiative.application.StartAssessmentAIInitiativeUseCase;
import com.leori.enia.initiative.application.SubmitAIInitiativeCommand;
import com.leori.enia.initiative.application.SubmitAIInitiativeUseCase;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-initiatives")
public class AIInitiativeController {

    private final GetAIInitiativeUseCase getAIInitiative;
    private final CreateAIInitiativeUseCase createAIInitiative;
    private final SubmitAIInitiativeUseCase submitAIInitiative;
    private final StartAssessmentAIInitiativeUseCase startAssessmentAIInitiative;
    private final AssessRiskAIInitiativeUseCase assessRiskAIInitiative;
    private final ApproveAIInitiativeUseCase approveAIInitiative;
    private final RejectAIInitiativeUseCase rejectAIInitiative;
    private final AIInitiativeETagCodec etags;

    public AIInitiativeController(
            GetAIInitiativeUseCase getAIInitiative,
            CreateAIInitiativeUseCase createAIInitiative,
            SubmitAIInitiativeUseCase submitAIInitiative,
            StartAssessmentAIInitiativeUseCase startAssessmentAIInitiative,
            AssessRiskAIInitiativeUseCase assessRiskAIInitiative,
            ApproveAIInitiativeUseCase approveAIInitiative,
            RejectAIInitiativeUseCase rejectAIInitiative,
            AIInitiativeETagCodec etags
    ) {
        this.getAIInitiative = getAIInitiative;
        this.createAIInitiative = createAIInitiative;
        this.submitAIInitiative = submitAIInitiative;
        this.startAssessmentAIInitiative = startAssessmentAIInitiative;
        this.assessRiskAIInitiative = assessRiskAIInitiative;
        this.approveAIInitiative = approveAIInitiative;
        this.rejectAIInitiative = rejectAIInitiative;
        this.etags = etags;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AIInitiativeResponse> get(@PathVariable UUID id) {
        var result = getAIInitiative.execute(new AIInitiativeId(id));
        return ResponseEntity.ok()
                .eTag(etags.encode(result.details().id(), result.revision()))
                .body(AIInitiativeResponse.from(result.details()));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<AIInitiativeResponse> submit(@PathVariable UUID id, @RequestHeader HttpHeaders headers) {
        var initiativeId = new AIInitiativeId(id);
        var expected = etags.decode(headers.get(HttpHeaders.IF_MATCH));
        var result = submitAIInitiative.execute(new SubmitAIInitiativeCommand(initiativeId, expected));
        return ResponseEntity.ok()
                .eTag(etags.encode(result.details().id(), result.revision()))
                .body(AIInitiativeResponse.from(result.details()));
    }

    @PostMapping("/{id}/assessment/start")
    public ResponseEntity<AIInitiativeResponse> startAssessment(@PathVariable UUID id, @RequestHeader HttpHeaders headers) {
        var initiativeId = new AIInitiativeId(id);
        var expected = etags.decode(headers.get(HttpHeaders.IF_MATCH));
        var result = startAssessmentAIInitiative.execute(new StartAssessmentAIInitiativeCommand(initiativeId, expected));
        return ResponseEntity.ok()
                .eTag(etags.encode(result.details().id(), result.revision()))
                .body(AIInitiativeResponse.from(result.details()));
    }

    @PostMapping("/{id}/risk-assessment")
    public ResponseEntity<AIInitiativeResponse> assessRisk(
            @PathVariable UUID id,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody AssessRiskAIInitiativeRequest request
    ) {
        var initiativeId = new AIInitiativeId(id);
        var expected = etags.decode(headers.get(HttpHeaders.IF_MATCH));
        var result = assessRiskAIInitiative.execute(
                new AssessRiskAIInitiativeCommand(initiativeId, request.riskLevel(), expected));
        return ResponseEntity.ok()
                .eTag(etags.encode(result.details().id(), result.revision()))
                .body(AIInitiativeResponse.from(result.details()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AIInitiativeResponse> approve(@PathVariable UUID id, @RequestHeader HttpHeaders headers) {
        var initiativeId = new AIInitiativeId(id);
        var expected = etags.decode(headers.get(HttpHeaders.IF_MATCH));
        var result = approveAIInitiative.execute(new ApproveAIInitiativeCommand(initiativeId, expected));
        return ResponseEntity.ok()
                .eTag(etags.encode(result.details().id(), result.revision()))
                .body(AIInitiativeResponse.from(result.details()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AIInitiativeResponse> reject(
            @PathVariable UUID id,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody RejectAIInitiativeRequest request
    ) {
        var initiativeId = new AIInitiativeId(id);
        var expected = etags.decode(headers.get(HttpHeaders.IF_MATCH));
        var result = rejectAIInitiative.execute(
                new RejectAIInitiativeCommand(initiativeId, request.reason(), expected));
        return ResponseEntity.ok()
                .eTag(etags.encode(result.details().id(), result.revision()))
                .body(AIInitiativeResponse.from(result.details()));
    }

    @PostMapping
    public ResponseEntity<AIInitiativeResponse> create(@Valid @RequestBody CreateAIInitiativeRequest request) {
        var created = createAIInitiative.execute(new CreateAIInitiativeCommand(
                new OrganizationId(request.organizationId()),
                request.name(),
                request.description(),
                request.usesPersonalData(),
                request.impactsRights()
        ));
        URI location = URI.create("/api/v1/ai-initiatives/" + created.id().value());
        return ResponseEntity.created(location).body(AIInitiativeResponse.from(created));
    }
}
