package com.leori.enia.governance.infrastructure.web;

import com.leori.enia.governance.application.GetAISystemGovernanceSummaryUseCase;
import com.leori.enia.governance.application.GetAISystemUseCase;
import com.leori.enia.governance.application.RegisterAISystemCommand;
import com.leori.enia.governance.application.RegisterAISystemUseCase;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.domain.AIInitiativeId;
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
@RequestMapping("/api/v1/ai-systems")
public class AISystemController {

    private final RegisterAISystemUseCase registerAISystem;
    private final GetAISystemUseCase getAISystem;
    private final GetAISystemGovernanceSummaryUseCase getGovernanceSummary;

    public AISystemController(
            RegisterAISystemUseCase registerAISystem,
            GetAISystemUseCase getAISystem,
            GetAISystemGovernanceSummaryUseCase getGovernanceSummary
    ) {
        this.registerAISystem = registerAISystem;
        this.getAISystem = getAISystem;
        this.getGovernanceSummary = getGovernanceSummary;
    }

    @PostMapping
    public ResponseEntity<AISystemResponse> register(@Valid @RequestBody RegisterAISystemRequest request) {
        var registered = registerAISystem.execute(new RegisterAISystemCommand(
                new AIInitiativeId(request.sourceInitiativeId()),
                request.name(),
                request.description()
        ));
        URI location = URI.create("/api/v1/ai-systems/" + registered.id().value());
        return ResponseEntity.created(location).body(AISystemResponse.from(registered));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AISystemResponse> get(@PathVariable UUID id) {
        var system = getAISystem.execute(new AISystemId(id));
        return ResponseEntity.ok(AISystemResponse.from(system));
    }

    @GetMapping("/{id}/governance-summary")
    public ResponseEntity<AISystemGovernanceSummaryResponse> governanceSummary(@PathVariable UUID id) {
        var summary = getGovernanceSummary.execute(new AISystemId(id));
        return ResponseEntity.ok(AISystemGovernanceSummaryResponse.from(summary));
    }
}
