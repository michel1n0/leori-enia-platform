package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.ControlRepository;
import jakarta.persistence.EntityManager;

import java.util.Objects;
import java.util.Optional;

/** Transaction advice is supplied by RiskPersistenceConfiguration. */
public final class JpaControlRepositoryAdapter implements ControlRepository {

    private final EntityManager entityManager;
    private final ControlPersistenceMapper mapper;

    public JpaControlRepositoryAdapter(EntityManager entityManager, ControlPersistenceMapper mapper) {
        this.entityManager = Objects.requireNonNull(entityManager, "Entity manager is required");
        this.mapper = Objects.requireNonNull(mapper, "Control persistence mapper is required");
    }

    @Override
    public Control create(Control control) {
        Objects.requireNonNull(control, "Control is required");
        ControlJpaEntity entity = mapper.toEntity(control);
        entityManager.persist(entity);
        entityManager.flush();
        return control;
    }

    @Override
    public Optional<Control> findById(ControlId id) {
        Objects.requireNonNull(id, "Control id is required");
        return Optional.ofNullable(entityManager.find(ControlJpaEntity.class, id.value()))
                .map(mapper::toDomain);
    }
}
