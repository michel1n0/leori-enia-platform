package com.leori.enia.evidence.application;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.evidence.domain.EvidenceRepository;
import com.leori.enia.evidence.domain.event.EvidenceRecorded;
import com.leori.enia.risk.application.exception.ControlImplementationNotFoundException;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RecordEvidenceUseCaseTest {

    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T11:30:45Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T12:45:00Z");
    private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);

    @Test
    void records_evidence_for_existing_control_implementation() {
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(implementation);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
        CountingClock clock = new CountingClock(RECORDED_AT);
        RecordEvidenceUseCase useCase = new RecordEvidenceUseCase(
                evidenceRepository, implementationRepository, clock);

        Evidence result = useCase.execute(command(implementation.id()));

        assertSame(result, evidenceRepository.createdEvidence);
        assertNotNull(result.id());
        assertNotNull(result.id().value());
        assertEquals(implementation.id(), result.controlImplementationId());
        assertEquals("Signed approval memo", result.description());
        assertEquals("s3://evidence-bucket/approval.pdf", result.reference());
        assertEquals(RECORDED_AT, result.recordedAt());
        assertEquals(1, implementationRepository.finds);
        assertEquals(implementation.id(), implementationRepository.lastFindId);
        assertEquals(1, evidenceRepository.creates);
        assertEquals(1, clock.instantCalls);
    }

    @Test
    void generated_evidence_ids_are_new() {
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(implementation);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
        RecordEvidenceUseCase useCase = new RecordEvidenceUseCase(
                evidenceRepository, implementationRepository, CLOCK);

        Evidence first = useCase.execute(command(implementation.id()));
        Evidence second = useCase.execute(command(implementation.id()));

        assertNotNull(first.id());
        assertNotNull(second.id());
        assertNotEquals(first.id(), second.id());
    }

    @Test
    void missing_control_implementation_throws_without_evidence_persistence_or_clock_access() {
        InMemoryControlImplementationRepository implementationRepository = new InMemoryControlImplementationRepository();
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
        Clock clock = mock(Clock.class);
        RecordEvidenceUseCase useCase = new RecordEvidenceUseCase(
                evidenceRepository, implementationRepository, clock);
        ControlImplementationId missingId = ControlImplementationId.generate();

        ControlImplementationNotFoundException exception = assertThrows(
                ControlImplementationNotFoundException.class,
                () -> useCase.execute(command(missingId))
        );

        assertEquals("Control implementation not found: " + missingId, exception.getMessage());
        assertEquals(1, implementationRepository.finds);
        assertEquals(missingId, implementationRepository.lastFindId);
        assertEquals(0, evidenceRepository.creates);
        verifyNoInteractions(clock);
    }

    @Test
    void returns_created_aggregate_from_repository() {
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(implementation);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
        Evidence repositoryResult = Evidence.rehydrate(
                EvidenceId.generate(), implementation.id(), "Repository result", "archive://result", RECORDED_AT);
        evidenceRepository.repositoryResult = repositoryResult;
        RecordEvidenceUseCase useCase = new RecordEvidenceUseCase(
                evidenceRepository, implementationRepository, CLOCK);

        Evidence result = useCase.execute(command(implementation.id()));

        assertSame(repositoryResult, result);
        assertNotNull(evidenceRepository.createdEvidence);
    }

    @Test
    void pending_evidence_recorded_event_is_preserved() {
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(implementation);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
        RecordEvidenceUseCase useCase = new RecordEvidenceUseCase(
                evidenceRepository, implementationRepository, CLOCK);

        Evidence result = useCase.execute(command(implementation.id()));

        assertEquals(1, result.domainEvents().size());
        EvidenceRecorded event = assertInstanceOf(EvidenceRecorded.class, result.domainEvents().getFirst());
        assertEquals(result.id(), event.evidenceId());
        assertEquals(implementation.id(), event.controlImplementationId());
        assertEquals(RECORDED_AT, event.occurredAt());
    }

    @Test
    void invalid_description_propagates_domain_failure_without_persistence() {
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(implementation);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
        CountingClock clock = new CountingClock(RECORDED_AT);
        RecordEvidenceUseCase useCase = new RecordEvidenceUseCase(
                evidenceRepository, implementationRepository, clock);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(new RecordEvidenceCommand(
                        implementation.id(), "   ", "s3://evidence-bucket/approval.pdf")));

        assertEquals("Description is required", exception.getMessage());
        assertEquals(1, implementationRepository.finds);
        assertEquals(1, clock.instantCalls);
        assertEquals(0, evidenceRepository.creates);
    }

    @Test
    void evidence_repository_failure_propagates_unchanged() {
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(implementation);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
        RuntimeException failure = new RuntimeException("Storage unavailable");
        evidenceRepository.failure = failure;
        RecordEvidenceUseCase useCase = new RecordEvidenceUseCase(
                evidenceRepository, implementationRepository, CLOCK);

        assertSame(failure, assertThrows(RuntimeException.class,
                () -> useCase.execute(command(implementation.id()))));

        assertEquals(1, evidenceRepository.creates);
    }

    @Test
    void null_command_fails_before_repository_access() {
        InMemoryControlImplementationRepository implementationRepository = new InMemoryControlImplementationRepository();
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
        Clock clock = mock(Clock.class);
        RecordEvidenceUseCase useCase = new RecordEvidenceUseCase(
                evidenceRepository, implementationRepository, clock);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Record evidence command is required", exception.getMessage());
        assertEquals(0, implementationRepository.finds);
        assertEquals(0, evidenceRepository.creates);
        verifyNoInteractions(clock);
    }

    @Test
    void command_requires_control_implementation_id() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new RecordEvidenceCommand(null, "Description", "Reference"));

        assertEquals("Control implementation id is required", exception.getMessage());
    }

    private RecordEvidenceCommand command(ControlImplementationId controlImplementationId) {
        return new RecordEvidenceCommand(
                controlImplementationId,
                "  Signed approval memo  ",
                "  s3://evidence-bucket/approval.pdf  "
        );
    }

    private ControlImplementation implementation() {
        return ControlImplementation.builder()
                .id(ControlImplementationId.generate())
                .controlId(ControlId.generate())
                .description("Evidence package attached.")
                .implementedAt(IMPLEMENTED_AT)
                .build();
    }

    private static final class InMemoryControlImplementationRepository implements ControlImplementationRepository {
        private final Map<ControlImplementationId, ControlImplementation> implementations = new HashMap<>();
        private ControlImplementationId lastFindId;
        private int finds;

        private InMemoryControlImplementationRepository(ControlImplementation... implementations) {
            for (ControlImplementation implementation : implementations) {
                this.implementations.put(implementation.id(), implementation);
            }
        }

        @Override
        public ControlImplementation create(ControlImplementation implementation) {
            throw new AssertionError("Recording evidence must not create a control implementation");
        }

        @Override
        public Optional<ControlImplementation> findById(ControlImplementationId id) {
            finds++;
            lastFindId = id;
            return Optional.ofNullable(implementations.get(id));
        }
    }

    private static final class InMemoryEvidenceRepository implements EvidenceRepository {
        private Evidence createdEvidence;
        private Evidence repositoryResult;
        private RuntimeException failure;
        private int creates;

        @Override
        public Evidence create(Evidence evidence) {
            creates++;
            createdEvidence = evidence;
            if (failure != null) {
                throw failure;
            }
            return repositoryResult == null ? evidence : repositoryResult;
        }

        @Override
        public Optional<Evidence> findById(EvidenceId id) {
            throw new AssertionError("Recording evidence must not read evidence by id");
        }

        @Override
        public List<Evidence> findByControlImplementationId(ControlImplementationId controlImplementationId) {
            throw new AssertionError("Recording evidence must not list evidence by control implementation");
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
