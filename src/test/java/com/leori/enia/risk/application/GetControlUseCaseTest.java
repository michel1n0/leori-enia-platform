package com.leori.enia.risk.application;

import com.leori.enia.risk.application.exception.ControlNotFoundException;
import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlRepository;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetControlUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-02T10:15:30Z");

    @Test
    void returns_existing_control() {
        Control control = control();
        InMemoryControlRepository repository = new InMemoryControlRepository(control);
        GetControlUseCase useCase = new GetControlUseCase(repository);

        Control result = useCase.execute(control.id());

        assertSame(control, result);
        assertEquals(control.id(), repository.lastFoundId);
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void returned_state_is_unchanged() {
        Control control = control();
        InMemoryControlRepository repository = new InMemoryControlRepository(control);
        GetControlUseCase useCase = new GetControlUseCase(repository);

        Control result = useCase.execute(control.id());

        assertAll(
                () -> assertEquals(control.id(), result.id()),
                () -> assertEquals(control.riskAssessmentId(), result.riskAssessmentId()),
                () -> assertEquals(control.riskFindingId(), result.riskFindingId()),
                () -> assertEquals(control.name(), result.name()),
                () -> assertEquals(control.description(), result.description()),
                () -> assertEquals(control.createdAt(), result.createdAt())
        );
    }

    @Test
    void missing_control_fails() {
        InMemoryControlRepository repository = new InMemoryControlRepository();
        GetControlUseCase useCase = new GetControlUseCase(repository);
        ControlId missingId = ControlId.generate();

        ControlNotFoundException exception = assertThrows(
                ControlNotFoundException.class,
                () -> useCase.execute(missingId)
        );

        assertEquals("Control not found: " + missingId, exception.getMessage());
        assertEquals(missingId, repository.lastFoundId);
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void null_id_fails_before_repository_access() {
        InMemoryControlRepository repository = new InMemoryControlRepository();
        GetControlUseCase useCase = new GetControlUseCase(repository);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Control id is required", exception.getMessage());
        assertEquals(0, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void read_does_not_write() {
        Control control = control();
        InMemoryControlRepository repository = new InMemoryControlRepository(control);
        GetControlUseCase useCase = new GetControlUseCase(repository);

        useCase.execute(control.id());

        assertEquals(0, repository.creates);
    }

    private Control control() {
        return Control.builder()
                .id(ControlId.generate())
                .riskAssessmentId(RiskAssessmentId.generate())
                .riskFindingId(RiskFindingId.generate())
                .name("Human review gate")
                .description("Require documented human approval before deployment.")
                .createdAt(CREATED_AT)
                .build();
    }

    private static final class InMemoryControlRepository implements ControlRepository {
        private final Map<ControlId, Control> controls = new HashMap<>();
        private ControlId lastFoundId;
        private int finds;
        private int creates;

        private InMemoryControlRepository(Control... controls) {
            for (Control control : controls) {
                this.controls.put(control.id(), control);
            }
        }

        @Override
        public Control create(Control control) {
            creates++;
            throw new AssertionError("Read use case must not create a control");
        }

        @Override
        public Optional<Control> findById(ControlId id) {
            finds++;
            lastFoundId = id;
            return Optional.ofNullable(controls.get(id));
        }
    }
}
