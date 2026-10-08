package com.leori.enia.evidence.infrastructure.web;

import com.leori.enia.evidence.domain.Evidence;

import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(
        UUID id,
        UUID controlImplementationId,
        String description,
        String reference,
        Instant recordedAt
) {
    static EvidenceResponse from(Evidence evidence) {
        return new EvidenceResponse(
                evidence.id().value(),
                evidence.controlImplementationId().value(),
                evidence.description(),
                evidence.reference(),
                evidence.recordedAt()
        );
    }
}
