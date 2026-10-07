package com.leori.enia.evidence.infrastructure.persistence;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceRepository;
import jakarta.persistence.EntityManager;

import java.util.Objects;

/** Transaction advice is supplied by EvidencePersistenceConfiguration. */
public final class JpaEvidenceRepositoryAdapter implements EvidenceRepository {

    private final EntityManager entityManager;
    private final EvidencePersistenceMapper mapper;

    public JpaEvidenceRepositoryAdapter(EntityManager entityManager, EvidencePersistenceMapper mapper) {
        this.entityManager = Objects.requireNonNull(entityManager, "Entity manager is required");
        this.mapper = Objects.requireNonNull(mapper, "Evidence persistence mapper is required");
    }

    @Override
    public Evidence create(Evidence evidence) {
        Objects.requireNonNull(evidence, "Evidence is required");
        EvidenceJpaEntity entity = mapper.toEntity(evidence);
        entityManager.persist(entity);
        entityManager.flush();
        return evidence;
    }
}
