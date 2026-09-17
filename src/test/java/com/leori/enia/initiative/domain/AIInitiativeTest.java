package com.leori.enia.initiative.domain;

import com.leori.enia.initiative.domain.event.AIInitiativeApproved;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
                NOW
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
                                NOW
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
                                NOW
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
                                NOW
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
                                NOW
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
                                NOW
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
                                NOW
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
                                null
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
                NOW
        );
    }
}
