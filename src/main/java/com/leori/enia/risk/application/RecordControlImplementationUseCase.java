package com.leori.enia.risk.application;

import com.leori.enia.risk.application.exception.ControlNotFoundException;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import com.leori.enia.risk.domain.ControlRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class RecordControlImplementationUseCase {

    private final ControlRepository controlRepository;
    private final ControlImplementationRepository controlImplementationRepository;
    private final Clock clock;

    public RecordControlImplementationUseCase(
            ControlRepository controlRepository,
            ControlImplementationRepository controlImplementationRepository,
            Clock clock
    ) {
        this.controlRepository = Objects.requireNonNull(controlRepository, "Control repository is required");
        this.controlImplementationRepository = Objects.requireNonNull(
                controlImplementationRepository,
                "Control implementation repository is required"
        );
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public ControlImplementation execute(RecordControlImplementationCommand command) {
        Objects.requireNonNull(command, "Record control implementation command is required");

        ControlId controlId = command.controlId();
        controlRepository.findById(controlId)
                .orElseThrow(() -> new ControlNotFoundException(controlId));

        Instant implementedAt = clock.instant();
        ControlImplementation implementation = ControlImplementation.builder()
                .id(ControlImplementationId.generate())
                .controlId(controlId)
                .description(command.description())
                .implementedAt(implementedAt)
                .build();

        return controlImplementationRepository.create(implementation);
    }
}
