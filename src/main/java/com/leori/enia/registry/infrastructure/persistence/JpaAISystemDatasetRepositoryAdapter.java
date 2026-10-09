package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.application.exception.DatasetAlreadyAssociatedWithAISystemException;
import com.leori.enia.registry.application.port.AISystemDatasetRepository;
import com.leori.enia.registry.domain.AISystemDataset;
import com.leori.enia.registry.domain.Dataset;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.JDBCException;
import org.postgresql.util.PSQLException;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Transaction advice is supplied by RegistryPersistenceConfiguration. */
public final class JpaAISystemDatasetRepositoryAdapter implements AISystemDatasetRepository {

    private final EntityManager entityManager;
    private final DatasetPersistenceMapper datasetMapper;

    public JpaAISystemDatasetRepositoryAdapter(EntityManager entityManager, DatasetPersistenceMapper datasetMapper) {
        this.entityManager = Objects.requireNonNull(entityManager, "Entity manager is required");
        this.datasetMapper = Objects.requireNonNull(datasetMapper, "Dataset persistence mapper is required");
    }

    @Override
    public AISystemDataset create(AISystemDataset association) {
        Objects.requireNonNull(association, "AI system dataset association is required");
        AISystemDatasetJpaEntity entity = new AISystemDatasetJpaEntity(
                association.aiSystemId().value(),
                association.datasetId().value(),
                association.associatedAt()
        );
        try {
            entityManager.persist(entity);
            entityManager.flush();
        } catch (PersistenceException failure) {
            if (isDuplicateAssociation(failure)) {
                throw new DatasetAlreadyAssociatedWithAISystemException(failure);
            }
            throw failure;
        }
        return association;
    }

    @Override
    public List<Dataset> findDatasetsByAISystemId(AISystemId aiSystemId) {
        Objects.requireNonNull(aiSystemId, "AI system id is required");
        return entityManager.createQuery("""
                        select dataset
                        from AISystemDatasetJpaEntity association
                        join DatasetJpaEntity dataset on dataset.id = association.id.datasetId
                        where association.id.systemId = :systemId
                        order by association.associatedAt asc, association.id.datasetId asc
                        """, DatasetJpaEntity.class)
                .setParameter("systemId", aiSystemId.value())
                .getResultStream()
                .map(datasetMapper::toDomain)
                .toList();
    }

    private boolean isDuplicateAssociation(Throwable failure) {
        var pending = new ArrayDeque<Throwable>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current instanceof PSQLException postgres) {
                var metadata = postgres.getServerErrorMessage();
                if ("23505".equals(postgres.getSQLState()) && metadata != null
                        && "pk_ai_system_datasets".equals(metadata.getConstraint())
                        && "ai_system_datasets".equals(metadata.getTable())) {
                    return true;
                }
            }
            if (current.getCause() != null) {
                pending.add(current.getCause());
            }
            if (current instanceof JDBCException jdbc && jdbc.getSQLException() != null) {
                pending.add(jdbc.getSQLException());
            }
            if (current instanceof SQLException sql && sql.getNextException() != null) {
                pending.add(sql.getNextException());
            }
        }
        return false;
    }
}
