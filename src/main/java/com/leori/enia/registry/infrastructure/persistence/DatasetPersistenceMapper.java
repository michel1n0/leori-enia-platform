package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;

class DatasetPersistenceMapper {

    DatasetJpaEntity toEntity(Dataset dataset) {
        return new DatasetJpaEntity(
                dataset.id().value(),
                dataset.name(),
                dataset.description(),
                dataset.createdAt()
        );
    }

    Dataset toDomain(DatasetJpaEntity entity) {
        return Dataset.rehydrate(
                new DatasetId(entity.id()),
                entity.name(),
                entity.description(),
                entity.createdAt()
        );
    }
}
