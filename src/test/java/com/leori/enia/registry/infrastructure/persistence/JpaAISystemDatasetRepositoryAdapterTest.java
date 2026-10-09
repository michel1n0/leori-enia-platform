package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.application.exception.DatasetAlreadyAssociatedWithAISystemException;
import com.leori.enia.registry.domain.AISystemDataset;
import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JpaAISystemDatasetRepositoryAdapterTest {

    private static final Instant ASSOCIATED_AT = Instant.parse("2026-10-06T10:15:30Z");

    private final EntityManager entityManager = mock(EntityManager.class);
    private final JpaAISystemDatasetRepositoryAdapter adapter =
            new JpaAISystemDatasetRepositoryAdapter(entityManager, new DatasetPersistenceMapper());

    @Test
    void create_persists_expected_composite_id_and_timestamp_then_flushes() {
        AISystemDataset association = association();

        assertSame(association, adapter.create(association));

        var captor = org.mockito.ArgumentCaptor.forClass(AISystemDatasetJpaEntity.class);
        var calls = inOrder(entityManager);
        calls.verify(entityManager).persist(captor.capture());
        calls.verify(entityManager).flush();
        calls.verifyNoMoreInteractions();
        AISystemDatasetJpaEntity entity = captor.getValue();
        assertEquals(association.aiSystemId().value(), entity.systemId());
        assertEquals(association.datasetId().value(), entity.datasetId());
        assertEquals(association.associatedAt(), entity.associatedAt());
    }

    @Test
    void create_rejects_null_before_accessing_persistence() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> adapter.create(null));

        assertEquals("AI system dataset association is required", exception.getMessage());
        verifyNoInteractions(entityManager);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void duplicate_pk_violation_translates_to_domain_exception(boolean duringPersist) {
        AISystemDataset association = association();
        PersistenceException failure = new PersistenceException(
                postgres("23505", "pk_ai_system_datasets", "ai_system_datasets"));
        failAt(duringPersist, failure);

        DatasetAlreadyAssociatedWithAISystemException duplicate = assertThrows(
                DatasetAlreadyAssociatedWithAISystemException.class,
                () -> adapter.create(association)
        );

        assertSame(failure, duplicate.getCause());
        assertEquals("Dataset already associated with AI system", duplicate.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "23505, unrelated_unique, ai_system_datasets",
            "23505, pk_ai_system_datasets, other_table",
            "23503, fk_ai_system_datasets_system, ai_system_datasets",
            "23502, pk_ai_system_datasets, ai_system_datasets"
    })
    void unrelated_persistence_failures_are_not_translated(String state, String constraint, String table) {
        PersistenceException failure = new PersistenceException(postgres(state, constraint, table));
        doThrow(failure).when(entityManager).flush();

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(association())));
    }

    @Test
    void listing_binds_correct_ai_system_id() {
        AISystemId systemId = AISystemId.generate();
        TypedQuery<DatasetJpaEntity> query = listingQuery(List.of());

        adapter.findDatasetsByAISystemId(systemId);

        var calls = inOrder(entityManager, query);
        calls.verify(entityManager).createQuery(any(String.class), eq(DatasetJpaEntity.class));
        calls.verify(query).setParameter("systemId", systemId.value());
        calls.verify(query).getResultStream();
    }

    @Test
    void listing_maps_dataset_entities_to_rehydrated_domain_objects() {
        Dataset expected = dataset(DatasetId.generate(), "Dataset");
        listingQuery(List.of(new DatasetPersistenceMapper().toEntity(expected)));

        List<Dataset> result = adapter.findDatasetsByAISystemId(AISystemId.generate());

        assertEquals(1, result.size());
        Dataset actual = result.getFirst();
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.description(), actual.description());
        assertEquals(expected.createdAt(), actual.createdAt());
        assertTrue(actual.domainEvents().isEmpty());
    }

    @Test
    void listing_returns_empty_list() {
        listingQuery(List.of());

        assertTrue(adapter.findDatasetsByAISystemId(AISystemId.generate()).isEmpty());
    }

    @Test
    void listing_rejects_null_ai_system_id_before_accessing_persistence() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> adapter.findDatasetsByAISystemId(null));

        assertEquals("AI system id is required", exception.getMessage());
        verifyNoInteractions(entityManager);
    }

    @SuppressWarnings("unchecked")
    private TypedQuery<DatasetJpaEntity> listingQuery(List<DatasetJpaEntity> entities) {
        TypedQuery<DatasetJpaEntity> query = mock(TypedQuery.class);
        when(entityManager.createQuery(any(String.class), eq(DatasetJpaEntity.class))).thenReturn(query);
        when(query.setParameter(eq("systemId"), any(UUID.class))).thenReturn(query);
        when(query.getResultStream()).thenReturn(entities.stream());
        return query;
    }

    private void failAt(boolean duringPersist, PersistenceException failure) {
        if (duringPersist) {
            doThrow(failure).when(entityManager).persist(any(AISystemDatasetJpaEntity.class));
        } else {
            doThrow(failure).when(entityManager).flush();
        }
    }

    private PSQLException postgres(String state, String constraint, String table) {
        String fields = "SERROR\0C" + state + "\0Mmensaje sin nombre de restricción\0";
        if (constraint != null) {
            fields += "n" + constraint + "\0";
        }
        if (table != null) {
            fields += "t" + table + "\0";
        }
        return new PSQLException(new ServerErrorMessage(fields + "\0"));
    }

    private AISystemDataset association() {
        return new AISystemDataset(AISystemId.generate(), DatasetId.generate(), ASSOCIATED_AT);
    }

    private Dataset dataset(DatasetId id, String name) {
        return Dataset.builder()
                .id(id)
                .name(name)
                .description("Description")
                .createdAt(Instant.parse("2026-09-29T10:00:00Z"))
                .build();
    }
}
