package com.leori.enia.risk.infrastructure.web;

import com.leori.enia.risk.application.DefineControlCommand;
import com.leori.enia.risk.application.DefineControlUseCase;
import com.leori.enia.risk.application.GetControlUseCase;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;
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
@RequestMapping("/api/v1/controls")
public class ControlController {

    private final DefineControlUseCase defineControl;
    private final GetControlUseCase getControl;

    public ControlController(DefineControlUseCase defineControl, GetControlUseCase getControl) {
        this.defineControl = defineControl;
        this.getControl = getControl;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ControlResponse> get(@PathVariable UUID id) {
        var control = getControl.execute(new ControlId(id));
        return ResponseEntity.ok(ControlResponse.from(control));
    }

    @PostMapping
    public ResponseEntity<ControlResponse> define(@Valid @RequestBody DefineControlRequest request) {
        var defined = defineControl.execute(new DefineControlCommand(
                new RiskAssessmentId(request.riskAssessmentId()),
                new RiskFindingId(request.riskFindingId()),
                request.name(),
                request.description()
        ));
        return ResponseEntity
                .created(URI.create("/api/v1/controls/" + defined.id().value()))
                .body(ControlResponse.from(defined));
    }
}
