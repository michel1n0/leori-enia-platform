package com.leori.enia.initiative.domain;

import com.leori.enia.initiative.domain.event.AIInitiativeApproved;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AIInitiativeTest {

    private static final Instant NOW =
            Instant.parse("2026-09-14T20:30:00Z");

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
}
