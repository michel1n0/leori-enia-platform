package com.leori.enia.evidence.application;

import com.leori.enia.risk.domain.ControlImplementationId;

import java.util.Objects;

public record RecordEvidenceCommand(
        ControlImplementationId controlImplementationId,
        String description,
        String reference
) {

    public RecordEvidenceCommand {
        Objects.requireNonNull(controlImplementationId, "Control implementation id is required");
    }
}
