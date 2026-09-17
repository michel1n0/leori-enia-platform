package com.leori.enia.initiative.infrastructure.persistence;

import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
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
    public AIInitiative save(AIInitiative initiative) {
        AIInitiativeJpaEntity entity = mapper.toEntity(initiative);
        AIInitiativeJpaEntity savedEntity = repository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<AIInitiative> findById(AIInitiativeId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }
}
