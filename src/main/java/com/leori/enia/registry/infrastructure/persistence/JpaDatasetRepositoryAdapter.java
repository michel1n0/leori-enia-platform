package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.registry.application.port.DatasetRepository;
import com.leori.enia.registry.domain.Dataset;
import jakarta.persistence.EntityManager;

import java.util.Objects;

/** Transaction advice is supplied by RegistryPersistenceConfiguration. */
public final class JpaDatasetRepositoryAdapter implements DatasetRepository {

    private final EntityManager entityManager;
    private final DatasetPersistenceMapper mapper;

    public JpaDatasetRepositoryAdapter(EntityManager entityManager, DatasetPersistenceMapper mapper) {
        this.entityManager = Objects.requireNonNull(entityManager, "Entity manager is required");
        this.mapper = Objects.requireNonNull(mapper, "Dataset persistence mapper is required");
    }

    @Override
    public Dataset create(Dataset dataset) {
        Objects.requireNonNull(dataset, "Dataset is required");
        DatasetJpaEntity entity = mapper.toEntity(dataset);
        entityManager.persist(entity);
        entityManager.flush();
        return dataset;
    }
}
