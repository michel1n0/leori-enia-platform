package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.registry.application.port.AIModelRepository;
import com.leori.enia.registry.domain.AIModel;
import jakarta.persistence.EntityManager;

import java.util.Objects;

/** Transaction advice is supplied by RegistryPersistenceConfiguration. */
public final class JpaAIModelRepositoryAdapter implements AIModelRepository {

  private final EntityManager entityManager;
  private final AIModelPersistenceMapper mapper;

  public JpaAIModelRepositoryAdapter(EntityManager entityManager, AIModelPersistenceMapper mapper) {
    this.entityManager = Objects.requireNonNull(entityManager, "Entity manager is required");
    this.mapper = Objects.requireNonNull(mapper, "AI model persistence mapper is required");
  }

  @Override
  public AIModel create(AIModel model) {
    Objects.requireNonNull(model, "AI model is required");
    AIModelJpaEntity entity = mapper.toEntity(model);
    entityManager.persist(entity);
    entityManager.flush();
    return model;
  }
}
