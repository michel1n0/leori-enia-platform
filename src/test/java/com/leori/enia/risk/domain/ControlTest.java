package com.leori.enia.risk.domain;

import com.leori.enia.risk.domain.event.ControlDefined;
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

class ControlTest {

    private static final ControlId CONTROL_ID = ControlId.of("20000000-0000-0000-0000-000000000001");
    private static final RiskAssessmentId ASSESSMENT_ID =
            RiskAssessmentId.of("5dd0fbc2-a933-4ed3-9fb2-13f6b4e2a55f");
    private static final RiskFindingId FINDING_ID = RiskFindingId.of("10000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-10-02T10:15:30.123456Z");

    @Test
    void should_define_a_control_for_a_finding_with_normalized_text_and_one_event() {
        Control control = validBuilder().build();

        assertBusinessState(control);
        assertEquals(1, control.domainEvents().size());
        ControlDefined event = assertInstanceOf(ControlDefined.class, control.domainEvents().getFirst());
        assertAll(
                () -> assertEquals(control.id(), event.controlId()),
                () -> assertEquals(control.riskAssessmentId(), event.riskAssessmentId()),
                () -> assertEquals(control.riskFindingId(), event.riskFindingId()),
                () -> assertEquals(control.createdAt(), event.occurredAt())
        );
    }

    @Test
    void should_require_control_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().id(null).build());

        assertEquals("Control id is required", exception.getMessage());
    }

    @Test
    void should_require_risk_assessment_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().riskAssessmentId(null).build());

        assertEquals("Risk assessment id is required", exception.getMessage());
    }

    @Test
    void should_require_risk_finding_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().riskFindingId(null).build());

        assertEquals("Risk finding id is required", exception.getMessage());
    }

    @Test
    void should_require_created_at() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().createdAt(null).build());

        assertEquals("CreatedAt is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_name_that_is_null_or_blank_after_trimming(String name) {
        var exception = assertThrows(IllegalArgumentException.class, () -> validBuilder().name(name).build());

        assertEquals("Name is required", exception.getMessage());
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
    void should_not_impose_arbitrary_text_length_limits() {
        String name = "n".repeat(300);
        String description = "d".repeat(5000);

        Control control = validBuilder().name(name).description(description).build();

        assertEquals(name, control.name());
        assertEquals(description, control.description());
    }

    @Test
    void should_expose_an_immutable_pending_event_snapshot() {
        Control control = validBuilder().build();
        var snapshot = control.domainEvents();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));
        assertEquals(snapshot, control.domainEvents());
    }

    @Test
    void should_clear_pending_events_without_changing_previous_snapshot_or_business_state() {
        Control control = validBuilder().build();
        var snapshot = control.domainEvents();
        var event = snapshot.getFirst();

        control.clearDomainEvents();

        assertTrue(control.domainEvents().isEmpty());
        assertEquals(1, snapshot.size());
        assertEquals(event, snapshot.getFirst());
        assertBusinessState(control);
    }

    @Test
    void should_rehydrate_state_with_normalized_text_and_no_events() {
        Control control = rehydrateControl();

        assertBusinessState(control);
        assertTrue(control.domainEvents().isEmpty());
    }

    @Test
    void should_require_fields_when_rehydrating() {
        assertAll(
                () -> assertEquals("Control id is required", assertThrows(NullPointerException.class,
                        () -> Control.rehydrate(null, ASSESSMENT_ID, FINDING_ID, "Name", "Description", CREATED_AT))
                        .getMessage()),
                () -> assertEquals("Risk assessment id is required", assertThrows(NullPointerException.class,
                        () -> Control.rehydrate(CONTROL_ID, null, FINDING_ID, "Name", "Description", CREATED_AT))
                        .getMessage()),
                () -> assertEquals("Risk finding id is required", assertThrows(NullPointerException.class,
                        () -> Control.rehydrate(CONTROL_ID, ASSESSMENT_ID, null, "Name", "Description", CREATED_AT))
                        .getMessage()),
                () -> assertEquals("CreatedAt is required", assertThrows(NullPointerException.class,
                        () -> Control.rehydrate(CONTROL_ID, ASSESSMENT_ID, FINDING_ID, "Name", "Description", null))
                        .getMessage())
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_name_that_is_null_or_blank_after_trimming_when_rehydrating(String name) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Control.rehydrate(
                CONTROL_ID, ASSESSMENT_ID, FINDING_ID, name, "Description", CREATED_AT));

        assertEquals("Name is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_description_that_is_null_or_blank_after_trimming_when_rehydrating(String description) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Control.rehydrate(
                CONTROL_ID, ASSESSMENT_ID, FINDING_ID, "Name", description, CREATED_AT));

        assertEquals("Description is required", exception.getMessage());
    }

    @Test
    void should_keep_restored_events_empty_and_immutable_when_inspected_or_cleared() {
        Control control = rehydrateControl();
        var snapshot = control.domainEvents();

        assertTrue(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(
                new ControlDefined(CONTROL_ID, ASSESSMENT_ID, FINDING_ID, CREATED_AT)));
        assertEquals(snapshot, control.domainEvents());

        control.clearDomainEvents();

        assertTrue(snapshot.isEmpty());
        assertTrue(control.domainEvents().isEmpty());
        assertBusinessState(control);
    }

    private Control rehydrateControl() {
        return Control.rehydrate(
                CONTROL_ID,
                ASSESSMENT_ID,
                FINDING_ID,
                "  Human review checkpoint  ",
                "  Require documented human review before production use  ",
                CREATED_AT
        );
    }

    private void assertBusinessState(Control control) {
        assertAll(
                () -> assertEquals(CONTROL_ID, control.id()),
                () -> assertEquals(ASSESSMENT_ID, control.riskAssessmentId()),
                () -> assertEquals(FINDING_ID, control.riskFindingId()),
                () -> assertEquals("Human review checkpoint", control.name()),
                () -> assertEquals("Require documented human review before production use", control.description()),
                () -> assertEquals(CREATED_AT, control.createdAt())
        );
    }

    private Control.Builder validBuilder() {
        return Control.builder()
                .id(CONTROL_ID)
                .riskAssessmentId(ASSESSMENT_ID)
                .riskFindingId(FINDING_ID)
                .name("  Human review checkpoint  ")
                .description("  Require documented human review before production use  ")
                .createdAt(CREATED_AT);
    }
}
