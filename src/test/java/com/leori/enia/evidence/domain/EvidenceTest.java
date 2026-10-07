package com.leori.enia.evidence.domain;

import com.leori.enia.evidence.domain.event.EvidenceRecorded;
import com.leori.enia.risk.domain.ControlImplementationId;
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

class EvidenceTest {

    private static final EvidenceId EVIDENCE_ID = EvidenceId.of("40000000-0000-0000-0000-000000000001");
    private static final ControlImplementationId IMPLEMENTATION_ID =
            ControlImplementationId.of("30000000-0000-0000-0000-000000000001");
    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T12:30:45.123456Z");

    @Test
    void should_record_evidence_with_normalized_text_and_one_event() {
        Evidence evidence = validBuilder().build();

        assertBusinessState(evidence);
        assertEquals(1, evidence.domainEvents().size());
        EvidenceRecorded event = assertInstanceOf(EvidenceRecorded.class, evidence.domainEvents().getFirst());
        assertAll(
                () -> assertEquals(evidence.id(), event.evidenceId()),
                () -> assertEquals(evidence.controlImplementationId(), event.controlImplementationId()),
                () -> assertEquals(evidence.recordedAt(), event.occurredAt())
        );
    }

    @Test
    void should_trim_leading_and_trailing_whitespace_from_description() {
        Evidence evidence = validBuilder()
                .description("  Signed approval minutes  ")
                .build();

        assertEquals("Signed approval minutes", evidence.description());
    }

    @Test
    void should_trim_leading_and_trailing_whitespace_from_reference() {
        Evidence evidence = validBuilder()
                .reference("  evidence-vault:item-123  ")
                .build();

        assertEquals("evidence-vault:item-123", evidence.reference());
    }

    @Test
    void should_require_evidence_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().id(null).build());

        assertEquals("Evidence id is required", exception.getMessage());
    }

    @Test
    void should_require_control_implementation_id() {
        var exception = assertThrows(NullPointerException.class,
                () -> validBuilder().controlImplementationId(null).build());

        assertEquals("Control implementation id is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u2003", "\t\u2003"})
    void should_reject_description_that_is_null_or_blank_after_trimming(String description) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> validBuilder().description(description).build());

        assertEquals("Description is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u2003", "\t\u2003"})
    void should_reject_reference_that_is_null_or_blank_after_trimming(String reference) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> validBuilder().reference(reference).build());

        assertEquals("Reference is required", exception.getMessage());
    }

    @Test
    void should_require_recorded_at() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().recordedAt(null).build());

        assertEquals("RecordedAt is required", exception.getMessage());
    }

    @Test
    void should_expose_an_immutable_pending_event_snapshot() {
        Evidence evidence = validBuilder().build();
        var snapshot = evidence.domainEvents();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));
        assertEquals(snapshot, evidence.domainEvents());
    }

    @Test
    void should_clear_pending_events_without_changing_previous_snapshot_or_business_state() {
        Evidence evidence = validBuilder().build();
        var snapshot = evidence.domainEvents();
        var event = snapshot.getFirst();

        evidence.clearDomainEvents();

        assertTrue(evidence.domainEvents().isEmpty());
        assertEquals(1, snapshot.size());
        assertEquals(event, snapshot.getFirst());
        assertBusinessState(evidence);
    }

    @Test
    void should_rehydrate_state_with_normalized_text_and_no_events() {
        Evidence evidence = rehydrateEvidence();

        assertBusinessState(evidence);
        assertTrue(evidence.domainEvents().isEmpty());
    }

    @Test
    void should_require_fields_when_rehydrating() {
        assertAll(
                () -> assertEquals("Evidence id is required", assertThrows(NullPointerException.class,
                        () -> Evidence.rehydrate(null, IMPLEMENTATION_ID, "Description", "Reference", RECORDED_AT))
                        .getMessage()),
                () -> assertEquals("Control implementation id is required", assertThrows(NullPointerException.class,
                        () -> Evidence.rehydrate(EVIDENCE_ID, null, "Description", "Reference", RECORDED_AT))
                        .getMessage()),
                () -> assertEquals("RecordedAt is required", assertThrows(NullPointerException.class,
                        () -> Evidence.rehydrate(EVIDENCE_ID, IMPLEMENTATION_ID, "Description", "Reference", null))
                        .getMessage())
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u2003", "\t\u2003"})
    void should_reject_description_that_is_null_or_blank_after_trimming_when_rehydrating(String description) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Evidence.rehydrate(
                EVIDENCE_ID, IMPLEMENTATION_ID, description, "Reference", RECORDED_AT));

        assertEquals("Description is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u2003", "\t\u2003"})
    void should_reject_reference_that_is_null_or_blank_after_trimming_when_rehydrating(String reference) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Evidence.rehydrate(
                EVIDENCE_ID, IMPLEMENTATION_ID, "Description", reference, RECORDED_AT));

        assertEquals("Reference is required", exception.getMessage());
    }

    @Test
    void should_keep_restored_events_empty_and_immutable_when_inspected_or_cleared() {
        Evidence evidence = rehydrateEvidence();
        var snapshot = evidence.domainEvents();

        assertTrue(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(
                new EvidenceRecorded(EVIDENCE_ID, IMPLEMENTATION_ID, RECORDED_AT)));
        assertEquals(snapshot, evidence.domainEvents());

        evidence.clearDomainEvents();

        assertTrue(snapshot.isEmpty());
        assertTrue(evidence.domainEvents().isEmpty());
        assertBusinessState(evidence);
    }

    private Evidence rehydrateEvidence() {
        return Evidence.rehydrate(
                EVIDENCE_ID,
                IMPLEMENTATION_ID,
                "  Signed approval minutes  ",
                "  evidence-vault:item-123  ",
                RECORDED_AT
        );
    }

    private void assertBusinessState(Evidence evidence) {
        assertAll(
                () -> assertEquals(EVIDENCE_ID, evidence.id()),
                () -> assertEquals(IMPLEMENTATION_ID, evidence.controlImplementationId()),
                () -> assertEquals("Signed approval minutes", evidence.description()),
                () -> assertEquals("evidence-vault:item-123", evidence.reference()),
                () -> assertEquals(RECORDED_AT, evidence.recordedAt())
        );
    }

    private Evidence.Builder validBuilder() {
        return Evidence.builder()
                .id(EVIDENCE_ID)
                .controlImplementationId(IMPLEMENTATION_ID)
                .description("  Signed approval minutes  ")
                .reference("  evidence-vault:item-123  ")
                .recordedAt(RECORDED_AT);
    }
}
