package com.leori.enia.evidence.application;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.evidence.domain.EvidenceRepository;
import com.leori.enia.risk.application.exception.ControlImplementationNotFoundException;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetEvidenceByControlImplementationUseCaseTest {

    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T11:30:45Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T12:45:00Z");

    @Test
    void returns_evidence_for_existing_parent() {
        CallRecorder calls = new CallRecorder();
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(calls, implementation);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository(calls);
        List<Evidence> expected = List.of(evidence(implementation.id(), "Signed approval memo"));
        evidenceRepository.result = expected;
        GetEvidenceByControlImplementationUseCase useCase = new GetEvidenceByControlImplementationUseCase(
                implementationRepository, evidenceRepository);

        List<Evidence> result = useCase.execute(implementation.id());

        assertSame(expected, result);
        assertEquals(implementation.id(), implementationRepository.lastFindId);
        assertEquals(implementation.id(), evidenceRepository.lastControlImplementationId);
        assertEquals(1, implementationRepository.finds);
        assertEquals(1, evidenceRepository.findsByControlImplementation);
    }

    @Test
    void parent_lookup_occurs_before_evidence_query() {
        CallRecorder calls = new CallRecorder();
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(calls, implementation);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository(calls);
        GetEvidenceByControlImplementationUseCase useCase = new GetEvidenceByControlImplementationUseCase(
                implementationRepository, evidenceRepository);

        useCase.execute(implementation.id());

        assertEquals(List.of("controlImplementation.findById", "evidence.findByControlImplementationId"), calls.calls);
    }

    @Test
    void missing_parent_fails() {
        CallRecorder calls = new CallRecorder();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(calls);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository(calls);
        GetEvidenceByControlImplementationUseCase useCase = new GetEvidenceByControlImplementationUseCase(
                implementationRepository, evidenceRepository);
        ControlImplementationId missingId = ControlImplementationId.generate();

        ControlImplementationNotFoundException exception = assertThrows(
                ControlImplementationNotFoundException.class,
                () -> useCase.execute(missingId)
        );

        assertEquals("Control implementation not found: " + missingId, exception.getMessage());
        assertEquals(missingId, implementationRepository.lastFindId);
        assertEquals(1, implementationRepository.finds);
    }

    @Test
    void evidence_repository_is_not_called_when_parent_is_absent() {
        CallRecorder calls = new CallRecorder();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(calls);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository(calls);
        GetEvidenceByControlImplementationUseCase useCase = new GetEvidenceByControlImplementationUseCase(
                implementationRepository, evidenceRepository);

        assertThrows(ControlImplementationNotFoundException.class,
                () -> useCase.execute(ControlImplementationId.generate()));

        assertEquals(0, evidenceRepository.findsByControlImplementation);
        assertEquals(List.of("controlImplementation.findById"), calls.calls);
    }

    @Test
    void existing_parent_with_no_evidence_returns_empty_list() {
        CallRecorder calls = new CallRecorder();
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(calls, implementation);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository(calls);
        List<Evidence> expected = List.of();
        evidenceRepository.result = expected;
        GetEvidenceByControlImplementationUseCase useCase = new GetEvidenceByControlImplementationUseCase(
                implementationRepository, evidenceRepository);

        List<Evidence> result = useCase.execute(implementation.id());

        assertSame(expected, result);
        assertEquals(1, evidenceRepository.findsByControlImplementation);
    }

    @Test
    void null_id_fails_before_repository_access() {
        CallRecorder calls = new CallRecorder();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(calls);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository(calls);
        GetEvidenceByControlImplementationUseCase useCase = new GetEvidenceByControlImplementationUseCase(
                implementationRepository, evidenceRepository);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Control implementation id is required", exception.getMessage());
        assertEquals(0, implementationRepository.finds);
        assertEquals(0, evidenceRepository.findsByControlImplementation);
    }

    @Test
    void evidence_repository_failure_propagates_unchanged() {
        CallRecorder calls = new CallRecorder();
        ControlImplementation implementation = implementation();
        InMemoryControlImplementationRepository implementationRepository =
                new InMemoryControlImplementationRepository(calls, implementation);
        InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository(calls);
        RuntimeException failure = new RuntimeException("Storage unavailable");
        evidenceRepository.failure = failure;
        GetEvidenceByControlImplementationUseCase useCase = new GetEvidenceByControlImplementationUseCase(
                implementationRepository, evidenceRepository);

        assertSame(failure, assertThrows(RuntimeException.class, () -> useCase.execute(implementation.id())));

        assertEquals(1, implementationRepository.finds);
        assertEquals(1, evidenceRepository.findsByControlImplementation);
    }

    private ControlImplementation implementation() {
        return ControlImplementation.builder()
                .id(ControlImplementationId.generate())
                .controlId(ControlId.generate())
                .description("Evidence package attached.")
                .implementedAt(IMPLEMENTED_AT)
                .build();
    }

    private Evidence evidence(ControlImplementationId controlImplementationId, String description) {
        return Evidence.rehydrate(
                EvidenceId.generate(),
                controlImplementationId,
                description,
                "archive://approval.pdf",
                RECORDED_AT
        );
    }

    private static final class CallRecorder {
        private final List<String> calls = new ArrayList<>();

        private void record(String call) {
            calls.add(call);
        }
    }

    private static final class InMemoryControlImplementationRepository implements ControlImplementationRepository {
        private final CallRecorder calls;
        private final Map<ControlImplementationId, ControlImplementation> implementations = new HashMap<>();
        private ControlImplementationId lastFindId;
        private int finds;

        private InMemoryControlImplementationRepository(CallRecorder calls, ControlImplementation... implementations) {
            this.calls = calls;
            for (ControlImplementation implementation : implementations) {
                this.implementations.put(implementation.id(), implementation);
            }
        }

        @Override
        public ControlImplementation create(ControlImplementation implementation) {
            throw new AssertionError("Listing evidence must not create a control implementation");
        }

        @Override
        public Optional<ControlImplementation> findById(ControlImplementationId id) {
            calls.record("controlImplementation.findById");
            finds++;
            lastFindId = id;
            return Optional.ofNullable(implementations.get(id));
        }
    }

    private static final class InMemoryEvidenceRepository implements EvidenceRepository {
        private final CallRecorder calls;
        private List<Evidence> result = List.of();
        private RuntimeException failure;
        private ControlImplementationId lastControlImplementationId;
        private int findsByControlImplementation;

        private InMemoryEvidenceRepository(CallRecorder calls) {
            this.calls = calls;
        }

        @Override
        public Evidence create(Evidence evidence) {
            throw new AssertionError("Listing evidence must not create evidence");
        }

        @Override
        public Optional<Evidence> findById(EvidenceId id) {
            throw new AssertionError("Listing evidence must not read evidence by id");
        }

        @Override
        public List<Evidence> findByControlImplementationId(ControlImplementationId controlImplementationId) {
            calls.record("evidence.findByControlImplementationId");
            findsByControlImplementation++;
            lastControlImplementationId = controlImplementationId;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
