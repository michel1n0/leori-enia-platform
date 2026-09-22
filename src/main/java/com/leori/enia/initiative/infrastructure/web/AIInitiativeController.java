package com.leori.enia.initiative.infrastructure.web;

import com.leori.enia.initiative.application.CreateAIInitiativeCommand;
import com.leori.enia.initiative.application.CreateAIInitiativeUseCase;
import com.leori.enia.initiative.application.GetAIInitiativeUseCase;
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
    private final AIInitiativeETagCodec etags;

    public AIInitiativeController(
            GetAIInitiativeUseCase getAIInitiative,
            CreateAIInitiativeUseCase createAIInitiative,
            SubmitAIInitiativeUseCase submitAIInitiative,
            StartAssessmentAIInitiativeUseCase startAssessmentAIInitiative,
            AIInitiativeETagCodec etags
    ) {
        this.getAIInitiative = getAIInitiative;
        this.createAIInitiative = createAIInitiative;
        this.submitAIInitiative = submitAIInitiative;
        this.startAssessmentAIInitiative = startAssessmentAIInitiative;
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
