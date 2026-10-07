package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;

class ControlImplementationPersistenceMapper {

    ControlImplementationJpaEntity toEntity(ControlImplementation implementation) {
        return new ControlImplementationJpaEntity(
                implementation.id().value(),
                implementation.controlId().value(),
                implementation.description(),
                implementation.implementedAt()
        );
    }

    ControlImplementation toDomain(ControlImplementationJpaEntity entity) {
        return ControlImplementation.rehydrate(
                new ControlImplementationId(entity.id()),
                new ControlId(entity.controlId()),
                entity.description(),
                entity.implementedAt()
        );
    }
}
