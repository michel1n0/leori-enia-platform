package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.domain.ControlImplementation;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import jakarta.persistence.EntityManager;

import java.util.Objects;
import java.util.Optional;

/** Transaction advice is supplied by RiskPersistenceConfiguration. */
public final class JpaControlImplementationRepositoryAdapter implements ControlImplementationRepository {

    private final EntityManager entityManager;
    private final ControlImplementationPersistenceMapper mapper;

    public JpaControlImplementationRepositoryAdapter(
            EntityManager entityManager,
            ControlImplementationPersistenceMapper mapper
    ) {
        this.entityManager = Objects.requireNonNull(entityManager, "Entity manager is required");
        this.mapper = Objects.requireNonNull(mapper, "Control implementation persistence mapper is required");
    }

    @Override
    public ControlImplementation create(ControlImplementation implementation) {
        Objects.requireNonNull(implementation, "Control implementation is required");
        ControlImplementationJpaEntity entity = mapper.toEntity(implementation);
        entityManager.persist(entity);
        entityManager.flush();
        return implementation;
    }

    @Override
    public Optional<ControlImplementation> findById(ControlImplementationId id) {
        Objects.requireNonNull(id, "Control implementation id is required");
        return Optional.ofNullable(entityManager.find(ControlImplementationJpaEntity.class, id.value()))
                .map(mapper::toDomain);
    }
}
