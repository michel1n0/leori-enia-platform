package com.leori.enia.initiative.domain;

import com.leori.enia.initiative.domain.event.AIInitiativeApproved;
import com.leori.enia.initiative.domain.event.AIInitiativeRejected;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AIInitiativeTest {

    private static final Instant NOW =
            Instant.parse("2026-09-14T20:30:00Z");
    private static final AIInitiativeId INITIATIVE_ID =
            AIInitiativeId.of("d8cc78c8-91c9-4211-8241-abd8606c00d9");
    private static final OrganizationId ORGANIZATION_ID =
            new OrganizationId(
                    java.util.UUID.fromString(
                            "7d6ebd96-13e3-48f2-913f-13c9b7013a0c"
                    )
            );

    @Test
    void should_create_initiative_in_draft_status() {

        AIInitiative initiative = createInitiative();

        assertEquals(
                InitiativeStatus.DRAFT,
                initiative.status()
        );

        assertEquals(
                RiskLevel.NOT_ASSESSED,
                initiative.preliminaryRisk()
        );
    }

    @Test
    void should_follow_assessment_workflow() {

        AIInitiative initiative = createInitiative();

        initiative.submit(NOW);
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.HIGH, NOW);

        assertEquals(
                InitiativeStatus.RISK_ASSESSED,
                initiative.status()
        );

        assertEquals(
                RiskLevel.HIGH,
                initiative.preliminaryRisk()
        );
    }

    @Test
    void should_not_start_assessment_from_draft() {

        AIInitiative initiative = createInitiative();

        assertThrows(
                IllegalStateException.class,
                initiative::startAssessment
        );
    }

    @Test
    void should_approve_risk_assessed_initiative() {

        AIInitiative initiative = createInitiative();

        initiative.submit(NOW);
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.HIGH, NOW);
        initiative.approve(NOW);

        assertEquals(
                InitiativeStatus.APPROVED,
                initiative.status()
        );
    }

    @Test
    void should_not_approve_without_risk_assessment() {

        AIInitiative initiative = createInitiative();

        initiative.submit(NOW);
        initiative.startAssessment();

        assertThrows(
                IllegalStateException.class,
                () -> initiative.approve(NOW)
        );
    }

    @Test
    void should_register_event_when_initiative_is_approved() {

        AIInitiative initiative = createInitiative();

        initiative.submit(NOW);
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.HIGH, NOW);

        // Eliminamos los eventos generados anteriormente
        initiative.clearDomainEvents();

        initiative.approve(NOW);

        assertEquals(1, initiative.domainEvents().size());

        assertInstanceOf(
                AIInitiativeApproved.class,
                initiative.domainEvents().getFirst()
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = InitiativeStatus.class,
            names = {"DRAFT", "SUBMITTED", "UNDER_ASSESSMENT"}
    )
    void should_rehydrate_unassessed_lifecycle_states(
            InitiativeStatus status
    ) {
        AIInitiative initiative = rehydrate(status, RiskLevel.NOT_ASSESSED);

        assertEquals(status, initiative.status());
        assertEquals(RiskLevel.NOT_ASSESSED, initiative.preliminaryRisk());
        assertTrue(initiative.domainEvents().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(
            value = RiskLevel.class,
            names = {"LOW", "MEDIUM", "HIGH"}
    )
    void should_rehydrate_risk_assessed_with_assessed_risk(
            RiskLevel riskLevel
    ) {
        AIInitiative initiative = rehydrate(
                InitiativeStatus.RISK_ASSESSED,
                riskLevel
        );

        assertEquals(InitiativeStatus.RISK_ASSESSED, initiative.status());
        assertEquals(riskLevel, initiative.preliminaryRisk());
        assertTrue(initiative.domainEvents().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(
            value = InitiativeStatus.class,
            names = {"APPROVED", "REJECTED"}
    )
    void should_rehydrate_terminal_states_with_assessed_risk(
            InitiativeStatus status
    ) {
        AIInitiative initiative = rehydrate(status, RiskLevel.HIGH);

        assertEquals(status, initiative.status());
        assertEquals(RiskLevel.HIGH, initiative.preliminaryRisk());
        assertTrue(initiative.domainEvents().isEmpty());
    }

    @Test
    void should_restore_and_normalize_all_persisted_fields() {
        AIInitiative initiative = AIInitiative.rehydrate(
                INITIATIVE_ID,
                ORGANIZATION_ID,
                "  Initiative name  ",
                "  Initiative description  ",
                InitiativeStatus.APPROVED,
                RiskLevel.MEDIUM,
                true,
                false,
                NOW, null
        );

        assertAll(
                () -> assertEquals(INITIATIVE_ID, initiative.id()),
                () -> assertEquals(ORGANIZATION_ID, initiative.organizationId()),
                () -> assertEquals("Initiative name", initiative.name()),
                () -> assertEquals(
                        "Initiative description",
                        initiative.description()
                ),
                () -> assertEquals(InitiativeStatus.APPROVED, initiative.status()),
                () -> assertEquals(RiskLevel.MEDIUM, initiative.preliminaryRisk()),
                () -> assertTrue(initiative.usesPersonalData()),
                () -> assertFalse(initiative.impactsRights()),
                () -> assertEquals(NOW, initiative.createdAt()),
                () -> assertTrue(initiative.domainEvents().isEmpty())
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = InitiativeStatus.class,
            names = {"DRAFT", "SUBMITTED", "UNDER_ASSESSMENT"}
    )
    void should_reject_assessed_risk_for_unassessed_lifecycle_states(
            InitiativeStatus status
    ) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> rehydrate(status, RiskLevel.HIGH)
        );

        assertEquals(
                "Preliminary risk must be NOT_ASSESSED for status " + status,
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = InitiativeStatus.class,
            names = {"RISK_ASSESSED", "APPROVED", "REJECTED"}
    )
    void should_reject_not_assessed_risk_for_assessed_lifecycle_states(
            InitiativeStatus status
    ) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> rehydrate(status, RiskLevel.NOT_ASSESSED)
        );

        assertEquals(
                "Preliminary risk must be assessed for status " + status,
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = InitiativeStatus.class,
            names = {
                    "EXPERIMENTATION",
                    "READY_FOR_DEPLOYMENT",
                    "ACTIVE",
                    "SUSPENDED",
                    "RETIRED"
            }
    )
    void should_reject_unsupported_future_states_during_rehydration(
            InitiativeStatus status
    ) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> rehydrate(status, RiskLevel.NOT_ASSESSED)
        );

        assertEquals(
                "Unsupported initiative status for rehydration: " + status,
                exception.getMessage()
        );
    }

    @Test
    void should_reject_null_required_rehydration_values() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> AIInitiative.rehydrate(
                                null,
                                ORGANIZATION_ID,
                                "Name",
                                "Description",
                                InitiativeStatus.DRAFT,
                                RiskLevel.NOT_ASSESSED,
                                true,
                                false,
                                NOW, null
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> AIInitiative.rehydrate(
                                INITIATIVE_ID,
                                null,
                                "Name",
                                "Description",
                                InitiativeStatus.DRAFT,
                                RiskLevel.NOT_ASSESSED,
                                true,
                                false,
                                NOW, null
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> AIInitiative.rehydrate(
                                INITIATIVE_ID,
                                ORGANIZATION_ID,
                                null,
                                "Description",
                                InitiativeStatus.DRAFT,
                                RiskLevel.NOT_ASSESSED,
                                true,
                                false,
                                NOW, null
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> AIInitiative.rehydrate(
                                INITIATIVE_ID,
                                ORGANIZATION_ID,
                                "Name",
                                null,
                                InitiativeStatus.DRAFT,
                                RiskLevel.NOT_ASSESSED,
                                true,
                                false,
                                NOW, null
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> AIInitiative.rehydrate(
                                INITIATIVE_ID,
                                ORGANIZATION_ID,
                                "Name",
                                "Description",
                                null,
                                RiskLevel.NOT_ASSESSED,
                                true,
                                false,
                                NOW, null
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> AIInitiative.rehydrate(
                                INITIATIVE_ID,
                                ORGANIZATION_ID,
                                "Name",
                                "Description",
                                InitiativeStatus.DRAFT,
                                null,
                                true,
                                false,
                                NOW, null
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> AIInitiative.rehydrate(
                                INITIATIVE_ID,
                                ORGANIZATION_ID,
                                "Name",
                                "Description",
                                InitiativeStatus.DRAFT,
                                RiskLevel.NOT_ASSESSED,
                                true,
                                false,
                                null, null
                        )
                )
        );
    }

    @Test
    void should_not_mutate_or_register_event_when_submission_time_is_null() {
        AIInitiative initiative = createInitiative();

        assertThrows(NullPointerException.class, () -> initiative.submit(null));

        assertEquals(InitiativeStatus.DRAFT, initiative.status());
        assertTrue(initiative.domainEvents().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = RiskLevel.class, names = {"LOW", "MEDIUM", "HIGH"})
    void rejection_stores_the_same_normalized_reason_as_its_event(RiskLevel risk) {
        AIInitiative initiative = createInitiative();
        initiative.submit(NOW);
        initiative.startAssessment();
        initiative.assessRisk(risk, NOW);
        var eventsBefore = initiative.domainEvents();
        Instant rejectedAt = NOW.plusSeconds(60);

        initiative.reject("  Residual risk unacceptable \t", rejectedAt);

        assertEquals(InitiativeStatus.REJECTED, initiative.status());
        assertEquals(risk, initiative.preliminaryRisk());
        assertEquals("Residual risk unacceptable", initiative.rejectionReason());
        assertEquals(eventsBefore.size() + 1, initiative.domainEvents().size());
        assertEquals(eventsBefore, initiative.domainEvents().subList(0, eventsBefore.size()));
        AIInitiativeRejected event = assertInstanceOf(AIInitiativeRejected.class, initiative.domainEvents().getLast());
        assertEquals(initiative.id(), event.initiativeId());
        assertSame(initiative.rejectionReason(), event.reason());
        assertEquals(rejectedAt, event.occurredAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "\u0000", "\u2003"})
    void invalid_rejection_reason_preserves_all_state_and_pending_events(String reason) {
        AIInitiative initiative = createInitiative();
        initiative.submit(NOW);
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.HIGH, NOW);
        var eventsBefore = initiative.domainEvents();

        assertThrows(IllegalArgumentException.class, () -> initiative.reject(reason, NOW));

        assertEquals(InitiativeStatus.RISK_ASSESSED, initiative.status());
        assertEquals(RiskLevel.HIGH, initiative.preliminaryRisk());
        assertNull(initiative.rejectionReason());
        assertEquals(eventsBefore, initiative.domainEvents());
    }

    @Test
    void null_rejection_time_preserves_all_state_and_pending_events() {
        AIInitiative initiative = createInitiative();
        initiative.submit(NOW);
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.HIGH, NOW);
        var eventsBefore = initiative.domainEvents();

        assertThrows(NullPointerException.class, () -> initiative.reject("Reason", null));

        assertEquals(InitiativeStatus.RISK_ASSESSED, initiative.status());
        assertEquals(RiskLevel.HIGH, initiative.preliminaryRisk());
        assertNull(initiative.rejectionReason());
        assertEquals(eventsBefore, initiative.domainEvents());
    }

    @Test
    void wrong_state_rejection_preserves_pending_events() {
        AIInitiative initiative = createInitiative();
        initiative.submit(NOW);
        var eventsBefore = initiative.domainEvents();

        assertThrows(IllegalStateException.class, () -> initiative.reject("Reason", NOW));

        assertEquals(InitiativeStatus.SUBMITTED, initiative.status());
        assertEquals(RiskLevel.NOT_ASSESSED, initiative.preliminaryRisk());
        assertNull(initiative.rejectionReason());
        assertEquals(eventsBefore, initiative.domainEvents());
    }

    @Test
    void repeated_rejection_preserves_the_original_reason_and_events() {
        AIInitiative initiative = rehydrate(InitiativeStatus.RISK_ASSESSED, RiskLevel.HIGH);
        initiative.reject("Original reason", NOW);
        var eventsBefore = initiative.domainEvents();

        assertThrows(IllegalStateException.class, () -> initiative.reject("Replacement reason", NOW.plusSeconds(1)));

        assertEquals(InitiativeStatus.REJECTED, initiative.status());
        assertEquals(RiskLevel.HIGH, initiative.preliminaryRisk());
        assertEquals("Original reason", initiative.rejectionReason());
        assertEquals(eventsBefore, initiative.domainEvents());
    }

    @Test
    void creation_and_non_rejected_transitions_have_no_rejection_reason() {
        AIInitiative initiative = createInitiative();
        assertNull(initiative.rejectionReason());
        initiative.submit(NOW);
        assertNull(initiative.rejectionReason());
        initiative.startAssessment();
        assertNull(initiative.rejectionReason());
        initiative.assessRisk(RiskLevel.HIGH, NOW);
        assertNull(initiative.rejectionReason());
        initiative.approve(NOW);
        assertNull(initiative.rejectionReason());
        assertEquals(InitiativeStatus.APPROVED, initiative.status());
        assertEquals(RiskLevel.HIGH, initiative.preliminaryRisk());
    }

    @ParameterizedTest
    @EnumSource(value = RiskLevel.class, names = {"LOW", "MEDIUM", "HIGH"})
    void rehydrates_rejected_reason_or_unavailable_legacy_reason_without_events(RiskLevel risk) {
        AIInitiative rejected = AIInitiative.rehydrate(INITIATIVE_ID, ORGANIZATION_ID,
                "Name", "Description", InitiativeStatus.REJECTED, risk, true, false, NOW, "  reason  ");
        assertEquals("reason", rejected.rejectionReason());
        assertEquals(InitiativeStatus.REJECTED, rejected.status());
        assertEquals(risk, rejected.preliminaryRisk());
        assertTrue(rejected.domainEvents().isEmpty());

        AIInitiative legacy = rehydrate(InitiativeStatus.REJECTED, risk);
        assertNull(legacy.rejectionReason());
        assertEquals(InitiativeStatus.REJECTED, legacy.status());
        assertEquals(risk, legacy.preliminaryRisk());
        assertTrue(legacy.domainEvents().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n", "\u0000", "\u2003"})
    void rehydration_rejects_blank_reasons(String reason) {
        assertThrows(IllegalArgumentException.class, () -> AIInitiative.rehydrate(
                INITIATIVE_ID, ORGANIZATION_ID, "Name", "Description", InitiativeStatus.REJECTED,
                RiskLevel.HIGH, true, false, NOW, reason));
    }

    @ParameterizedTest
    @EnumSource(value = InitiativeStatus.class,
            names = {"DRAFT", "SUBMITTED", "UNDER_ASSESSMENT", "RISK_ASSESSED", "APPROVED"})
    void rehydration_requires_null_reason_for_non_rejected_states(InitiativeStatus status) {
        RiskLevel risk = status == InitiativeStatus.RISK_ASSESSED || status == InitiativeStatus.APPROVED
                ? RiskLevel.HIGH : RiskLevel.NOT_ASSESSED;
        assertNull(rehydrate(status, risk).rejectionReason());
        for (String reason : new String[]{"Reason", "", "   "}) {
            assertThrows(IllegalArgumentException.class, () -> AIInitiative.rehydrate(
                    INITIATIVE_ID, ORGANIZATION_ID, "Name", "Description", status,
                    risk, true, false, NOW, reason));
        }
    }

    private AIInitiative createInitiative() {

        return AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("Detección de anomalías de asistencia")
                .description(
                        "Detectar patrones anómalos de asistencia laboral"
                )
                .usesPersonalData(true)
                .impactsRights(true)
                .createdAt(NOW)
                .build();
    }

    private AIInitiative rehydrate(
            InitiativeStatus status,
            RiskLevel preliminaryRisk
    ) {
        return AIInitiative.rehydrate(
                INITIATIVE_ID,
                ORGANIZATION_ID,
                "Initiative name",
                "Initiative description",
                status,
                preliminaryRisk,
                true,
                false,
                NOW, null
        );
    }
}
