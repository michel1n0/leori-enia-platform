package com.leori.enia.evidence.application;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceRepository;
import com.leori.enia.risk.application.exception.ControlImplementationNotFoundException;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;

import java.util.List;
import java.util.Objects;

public class GetEvidenceByControlImplementationUseCase {

    private final ControlImplementationRepository controlImplementationRepository;
    private final EvidenceRepository evidenceRepository;

    public GetEvidenceByControlImplementationUseCase(
            ControlImplementationRepository controlImplementationRepository,
            EvidenceRepository evidenceRepository
    ) {
        this.controlImplementationRepository = Objects.requireNonNull(
                controlImplementationRepository,
                "Control implementation repository is required"
        );
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "Evidence repository is required");
    }

    public List<Evidence> execute(ControlImplementationId controlImplementationId) {
        Objects.requireNonNull(controlImplementationId, "Control implementation id is required");

        controlImplementationRepository.findById(controlImplementationId)
                .orElseThrow(() -> new ControlImplementationNotFoundException(controlImplementationId));

        return evidenceRepository.findByControlImplementationId(controlImplementationId);
    }
}
