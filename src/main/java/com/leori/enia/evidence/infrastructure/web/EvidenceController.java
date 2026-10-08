package com.leori.enia.evidence.infrastructure.web;

import com.leori.enia.evidence.application.RecordEvidenceCommand;
import com.leori.enia.evidence.application.RecordEvidenceUseCase;
import com.leori.enia.risk.domain.ControlImplementationId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenceController {

    private final RecordEvidenceUseCase recordEvidence;

    public EvidenceController(RecordEvidenceUseCase recordEvidence) {
        this.recordEvidence = recordEvidence;
    }

    @PostMapping
    public ResponseEntity<EvidenceResponse> record(
            @Valid @RequestBody RecordEvidenceRequest request
    ) {
        var recorded = recordEvidence.execute(new RecordEvidenceCommand(
                new ControlImplementationId(request.controlImplementationId()),
                request.description(),
                request.reference()
        ));
        return ResponseEntity
                .created(URI.create("/api/v1/evidence/" + recorded.id().value()))
                .body(EvidenceResponse.from(recorded));
    }
}
