package com.leori.enia.evidence.infrastructure.web;

import com.leori.enia.evidence.application.GetEvidenceByControlImplementationUseCase;
import com.leori.enia.evidence.application.GetEvidenceUseCase;
import com.leori.enia.evidence.application.RecordEvidenceCommand;
import com.leori.enia.evidence.application.RecordEvidenceUseCase;
import com.leori.enia.evidence.domain.EvidenceId;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class EvidenceController {

    private final RecordEvidenceUseCase recordEvidence;
    private final GetEvidenceUseCase getEvidence;
    private final GetEvidenceByControlImplementationUseCase getEvidenceByControlImplementation;

    public EvidenceController(
            RecordEvidenceUseCase recordEvidence,
            GetEvidenceUseCase getEvidence,
            GetEvidenceByControlImplementationUseCase getEvidenceByControlImplementation
    ) {
        this.recordEvidence = recordEvidence;
        this.getEvidence = getEvidence;
        this.getEvidenceByControlImplementation = getEvidenceByControlImplementation;
    }

    @GetMapping("/evidence/{id}")
    public ResponseEntity<EvidenceResponse> get(@PathVariable UUID id) {
        var evidence = getEvidence.execute(new EvidenceId(id));
        return ResponseEntity.ok(EvidenceResponse.from(evidence));
    }

    @GetMapping("/control-implementations/{controlImplementationId}/evidence")
    public ResponseEntity<List<EvidenceResponse>> listByControlImplementation(
            @PathVariable UUID controlImplementationId
    ) {
        var evidence = getEvidenceByControlImplementation.execute(new ControlImplementationId(controlImplementationId));

        return ResponseEntity.ok(evidence.stream()
                .map(EvidenceResponse::from)
                .toList());
    }

    @PostMapping("/evidence")
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
