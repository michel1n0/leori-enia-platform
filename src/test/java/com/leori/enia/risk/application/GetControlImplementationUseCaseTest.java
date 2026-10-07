package com.leori.enia.risk.application;

import com.leori.enia.risk.application.exception.ControlImplementationNotFoundException;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetControlImplementationUseCaseTest {

    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T11:30:45Z");

    @Test
    void returns_existing_control_implementation() {
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository repository = new InMemoryControlImplementationRepository(implementation);
        GetControlImplementationUseCase useCase = new GetControlImplementationUseCase(repository);

        ControlImplementation result = useCase.execute(implementation.id());

        assertSame(implementation, result);
        assertEquals(implementation.id(), repository.lastFoundId);
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void returned_state_is_unchanged() {
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository repository = new InMemoryControlImplementationRepository(implementation);
        GetControlImplementationUseCase useCase = new GetControlImplementationUseCase(repository);

        ControlImplementation result = useCase.execute(implementation.id());

        assertAll(
                () -> assertEquals(implementation.id(), result.id()),
                () -> assertEquals(implementation.controlId(), result.controlId()),
                () -> assertEquals(implementation.description(), result.description()),
                () -> assertEquals(implementation.implementedAt(), result.implementedAt())
        );
    }

    @Test
    void missing_control_implementation_fails() {
        InMemoryControlImplementationRepository repository = new InMemoryControlImplementationRepository();
        GetControlImplementationUseCase useCase = new GetControlImplementationUseCase(repository);
        ControlImplementationId missingId = ControlImplementationId.generate();

        ControlImplementationNotFoundException exception = assertThrows(
                ControlImplementationNotFoundException.class,
                () -> useCase.execute(missingId)
        );

        assertEquals("Control implementation not found: " + missingId, exception.getMessage());
        assertEquals(missingId, repository.lastFoundId);
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void null_id_fails_before_repository_access() {
        InMemoryControlImplementationRepository repository = new InMemoryControlImplementationRepository();
        GetControlImplementationUseCase useCase = new GetControlImplementationUseCase(repository);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Control implementation id is required", exception.getMessage());
        assertEquals(0, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void read_does_not_write_or_emit_events() {
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository repository = new InMemoryControlImplementationRepository(implementation);
        GetControlImplementationUseCase useCase = new GetControlImplementationUseCase(repository);

        ControlImplementation result = useCase.execute(implementation.id());

        assertEquals(0, repository.creates);
        assertEquals(0, result.domainEvents().size());
    }

    private ControlImplementation implementation() {
        return ControlImplementation.rehydrate(
                ControlImplementationId.generate(),
                ControlId.generate(),
                "Evidence package attached.",
                IMPLEMENTED_AT
        );
    }

    private static final class InMemoryControlImplementationRepository implements ControlImplementationRepository {
        private final Map<ControlImplementationId, ControlImplementation> implementations = new HashMap<>();
        private ControlImplementationId lastFoundId;
        private int finds;
        private int creates;

        private InMemoryControlImplementationRepository(ControlImplementation... implementations) {
            for (ControlImplementation implementation : implementations) {
                this.implementations.put(implementation.id(), implementation);
            }
        }

        @Override
        public ControlImplementation create(ControlImplementation implementation) {
            creates++;
            throw new AssertionError("Read use case must not create a control implementation");
        }

        @Override
        public Optional<ControlImplementation> findById(ControlImplementationId id) {
            finds++;
            lastFoundId = id;
            return Optional.ofNullable(implementations.get(id));
        }
    }
}
