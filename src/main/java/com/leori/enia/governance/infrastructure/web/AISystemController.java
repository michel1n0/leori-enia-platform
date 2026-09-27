package com.leori.enia.governance.infrastructure.web;

import com.leori.enia.governance.application.RegisterAISystemCommand;
import com.leori.enia.governance.application.RegisterAISystemUseCase;
import com.leori.enia.initiative.domain.AIInitiativeId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/ai-systems")
public class AISystemController {

    private final RegisterAISystemUseCase registerAISystem;

    public AISystemController(RegisterAISystemUseCase registerAISystem) {
        this.registerAISystem = registerAISystem;
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
}
