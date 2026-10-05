package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import jakarta.persistence.EntityManager;

import java.util.Objects;
import java.util.Optional;

/** Transaction advice is supplied by RiskPersistenceConfiguration. */
public final class JpaRiskAssessmentRepositoryAdapter implements RiskAssessmentRepository {

    private final EntityManager entityManager;
    private final RiskAssessmentPersistenceMapper mapper;

    public JpaRiskAssessmentRepositoryAdapter(EntityManager entityManager, RiskAssessmentPersistenceMapper mapper) {
        this.entityManager = Objects.requireNonNull(entityManager, "Entity manager is required");
        this.mapper = Objects.requireNonNull(mapper, "Risk assessment persistence mapper is required");
    }

    @Override
    public RiskAssessment create(RiskAssessment assessment) {
        Objects.requireNonNull(assessment, "Risk assessment is required");
        RiskAssessmentJpaEntity entity = mapper.toEntity(assessment);
        entityManager.persist(entity);
        entityManager.flush();
        return assessment;
    }

    @Override
    public Optional<RiskAssessment> findById(RiskAssessmentId id) {
        Objects.requireNonNull(id, "Risk assessment id is required");
        return Optional.ofNullable(entityManager.find(RiskAssessmentJpaEntity.class, id.value()))
                .map(mapper::toDomain);
    }
}
