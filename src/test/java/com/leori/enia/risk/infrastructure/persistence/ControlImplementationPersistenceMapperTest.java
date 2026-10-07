package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlImplementationPersistenceMapperTest {

    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T12:30:45.123456Z");
    private final ControlImplementationPersistenceMapper mapper = new ControlImplementationPersistenceMapper();

    @Test
    void maps_aggregate_to_jpa_fields_without_consuming_events() {
        ControlImplementation implementation = implementation();
        var events = implementation.domainEvents();

        ControlImplementationJpaEntity entity = mapper.toEntity(implementation);

        assertAll(
                () -> assertEquals(implementation.id().value(), entity.id()),
                () -> assertEquals(implementation.controlId().value(), entity.controlId()),
                () -> assertEquals(implementation.description(), entity.description()),
                () -> assertEquals(IMPLEMENTED_AT, entity.implementedAt()),
                () -> assertEquals(1, events.size()),
                () -> assertEquals(events, implementation.domainEvents())
        );
    }

    @Test
    void maps_jpa_to_domain_fields_and_rehydrates_without_events() {
        ControlImplementation original = implementation();
        ControlImplementationJpaEntity entity = new ControlImplementationJpaEntity(
                original.id().value(),
                original.controlId().value(),
                "  Evidence package uploaded and reviewed.  ",
                IMPLEMENTED_AT);

        ControlImplementation restored = mapper.toDomain(entity);

        assertAll(
                () -> assertEquals(original.id(), restored.id()),
                () -> assertEquals(new ControlId(original.controlId().value()), restored.controlId()),
                () -> assertEquals("Evidence package uploaded and reviewed.", restored.description()),
                () -> assertEquals(IMPLEMENTED_AT, restored.implementedAt()),
                () -> assertTrue(restored.domainEvents().isEmpty())
        );
    }

    private ControlImplementation implementation() {
        return ControlImplementation.builder()
                .id(ControlImplementationId.generate())
                .controlId(ControlId.generate())
                .description("Evidence package uploaded and reviewed.")
                .implementedAt(IMPLEMENTED_AT)
                .build();
    }
}
