package com.leori.enia.initiative.infrastructure.web;

import com.leori.enia.initiative.application.GetAIInitiativeUseCase;
import com.leori.enia.initiative.domain.AIInitiativeId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-initiatives")
public class AIInitiativeController {

    private final GetAIInitiativeUseCase getAIInitiative;

    public AIInitiativeController(GetAIInitiativeUseCase getAIInitiative) {
        this.getAIInitiative = getAIInitiative;
    }

    @GetMapping("/{id}")
    public AIInitiativeResponse get(@PathVariable UUID id) {
        return AIInitiativeResponse.from(getAIInitiative.execute(new AIInitiativeId(id)));
    }
}
