package com.leori.enia.evidence.infrastructure.persistence;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.risk.domain.ControlImplementationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidencePersistenceMapperTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T12:30:45.123456Z");
    private final EvidencePersistenceMapper mapper = new EvidencePersistenceMapper();

    @Test
    void maps_aggregate_to_jpa_fields_without_consuming_events() {
        Evidence evidence = evidence();
        var events = evidence.domainEvents();

        EvidenceJpaEntity entity = mapper.toEntity(evidence);

        assertAll(
                () -> assertEquals(evidence.id().value(), entity.id()),
                () -> assertEquals(evidence.controlImplementationId().value(), entity.controlImplementationId()),
                () -> assertEquals(evidence.description(), entity.description()),
                () -> assertEquals(evidence.reference(), entity.reference()),
                () -> assertEquals(RECORDED_AT, entity.recordedAt()),
                () -> assertEquals(1, events.size()),
                () -> assertEquals(events, evidence.domainEvents())
        );
    }

    @Test
    void maps_jpa_to_domain_fields_and_rehydrates_without_events() {
        Evidence original = evidence();
        EvidenceJpaEntity entity = new EvidenceJpaEntity(
                original.id().value(),
                original.controlImplementationId().value(),
                "  Signed approval minutes  ",
                "  evidence-vault:item-123  ",
                RECORDED_AT);

        Evidence restored = mapper.toDomain(entity);

        assertAll(
                () -> assertEquals(original.id(), restored.id()),
                () -> assertEquals(new ControlImplementationId(original.controlImplementationId().value()),
                        restored.controlImplementationId()),
                () -> assertEquals("Signed approval minutes", restored.description()),
                () -> assertEquals("evidence-vault:item-123", restored.reference()),
                () -> assertEquals(RECORDED_AT, restored.recordedAt()),
                () -> assertTrue(restored.domainEvents().isEmpty())
        );
    }

    private Evidence evidence() {
        return Evidence.builder()
                .id(EvidenceId.generate())
                .controlImplementationId(ControlImplementationId.generate())
                .description("Signed approval minutes")
                .reference("evidence-vault:item-123")
                .recordedAt(RECORDED_AT)
                .build();
    }
}
