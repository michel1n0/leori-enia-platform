package com.leori.enia.risk.application;

import com.leori.enia.risk.application.exception.ControlNotFoundException;
import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import com.leori.enia.risk.domain.ControlRepository;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;
import com.leori.enia.risk.domain.event.ControlImplementationRecorded;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordControlImplementationUseCaseTest {

    private static final Instant CONTROL_CREATED_AT = Instant.parse("2026-10-02T10:15:30Z");
    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T11:30:45Z");

    @Test
    void records_control_implementation_for_existing_control() {
        Control control = control();
        InMemoryControlRepository controlRepository = new InMemoryControlRepository(control);
        InMemoryControlImplementationRepository implementationRepository = new InMemoryControlImplementationRepository();
        CountingClock clock = new CountingClock(IMPLEMENTED_AT);
        RecordControlImplementationUseCase useCase = new RecordControlImplementationUseCase(
                controlRepository, implementationRepository, clock);

        ControlImplementation result = useCase.execute(command(control.id(), "  Evidence package attached.  "));

        assertSame(result, implementationRepository.createdImplementation);
        assertNotNull(result.id());
        assertEquals(control.id(), result.controlId());
        assertEquals("Evidence package attached.", result.description());
        assertEquals(IMPLEMENTED_AT, result.implementedAt());
        assertEquals(1, controlRepository.finds);
        assertEquals(control.id(), controlRepository.lastFindId);
        assertEquals(1, implementationRepository.creates);
        assertEquals(1, clock.instantCalls);
    }

    @Test
    void missing_control_throws_without_persistence_or_clock_access() {
        InMemoryControlRepository controlRepository = new InMemoryControlRepository();
        InMemoryControlImplementationRepository implementationRepository = new InMemoryControlImplementationRepository();
        CountingClock clock = new CountingClock(IMPLEMENTED_AT);
        RecordControlImplementationUseCase useCase = new RecordControlImplementationUseCase(
                controlRepository, implementationRepository, clock);
        ControlId missingId = ControlId.generate();

        ControlNotFoundException exception = assertThrows(
                ControlNotFoundException.class,
                () -> useCase.execute(command(missingId, "Evidence package attached."))
        );

        assertEquals("Control not found: " + missingId, exception.getMessage());
        assertEquals(1, controlRepository.finds);
        assertEquals(missingId, controlRepository.lastFindId);
        assertEquals(0, implementationRepository.creates);
        assertEquals(0, clock.instantCalls);
    }

    @Test
    void generated_control_implementation_ids_are_new() {
        Control control = control();
        InMemoryControlRepository controlRepository = new InMemoryControlRepository(control);
        InMemoryControlImplementationRepository implementationRepository = new InMemoryControlImplementationRepository();
        RecordControlImplementationUseCase useCase = new RecordControlImplementationUseCase(
                controlRepository, implementationRepository, Clock.fixed(IMPLEMENTED_AT, ZoneOffset.UTC));

        ControlImplementation first = useCase.execute(command(control.id(), "Evidence package attached."));
        ControlImplementation second = useCase.execute(command(control.id(), "Evidence package attached."));

        assertNotNull(first.id());
        assertNotNull(second.id());
        assertNotEquals(first.id(), second.id());
    }

    @Test
    void invalid_description_propagates_domain_failure_without_persistence() {
        Control control = control();
        InMemoryControlRepository controlRepository = new InMemoryControlRepository(control);
        InMemoryControlImplementationRepository implementationRepository = new InMemoryControlImplementationRepository();
        CountingClock clock = new CountingClock(IMPLEMENTED_AT);
        RecordControlImplementationUseCase useCase = new RecordControlImplementationUseCase(
                controlRepository, implementationRepository, clock);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(command(control.id(), "   "))
        );

        assertEquals("Description is required", exception.getMessage());
        assertEquals(1, controlRepository.finds);
        assertEquals(1, clock.instantCalls);
        assertEquals(0, implementationRepository.creates);
    }

    @Test
    void returns_created_aggregate_from_repository() {
        Control control = control();
        InMemoryControlRepository controlRepository = new InMemoryControlRepository(control);
        InMemoryControlImplementationRepository implementationRepository = new InMemoryControlImplementationRepository();
        ControlImplementation repositoryResult = ControlImplementation.rehydrate(
                ControlImplementationId.generate(), control.id(), "Repository result", IMPLEMENTED_AT);
        implementationRepository.repositoryResult = repositoryResult;
        RecordControlImplementationUseCase useCase = new RecordControlImplementationUseCase(
                controlRepository, implementationRepository, Clock.fixed(IMPLEMENTED_AT, ZoneOffset.UTC));

        ControlImplementation result = useCase.execute(command(control.id(), "Evidence package attached."));

        assertSame(repositoryResult, result);
        assertNotNull(implementationRepository.createdImplementation);
    }

    @Test
    void pending_control_implementation_recorded_event_is_preserved() {
        Control control = control();
        InMemoryControlRepository controlRepository = new InMemoryControlRepository(control);
        InMemoryControlImplementationRepository implementationRepository = new InMemoryControlImplementationRepository();
        RecordControlImplementationUseCase useCase = new RecordControlImplementationUseCase(
                controlRepository, implementationRepository, Clock.fixed(IMPLEMENTED_AT, ZoneOffset.UTC));

        ControlImplementation result = useCase.execute(command(control.id(), "Evidence package attached."));

        assertEquals(1, result.domainEvents().size());
        ControlImplementationRecorded event = assertInstanceOf(
                ControlImplementationRecorded.class,
                result.domainEvents().getFirst()
        );
        assertEquals(result.id(), event.controlImplementationId());
        assertEquals(control.id(), event.controlId());
        assertEquals(IMPLEMENTED_AT, event.occurredAt());
    }

    @Test
    void implementation_repository_failure_propagates_unchanged() {
        Control control = control();
        InMemoryControlRepository controlRepository = new InMemoryControlRepository(control);
        InMemoryControlImplementationRepository implementationRepository = new InMemoryControlImplementationRepository();
        RuntimeException failure = new RuntimeException("Storage unavailable");
        implementationRepository.failure = failure;
        RecordControlImplementationUseCase useCase = new RecordControlImplementationUseCase(
                controlRepository, implementationRepository, Clock.fixed(IMPLEMENTED_AT, ZoneOffset.UTC));

        assertSame(failure, assertThrows(RuntimeException.class,
                () -> useCase.execute(command(control.id(), "Evidence package attached."))));

        assertEquals(1, implementationRepository.creates);
    }

    @Test
    void null_command_fails_before_repository_access() {
        InMemoryControlRepository controlRepository = new InMemoryControlRepository();
        InMemoryControlImplementationRepository implementationRepository = new InMemoryControlImplementationRepository();
        CountingClock clock = new CountingClock(IMPLEMENTED_AT);
        RecordControlImplementationUseCase useCase = new RecordControlImplementationUseCase(
                controlRepository, implementationRepository, clock);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Record control implementation command is required", exception.getMessage());
        assertEquals(0, controlRepository.finds);
        assertEquals(0, implementationRepository.creates);
        assertEquals(0, clock.instantCalls);
    }

    private RecordControlImplementationCommand command(ControlId controlId, String description) {
        return new RecordControlImplementationCommand(controlId, description);
    }

    private Control control() {
        return Control.builder()
                .id(ControlId.generate())
                .riskAssessmentId(RiskAssessmentId.generate())
                .riskFindingId(RiskFindingId.generate())
                .name("Human review gate")
                .description("Require documented human approval before deployment.")
                .createdAt(CONTROL_CREATED_AT)
                .build();
    }

    private static final class InMemoryControlRepository implements ControlRepository {
        private final Map<ControlId, Control> controls = new HashMap<>();
        private ControlId lastFindId;
        private int finds;

        private InMemoryControlRepository(Control... controls) {
            for (Control control : controls) {
                this.controls.put(control.id(), control);
            }
        }

        @Override
        public Control create(Control control) {
            throw new AssertionError("Recording a control implementation must not create a control");
        }

        @Override
        public Optional<Control> findById(ControlId id) {
            finds++;
            lastFindId = id;
            return Optional.ofNullable(controls.get(id));
        }
    }

    private static final class InMemoryControlImplementationRepository implements ControlImplementationRepository {
        private ControlImplementation createdImplementation;
        private ControlImplementation repositoryResult;
        private RuntimeException failure;
        private int creates;

        @Override
        public ControlImplementation create(ControlImplementation implementation) {
            creates++;
            createdImplementation = implementation;
            if (failure != null) {
                throw failure;
            }
            return repositoryResult == null ? implementation : repositoryResult;
        }
    }

    private static final class CountingClock extends Clock {
        private final Instant fixedInstant;
        private int instantCalls;

        CountingClock(Instant fixedInstant) {
            this.fixedInstant = fixedInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            instantCalls++;
            return fixedInstant;
        }
    }
}
