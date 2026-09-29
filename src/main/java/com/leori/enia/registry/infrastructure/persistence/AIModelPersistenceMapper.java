package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.domain.AIModel;
import com.leori.enia.registry.domain.AIModelId;

class AIModelPersistenceMapper {

    AIModelJpaEntity toEntity(AIModel model) {
        return new AIModelJpaEntity(
                model.id().value(),
                model.systemId().value(),
                model.name(),
                model.description(),
                model.provider(),
                model.createdAt()
        );
    }

    AIModel toDomain(AIModelJpaEntity entity) {
        return AIModel.rehydrate(
                new AIModelId(entity.id()),
                new AISystemId(entity.systemId()),
                entity.name(),
                entity.description(),
                entity.provider(),
                entity.createdAt()
        );
    }
}
