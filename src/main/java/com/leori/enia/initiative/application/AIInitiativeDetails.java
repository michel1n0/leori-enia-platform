package com.leori.enia.initiative.application;

import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.organization.domain.OrganizationId;

import java.time.Instant;

public record AIInitiativeDetails(
        AIInitiativeId id,
        OrganizationId organizationId,
        String name,
        String description,
        InitiativeStatus status,
        RiskLevel preliminaryRisk,
        boolean usesPersonalData,
        boolean impactsRights,
        Instant createdAt
) {
    public static AIInitiativeDetails from(AIInitiative initiative) {
        return new AIInitiativeDetails(
                initiative.id(), initiative.organizationId(), initiative.name(),
                initiative.description(), initiative.status(), initiative.preliminaryRisk(),
                initiative.usesPersonalData(), initiative.impactsRights(), initiative.createdAt()
        );
    }
}
