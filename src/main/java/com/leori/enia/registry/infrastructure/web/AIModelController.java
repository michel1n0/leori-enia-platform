package com.leori.enia.registry.infrastructure.web;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.application.RegisterAIModelCommand;
import com.leori.enia.registry.application.RegisterAIModelUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/ai-models")
public class AIModelController {

    private final RegisterAIModelUseCase registerAIModel;

    public AIModelController(RegisterAIModelUseCase registerAIModel) {
        this.registerAIModel = registerAIModel;
    }

    @PostMapping
    public ResponseEntity<AIModelResponse> register(@Valid @RequestBody RegisterAIModelRequest request) {
        var registered = registerAIModel.execute(new RegisterAIModelCommand(
                new AISystemId(request.systemId()),
                request.name(),
                request.description(),
                request.provider()
        ));
        URI location = URI.create("/api/v1/ai-models/" + registered.id().value());
        return ResponseEntity.created(location).body(AIModelResponse.from(registered));
    }
}
