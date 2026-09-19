package com.leori.enia.initiative.infrastructure.persistence;

import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import org.springframework.stereotype.Component;

@Component
class AIInitiativePersistenceMapper {

    AIInitiativeJpaEntity toEntity(AIInitiative initiative, Long version) {
        return new AIInitiativeJpaEntity(
                initiative.id().value(),
                initiative.organizationId().value(),
                initiative.name(),
                initiative.description(),
                initiative.status(),
                initiative.preliminaryRisk(),
                initiative.usesPersonalData(),
                initiative.impactsRights(),
                initiative.createdAt(),
                version
        );
    }

    LoadedAIInitiative toLoaded(AIInitiativeJpaEntity entity) {
        return new LoadedAIInitiative(toDomain(entity), entity.version());
    }

    AIInitiative toDomain(AIInitiativeJpaEntity entity) {
        return AIInitiative.rehydrate(
                new AIInitiativeId(entity.id()),
                new OrganizationId(entity.organizationId()),
                entity.name(),
                entity.description(),
                entity.status(),
                entity.preliminaryRisk(),
                entity.usesPersonalData(),
                entity.impactsRights(),
                entity.createdAt()
        );
    }
}
