package com.leori.enia.evidence.application;

import com.leori.enia.evidence.application.exception.EvidenceNotFoundException;
import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.evidence.domain.EvidenceRepository;
import com.leori.enia.risk.domain.ControlImplementationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetEvidenceUseCaseTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T12:45:00Z");

    @Test
    void returns_existing_evidence() {
        Evidence evidence = evidence();
        InMemoryEvidenceRepository repository = new InMemoryEvidenceRepository(evidence);
        GetEvidenceUseCase useCase = new GetEvidenceUseCase(repository);

        Evidence result = useCase.execute(evidence.id());

        assertSame(evidence, result);
        assertEquals(evidence.id(), repository.lastFindId);
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void returned_state_is_repository_result() {
        Evidence evidence = evidence();
        InMemoryEvidenceRepository repository = new InMemoryEvidenceRepository(evidence);
        GetEvidenceUseCase useCase = new GetEvidenceUseCase(repository);

        Evidence result = useCase.execute(evidence.id());

        assertAll(
                () -> assertSame(evidence, result),
                () -> assertEquals(evidence.id(), result.id()),
                () -> assertEquals(evidence.controlImplementationId(), result.controlImplementationId()),
                () -> assertEquals(evidence.description(), result.description()),
                () -> assertEquals(evidence.reference(), result.reference()),
                () -> assertEquals(evidence.recordedAt(), result.recordedAt())
        );
    }

    @Test
    void missing_evidence_fails() {
        InMemoryEvidenceRepository repository = new InMemoryEvidenceRepository();
        GetEvidenceUseCase useCase = new GetEvidenceUseCase(repository);
        EvidenceId missingId = EvidenceId.generate();

        EvidenceNotFoundException exception = assertThrows(
                EvidenceNotFoundException.class,
                () -> useCase.execute(missingId)
        );

        assertEquals("Evidence not found: " + missingId, exception.getMessage());
        assertEquals(missingId, repository.lastFindId);
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void repository_failure_propagates_unchanged() {
        InMemoryEvidenceRepository repository = new InMemoryEvidenceRepository();
        RuntimeException failure = new RuntimeException("Storage unavailable");
        repository.failure = failure;
        GetEvidenceUseCase useCase = new GetEvidenceUseCase(repository);

        assertSame(failure, assertThrows(RuntimeException.class, () -> useCase.execute(EvidenceId.generate())));

        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void null_id_fails_before_repository_access() {
        InMemoryEvidenceRepository repository = new InMemoryEvidenceRepository();
        GetEvidenceUseCase useCase = new GetEvidenceUseCase(repository);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Evidence id is required", exception.getMessage());
        assertEquals(0, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void read_does_not_write_or_mutate_events() {
        Evidence evidence = evidence();
        var events = evidence.domainEvents();
        InMemoryEvidenceRepository repository = new InMemoryEvidenceRepository(evidence);
        GetEvidenceUseCase useCase = new GetEvidenceUseCase(repository);

        useCase.execute(evidence.id());

        assertEquals(0, repository.creates);
        assertEquals(events, evidence.domainEvents());
    }

    private Evidence evidence() {
        return Evidence.rehydrate(
                EvidenceId.generate(),
                ControlImplementationId.generate(),
                "Signed approval memo",
                "archive://approval.pdf",
                RECORDED_AT
        );
    }

    private static final class InMemoryEvidenceRepository implements EvidenceRepository {
        private final Map<EvidenceId, Evidence> evidenceById = new HashMap<>();
        private EvidenceId lastFindId;
        private RuntimeException failure;
        private int finds;
        private int creates;

        private InMemoryEvidenceRepository(Evidence... evidence) {
            for (Evidence item : evidence) {
                evidenceById.put(item.id(), item);
            }
        }

        @Override
        public Evidence create(Evidence evidence) {
            creates++;
            throw new AssertionError("Read use case must not create evidence");
        }

        @Override
        public Optional<Evidence> findById(EvidenceId id) {
            finds++;
            lastFindId = id;
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(evidenceById.get(id));
        }
    }
}
