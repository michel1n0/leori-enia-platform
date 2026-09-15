package com.leori.enia.initiative.domain;

import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AIInitiativeTest {

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

        initiative.submit();
        initiative.startAssessment();
        initiative.assessRisk(RiskLevel.HIGH);

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
                .build();
    }
}
