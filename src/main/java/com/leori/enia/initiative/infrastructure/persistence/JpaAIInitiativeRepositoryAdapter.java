package com.leori.enia.initiative.infrastructure.persistence;

import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;

import java.util.Optional;

/**
 * Registered explicitly by AIInitiativePersistenceConfiguration.
 * Persistence exception translation belongs to the Spring Data repository proxy;
 * this adapter only maps values and delegates repository calls.
 */
public final class JpaAIInitiativeRepositoryAdapter
        implements AIInitiativeRepository {

    private final SpringDataAIInitiativeRepository repository;
    private final AIInitiativePersistenceMapper mapper;

    public JpaAIInitiativeRepositoryAdapter(
            SpringDataAIInitiativeRepository repository,
            AIInitiativePersistenceMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public AIInitiative create(AIInitiative initiative) {
        // A null version makes Spring Data use persist, even with an assigned ID.
        AIInitiativeJpaEntity entity = mapper.toEntity(initiative, null);
        AIInitiativeJpaEntity savedEntity = repository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public AIInitiative save(LoadedAIInitiative loaded) {
        // Never replace the caller's expected version with a freshly read one.
        AIInitiativeJpaEntity entity = mapper.toEntity(loaded.initiative(), loaded.version());
        AIInitiativeJpaEntity savedEntity = repository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<LoadedAIInitiative> findById(AIInitiativeId id) {
        return repository.findById(id.value()).map(mapper::toLoaded);
    }
}
