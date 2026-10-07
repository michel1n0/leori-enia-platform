package com.leori.enia.risk.domain;

import com.leori.enia.risk.domain.event.ControlImplementationRecorded;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlImplementationTest {

    private static final ControlImplementationId IMPLEMENTATION_ID =
            ControlImplementationId.of("30000000-0000-0000-0000-000000000001");
    private static final ControlId CONTROL_ID = ControlId.of("20000000-0000-0000-0000-000000000001");
    private static final Instant IMPLEMENTED_AT = Instant.parse("2026-10-03T11:20:30.123456Z");

    @Test
    void should_record_a_control_implementation_with_normalized_description_and_one_event() {
        ControlImplementation implementation = validBuilder().build();

        assertBusinessState(implementation);
        assertEquals(1, implementation.domainEvents().size());
        ControlImplementationRecorded event = assertInstanceOf(ControlImplementationRecorded.class,
                implementation.domainEvents().getFirst());
        assertAll(
                () -> assertEquals(implementation.id(), event.controlImplementationId()),
                () -> assertEquals(implementation.controlId(), event.controlId()),
                () -> assertEquals(implementation.implementedAt(), event.occurredAt())
        );
    }

    @Test
    void should_trim_leading_and_trailing_whitespace_from_description() {
        ControlImplementation implementation = validBuilder()
                .description("  Documented control implementation  ")
                .build();

        assertEquals("Documented control implementation", implementation.description());
    }

    @Test
    void should_require_control_implementation_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().id(null).build());

        assertEquals("Control implementation id is required", exception.getMessage());
    }

    @Test
    void should_require_control_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().controlId(null).build());

        assertEquals("Control id is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_description_that_is_null_or_blank_after_trimming(String description) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> validBuilder().description(description).build());

        assertEquals("Description is required", exception.getMessage());
    }

    @Test
    void should_require_implemented_at() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().implementedAt(null).build());

        assertEquals("ImplementedAt is required", exception.getMessage());
    }

    @Test
    void should_expose_an_immutable_pending_event_snapshot() {
        ControlImplementation implementation = validBuilder().build();
        var snapshot = implementation.domainEvents();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));
        assertEquals(snapshot, implementation.domainEvents());
    }

    @Test
    void should_clear_pending_events_without_changing_previous_snapshot_or_business_state() {
        ControlImplementation implementation = validBuilder().build();
        var snapshot = implementation.domainEvents();
        var event = snapshot.getFirst();

        implementation.clearDomainEvents();

        assertTrue(implementation.domainEvents().isEmpty());
        assertEquals(1, snapshot.size());
        assertEquals(event, snapshot.getFirst());
        assertBusinessState(implementation);
    }

    @Test
    void should_rehydrate_state_with_normalized_description_and_no_events() {
        ControlImplementation implementation = rehydrateImplementation();

        assertBusinessState(implementation);
        assertTrue(implementation.domainEvents().isEmpty());
    }

    @Test
    void should_require_fields_when_rehydrating() {
        assertAll(
                () -> assertEquals("Control implementation id is required", assertThrows(NullPointerException.class,
                        () -> ControlImplementation.rehydrate(null, CONTROL_ID, "Description", IMPLEMENTED_AT))
                        .getMessage()),
                () -> assertEquals("Control id is required", assertThrows(NullPointerException.class,
                        () -> ControlImplementation.rehydrate(IMPLEMENTATION_ID, null, "Description", IMPLEMENTED_AT))
                        .getMessage()),
                () -> assertEquals("ImplementedAt is required", assertThrows(NullPointerException.class,
                        () -> ControlImplementation.rehydrate(IMPLEMENTATION_ID, CONTROL_ID, "Description", null))
                        .getMessage())
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_description_that_is_null_or_blank_after_trimming_when_rehydrating(String description) {
        var exception = assertThrows(IllegalArgumentException.class, () -> ControlImplementation.rehydrate(
                IMPLEMENTATION_ID, CONTROL_ID, description, IMPLEMENTED_AT));

        assertEquals("Description is required", exception.getMessage());
    }

    @Test
    void should_keep_restored_events_empty_and_immutable_when_inspected_or_cleared() {
        ControlImplementation implementation = rehydrateImplementation();
        var snapshot = implementation.domainEvents();

        assertTrue(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(
                new ControlImplementationRecorded(IMPLEMENTATION_ID, CONTROL_ID, IMPLEMENTED_AT)));
        assertEquals(snapshot, implementation.domainEvents());

        implementation.clearDomainEvents();

        assertTrue(snapshot.isEmpty());
        assertTrue(implementation.domainEvents().isEmpty());
        assertBusinessState(implementation);
    }

    private ControlImplementation rehydrateImplementation() {
        return ControlImplementation.rehydrate(
                IMPLEMENTATION_ID,
                CONTROL_ID,
                "  Implemented documented human review before production use  ",
                IMPLEMENTED_AT
        );
    }

    private void assertBusinessState(ControlImplementation implementation) {
        assertAll(
                () -> assertEquals(IMPLEMENTATION_ID, implementation.id()),
                () -> assertEquals(CONTROL_ID, implementation.controlId()),
                () -> assertEquals("Implemented documented human review before production use",
                        implementation.description()),
                () -> assertEquals(IMPLEMENTED_AT, implementation.implementedAt())
        );
    }

    private ControlImplementation.Builder validBuilder() {
        return ControlImplementation.builder()
                .id(IMPLEMENTATION_ID)
                .controlId(CONTROL_ID)
                .description("  Implemented documented human review before production use  ")
                .implementedAt(IMPLEMENTED_AT);
    }
}
