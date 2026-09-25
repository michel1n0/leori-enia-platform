package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.exception.AISystemAlreadyRegisteredException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.JDBCException;
import org.postgresql.util.PSQLException;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** Transaction advice is supplied by GovernancePersistenceConfiguration. */
public final class JpaAISystemRepositoryAdapter implements AISystemRepository {

    private final EntityManager entityManager;
    private final AISystemPersistenceMapper mapper;

    public JpaAISystemRepositoryAdapter(EntityManager entityManager, AISystemPersistenceMapper mapper) {
        this.entityManager = Objects.requireNonNull(entityManager, "Entity manager is required");
        this.mapper = Objects.requireNonNull(mapper, "AI system persistence mapper is required");
    }

    @Override
    public AISystem create(AISystem system) {
        Objects.requireNonNull(system, "AI system is required");
        AISystemJpaEntity entity = mapper.toEntity(system);
        try {
            entityManager.persist(entity);
            // Detect the non-deferrable source constraint inside the translation boundary.
            entityManager.flush();
        } catch (PersistenceException failure) {
            if (isSourceDuplicate(failure)) {
                throw new AISystemAlreadyRegisteredException(system.sourceInitiativeId(), failure);
            }
            throw failure;
        }
        return system;
    }

    private boolean isSourceDuplicate(Throwable failure) {
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
                        && "uk_ai_systems_source_initiative".equals(metadata.getConstraint())
                        && "ai_systems".equals(metadata.getTable())) {
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
