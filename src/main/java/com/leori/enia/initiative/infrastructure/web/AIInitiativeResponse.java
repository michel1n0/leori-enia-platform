package com.leori.enia.initiative.infrastructure.web;

import com.leori.enia.initiative.application.AIInitiativeDetails;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;

import java.time.Instant;
import java.util.UUID;

public record AIInitiativeResponse(
        UUID id,
        UUID organizationId,
        String name,
        String description,
        InitiativeStatus status,
        RiskLevel preliminaryRisk,
        boolean usesPersonalData,
        boolean impactsRights,
        Instant createdAt
) {
    static AIInitiativeResponse from(AIInitiativeDetails details) {
        return new AIInitiativeResponse(
                details.id().value(),
                details.organizationId().value(),
                details.name(),
                details.description(),
                details.status(),
                details.preliminaryRisk(),
                details.usesPersonalData(),
                details.impactsRights(),
                details.createdAt()
        );
    }
}
