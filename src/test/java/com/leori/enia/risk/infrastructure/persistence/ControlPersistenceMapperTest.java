package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPersistenceMapperTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-02T10:15:30.123456Z");
    private final ControlPersistenceMapper mapper = new ControlPersistenceMapper();

    @Test
    void maps_all_business_fields_without_consuming_events() {
        Control control = control();
        var events = control.domainEvents();

        ControlJpaEntity entity = mapper.toEntity(control);

        assertAll(
                () -> assertEquals(control.id().value(), entity.id()),
                () -> assertEquals(control.riskAssessmentId().value(), entity.riskAssessmentId()),
                () -> assertEquals(control.riskFindingId().value(), entity.riskFindingId()),
                () -> assertEquals(control.name(), entity.name()),
                () -> assertEquals(control.description(), entity.description()),
                () -> assertEquals(CREATED_AT, entity.createdAt()),
                () -> assertEquals(1, events.size()),
                () -> assertEquals(events, control.domainEvents())
        );
    }

    @Test
    void restores_all_fields_with_domain_normalization_and_no_events() {
        Control original = control();
        ControlJpaEntity entity = new ControlJpaEntity(
                original.id().value(),
                original.riskAssessmentId().value(),
                original.riskFindingId().value(),
                "  Human review gate  ",
                "  Require documented human approval before deployment.  ",
                CREATED_AT);

        Control restored = mapper.toDomain(entity);

        assertAll(
                () -> assertEquals(original.id(), restored.id()),
                () -> assertEquals(original.riskAssessmentId(), restored.riskAssessmentId()),
                () -> assertEquals(original.riskFindingId(), restored.riskFindingId()),
                () -> assertEquals("Human review gate", restored.name()),
                () -> assertEquals("Require documented human approval before deployment.", restored.description()),
                () -> assertEquals(CREATED_AT, restored.createdAt()),
                () -> assertTrue(restored.domainEvents().isEmpty())
        );
    }

    @Test
    void does_not_bypass_domain_validation_of_persisted_text() {
        Control valid = control();
        var invalidName = new ControlJpaEntity(valid.id().value(), valid.riskAssessmentId().value(),
                valid.riskFindingId().value(), " ", valid.description(), valid.createdAt());
        var invalidDescription = new ControlJpaEntity(valid.id().value(), valid.riskAssessmentId().value(),
                valid.riskFindingId().value(), valid.name(), " ", valid.createdAt());

        assertEquals("Name is required",
                assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidName)).getMessage());
        assertEquals("Description is required",
                assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidDescription)).getMessage());
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
}
