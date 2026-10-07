package com.leori.enia.evidence.application;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.evidence.domain.EvidenceRepository;
import com.leori.enia.risk.application.exception.ControlImplementationNotFoundException;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class RecordEvidenceUseCase {

    private final EvidenceRepository evidenceRepository;
    private final ControlImplementationRepository controlImplementationRepository;
    private final Clock evidenceClock;

    public RecordEvidenceUseCase(
            EvidenceRepository evidenceRepository,
            ControlImplementationRepository controlImplementationRepository,
            Clock evidenceClock
    ) {
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "Evidence repository is required");
        this.controlImplementationRepository = Objects.requireNonNull(
                controlImplementationRepository,
                "Control implementation repository is required"
        );
        this.evidenceClock = Objects.requireNonNull(evidenceClock, "Clock is required");
    }

    public Evidence execute(RecordEvidenceCommand command) {
        Objects.requireNonNull(command, "Record evidence command is required");

        ControlImplementationId controlImplementationId = command.controlImplementationId();
        controlImplementationRepository.findById(controlImplementationId)
                .orElseThrow(() -> new ControlImplementationNotFoundException(controlImplementationId));

        Instant recordedAt = evidenceClock.instant();
        Evidence evidence = Evidence.builder()
                .id(EvidenceId.generate())
                .controlImplementationId(controlImplementationId)
                .description(command.description())
                .reference(command.reference())
                .recordedAt(recordedAt)
                .build();

        return evidenceRepository.create(evidence);
    }
}
