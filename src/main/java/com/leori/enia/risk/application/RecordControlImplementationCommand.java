package com.leori.enia.risk.application;

import com.leori.enia.risk.domain.ControlId;

import java.util.Objects;

public record RecordControlImplementationCommand(
        ControlId controlId,
        String description
) {

    public RecordControlImplementationCommand {
        Objects.requireNonNull(controlId, "Control id is required");
    }
}
