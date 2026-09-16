package com.leori.enia.initiative.application;

import com.leori.enia.organization.domain.OrganizationId;

public record CreateAIInitiativeCommand(
        OrganizationId organizationId,
        String name,
        String description,
        boolean usesPersonalData,
        boolean impactsRights
) {
}
