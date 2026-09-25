package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;

class AISystemPersistenceMapper {

    AISystemJpaEntity toEntity(AISystem system) {
        return new AISystemJpaEntity(
                system.id().value(),
                system.organizationId().value(),
                system.sourceInitiativeId().value(),
                system.name(),
                system.description(),
                system.status(),
                system.createdAt()
        );
    }

    AISystem toDomain(AISystemJpaEntity entity) {
        return AISystem.rehydrate(
                new AISystemId(entity.id()),
                new OrganizationId(entity.organizationId()),
                new AIInitiativeId(entity.sourceInitiativeId()),
                entity.name(),
                entity.description(),
                entity.status(),
                entity.createdAt()
        );
    }
}
