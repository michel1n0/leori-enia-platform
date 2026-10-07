package com.leori.enia.risk.infrastructure.web;

import com.leori.enia.risk.application.RecordControlImplementationCommand;
import com.leori.enia.risk.application.RecordControlImplementationUseCase;
import com.leori.enia.risk.domain.ControlId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/control-implementations")
public class ControlImplementationController {

    private final RecordControlImplementationUseCase recordControlImplementation;

    public ControlImplementationController(RecordControlImplementationUseCase recordControlImplementation) {
        this.recordControlImplementation = recordControlImplementation;
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
