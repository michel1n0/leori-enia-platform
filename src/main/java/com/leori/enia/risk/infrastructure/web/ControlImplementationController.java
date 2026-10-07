package com.leori.enia.risk.infrastructure.web;

import com.leori.enia.risk.application.GetControlImplementationUseCase;
import com.leori.enia.risk.application.RecordControlImplementationCommand;
import com.leori.enia.risk.application.RecordControlImplementationUseCase;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementationId;
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
@RequestMapping("/api/v1/control-implementations")
public class ControlImplementationController {

    private final RecordControlImplementationUseCase recordControlImplementation;
    private final GetControlImplementationUseCase getControlImplementation;

    public ControlImplementationController(
            RecordControlImplementationUseCase recordControlImplementation,
            GetControlImplementationUseCase getControlImplementation
    ) {
        this.recordControlImplementation = recordControlImplementation;
        this.getControlImplementation = getControlImplementation;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ControlImplementationResponse> get(@PathVariable UUID id) {
        var implementation = getControlImplementation.execute(new ControlImplementationId(id));
        return ResponseEntity.ok(ControlImplementationResponse.from(implementation));
    }

    @PostMapping
    public ResponseEntity<ControlImplementationResponse> record(
            @Valid @RequestBody RecordControlImplementationRequest request
    ) {
        var recorded = recordControlImplementation.execute(new RecordControlImplementationCommand(
                new ControlId(request.controlId()),
                request.description()
        ));
        return ResponseEntity
                .created(URI.create("/api/v1/control-implementations/" + recorded.id().value()))
                .body(ControlImplementationResponse.from(recorded));
    }
}
