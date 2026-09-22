package com.leori.enia.initiative.infrastructure.web;

import com.leori.enia.initiative.application.CreateAIInitiativeCommand;
import com.leori.enia.initiative.application.CreateAIInitiativeUseCase;
import com.leori.enia.initiative.application.GetAIInitiativeUseCase;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-initiatives")
public class AIInitiativeController {

    private final GetAIInitiativeUseCase getAIInitiative;
    private final CreateAIInitiativeUseCase createAIInitiative;

    public AIInitiativeController(
            GetAIInitiativeUseCase getAIInitiative,
            CreateAIInitiativeUseCase createAIInitiative
    ) {
        this.getAIInitiative = getAIInitiative;
        this.createAIInitiative = createAIInitiative;
    }

    @GetMapping("/{id}")
    public AIInitiativeResponse get(@PathVariable UUID id) {
        return AIInitiativeResponse.from(getAIInitiative.execute(new AIInitiativeId(id)));
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
