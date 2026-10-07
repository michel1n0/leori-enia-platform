package com.leori.enia.risk.domain;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.domain.event.RiskAssessmentRecorded;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskAssessmentTest {

    private static final RiskAssessmentId ASSESSMENT_ID =
            RiskAssessmentId.of("5dd0fbc2-a933-4ed3-9fb2-13f6b4e2a55f");
    private static final AISystemId SYSTEM_ID = AISystemId.of("a1aa76f0-1cbd-4b51-8eae-241d5fccfd3e");
    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00.123456Z");
    private static final ContextOfUse CONTEXT = new ContextOfUse(
            "  Credit eligibility  ",
            "  Production lending workflow  "
    );
    private static final RiskFindingId FIRST_FINDING_ID =
            RiskFindingId.of("10000000-0000-0000-0000-000000000001");
    private static final RiskFindingId SECOND_FINDING_ID =
            RiskFindingId.of("10000000-0000-0000-0000-000000000002");
    private static final RiskFinding FIRST_FINDING = new RiskFinding(FIRST_FINDING_ID,
            "  Biased recommendations  ",
            Likelihood.MEDIUM,
            ImpactMagnitude.HIGH
    );
    private static final RiskFinding SECOND_FINDING = new RiskFinding(SECOND_FINDING_ID,
            "  Insufficient explanation  ",
            Likelihood.LOW,
            ImpactMagnitude.MEDIUM
    );
    private static final RiskFinding DUPLICATE_CONTENT_FINDING = new RiskFinding(
            RiskFindingId.of("10000000-0000-0000-0000-000000000003"),
            "  Biased recommendations  ",
            Likelihood.MEDIUM,
            ImpactMagnitude.HIGH
    );

    @Test
    void should_record_a_risk_assessment_with_normalized_state_and_one_event() {
        RiskAssessment assessment = validBuilder().build();

        assertBusinessState(assessment);
        assertEquals(1, assessment.domainEvents().size());
        RiskAssessmentRecorded event = assertInstanceOf(RiskAssessmentRecorded.class,
                assessment.domainEvents().getFirst());
        assertAll(
                () -> assertEquals(assessment.id(), event.riskAssessmentId()),
                () -> assertEquals(assessment.systemId(), event.systemId()),
                () -> assertEquals(assessment.assessedAt(), event.occurredAt())
        );
    }

    @Test
    void should_defensively_copy_findings_and_preserve_order_and_duplicate_content_with_distinct_ids() {
        List<RiskFinding> source = new ArrayList<>(List.of(
                FIRST_FINDING, SECOND_FINDING, DUPLICATE_CONTENT_FINDING));
        RiskAssessment assessment = validBuilder().findings(source).build();

        source.clear();

        assertEquals(List.of(FIRST_FINDING, SECOND_FINDING, DUPLICATE_CONTENT_FINDING), assessment.findings());
        assertEquals(assessment.findings().get(0).description(), assessment.findings().get(2).description());
        assertTrue(!assessment.findings().get(0).id().equals(assessment.findings().get(2).id()));
        assertThrows(UnsupportedOperationException.class, () -> assessment.findings().add(SECOND_FINDING));
    }

    @Test
    void should_report_existing_finding_membership_by_id() {
        RiskAssessment assessment = validBuilder().build();

        assertTrue(assessment.containsFinding(FIRST_FINDING_ID));
    }

    @Test
    void should_report_missing_finding_membership_by_id() {
        RiskAssessment assessment = validBuilder().build();

        assertFalse(assessment.containsFinding(RiskFindingId.generate()));
    }

    @Test
    void should_require_finding_id_when_checking_membership() {
        RiskAssessment assessment = validBuilder().build();

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> assessment.containsFinding(null));

        assertEquals("Risk finding id is required", exception.getMessage());
    }

    @Test
    void should_reject_duplicate_finding_ids_when_recording() {
        RiskFinding duplicateIdFinding = new RiskFinding(
                FIRST_FINDING_ID,
                "Different risk with reused id",
                Likelihood.LOW,
                ImpactMagnitude.LOW
        );

        var exception = assertThrows(IllegalArgumentException.class,
                () -> validBuilder().findings(List.of(FIRST_FINDING, duplicateIdFinding)).build());

        assertEquals("Risk finding ids must be unique within an assessment", exception.getMessage());
    }

    @Test
    void should_require_assessment_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().id(null).build());

        assertEquals("Risk assessment id is required", exception.getMessage());
    }

    @Test
    void should_require_system_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().systemId(null).build());

        assertEquals("AI system id is required", exception.getMessage());
    }

    @Test
    void should_require_context_of_use() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().contextOfUse(null).build());

        assertEquals("Context of use is required", exception.getMessage());
    }

    @Test
    void should_require_findings() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().findings(null).build());

        assertEquals("Findings are required", exception.getMessage());
    }

    @Test
    void should_require_each_risk_finding() {
        var exception = assertThrows(NullPointerException.class,
                () -> validBuilder().findings(Arrays.asList(FIRST_FINDING, null)).build());

        assertEquals("Risk finding is required", exception.getMessage());
    }

    @Test
    void should_reject_an_empty_findings_list() {
        var exception = assertThrows(IllegalArgumentException.class, () -> validBuilder().findings(List.of()).build());

        assertEquals("At least one risk finding is required", exception.getMessage());
    }

    @Test
    void should_require_assessed_at() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().assessedAt(null).build());

        assertEquals("AssessedAt is required", exception.getMessage());
    }

    @Test
    void should_expose_an_immutable_pending_event_snapshot() {
        RiskAssessment assessment = validBuilder().build();
        var snapshot = assessment.domainEvents();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));
        assertEquals(snapshot, assessment.domainEvents());
    }

    @Test
    void should_clear_pending_events_without_changing_previous_snapshot_or_business_state() {
        RiskAssessment assessment = validBuilder().build();
        var snapshot = assessment.domainEvents();
        var event = snapshot.getFirst();

        assessment.clearDomainEvents();

        assertTrue(assessment.domainEvents().isEmpty());
        assertEquals(1, snapshot.size());
        assertEquals(event, snapshot.getFirst());
        assertBusinessState(assessment);
    }

    @Test
    void should_rehydrate_state_with_normalized_nested_values_and_no_events() {
        RiskAssessment assessment = rehydrateAssessment();

        assertBusinessState(assessment);
        assertTrue(assessment.domainEvents().isEmpty());
    }

    @Test
    void should_defensively_copy_findings_when_rehydrating() {
        List<RiskFinding> source = new ArrayList<>(List.of(
                FIRST_FINDING, SECOND_FINDING, DUPLICATE_CONTENT_FINDING));
        RiskAssessment assessment = RiskAssessment.rehydrate(ASSESSMENT_ID, SYSTEM_ID, CONTEXT, source, ASSESSED_AT);

        source.clear();

        assertEquals(List.of(FIRST_FINDING, SECOND_FINDING, DUPLICATE_CONTENT_FINDING), assessment.findings());
        assertTrue(assessment.domainEvents().isEmpty());
    }

    @Test
    void should_reject_duplicate_finding_ids_when_rehydrating() {
        RiskFinding duplicateIdFinding = new RiskFinding(
                FIRST_FINDING_ID,
                "Different persisted risk with reused id",
                Likelihood.LOW,
                ImpactMagnitude.LOW
        );

        var exception = assertThrows(IllegalArgumentException.class,
                () -> RiskAssessment.rehydrate(ASSESSMENT_ID, SYSTEM_ID, CONTEXT,
                        List.of(FIRST_FINDING, duplicateIdFinding), ASSESSED_AT));

        assertEquals("Risk finding ids must be unique within an assessment", exception.getMessage());
    }

    @Test
    void should_require_fields_when_rehydrating() {
        assertAll(
                () -> assertEquals("Risk assessment id is required", assertThrows(NullPointerException.class,
                        () -> RiskAssessment.rehydrate(null, SYSTEM_ID, CONTEXT, List.of(FIRST_FINDING), ASSESSED_AT))
                        .getMessage()),
                () -> assertEquals("AI system id is required", assertThrows(NullPointerException.class,
                        () -> RiskAssessment.rehydrate(ASSESSMENT_ID, null, CONTEXT, List.of(FIRST_FINDING), ASSESSED_AT))
                        .getMessage()),
                () -> assertEquals("Context of use is required", assertThrows(NullPointerException.class,
                        () -> RiskAssessment.rehydrate(ASSESSMENT_ID, SYSTEM_ID, null, List.of(FIRST_FINDING), ASSESSED_AT))
                        .getMessage()),
                () -> assertEquals("Findings are required", assertThrows(NullPointerException.class,
                        () -> RiskAssessment.rehydrate(ASSESSMENT_ID, SYSTEM_ID, CONTEXT, null, ASSESSED_AT))
                        .getMessage()),
                () -> assertEquals("Risk finding is required", assertThrows(NullPointerException.class,
                        () -> RiskAssessment.rehydrate(ASSESSMENT_ID, SYSTEM_ID, CONTEXT,
                                Arrays.asList(FIRST_FINDING, null), ASSESSED_AT))
                        .getMessage()),
                () -> assertEquals("AssessedAt is required", assertThrows(NullPointerException.class,
                        () -> RiskAssessment.rehydrate(ASSESSMENT_ID, SYSTEM_ID, CONTEXT, List.of(FIRST_FINDING), null))
                        .getMessage())
        );
    }

    @Test
    void should_keep_restored_events_empty_and_immutable_when_inspected_or_cleared() {
        RiskAssessment assessment = rehydrateAssessment();
        var snapshot = assessment.domainEvents();

        assertTrue(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(
                new RiskAssessmentRecorded(ASSESSMENT_ID, SYSTEM_ID, ASSESSED_AT)));
        assertEquals(snapshot, assessment.domainEvents());

        assessment.clearDomainEvents();

        assertTrue(snapshot.isEmpty());
        assertTrue(assessment.domainEvents().isEmpty());
        assertBusinessState(assessment);
    }

    private RiskAssessment rehydrateAssessment() {
        return RiskAssessment.rehydrate(
                ASSESSMENT_ID,
                SYSTEM_ID,
                new ContextOfUse("  Credit eligibility  ", "  Production lending workflow  "),
                List.of(
                        new RiskFinding(FIRST_FINDING_ID, "  Biased recommendations  ", Likelihood.MEDIUM, ImpactMagnitude.HIGH),
                        new RiskFinding(SECOND_FINDING_ID, "  Insufficient explanation  ", Likelihood.LOW, ImpactMagnitude.MEDIUM)
                ),
                ASSESSED_AT
        );
    }

    private void assertBusinessState(RiskAssessment assessment) {
        assertAll(
                () -> assertEquals(ASSESSMENT_ID, assessment.id()),
                () -> assertEquals(SYSTEM_ID, assessment.systemId()),
                () -> assertEquals(new ContextOfUse("Credit eligibility", "Production lending workflow"),
                        assessment.contextOfUse()),
                () -> assertEquals(List.of(FIRST_FINDING, SECOND_FINDING), assessment.findings()),
                () -> assertEquals(ASSESSED_AT, assessment.assessedAt())
        );
    }

    private RiskAssessment.Builder validBuilder() {
        return RiskAssessment.builder()
                .id(ASSESSMENT_ID)
                .systemId(SYSTEM_ID)
                .contextOfUse(CONTEXT)
                .findings(List.of(FIRST_FINDING, SECOND_FINDING))
                .assessedAt(ASSESSED_AT);
    }
}
