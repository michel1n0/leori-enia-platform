package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetPersistenceMapperTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-28T14:00:00.123456789Z");
    private final DatasetPersistenceMapper mapper = new DatasetPersistenceMapper();

    @Test
    void maps_all_fields_without_consuming_pending_events() {
        Dataset dataset = dataset();
        var events = dataset.domainEvents();

        DatasetJpaEntity entity = mapper.toEntity(dataset);

        assertAll(
                () -> assertEquals(dataset.id().value(), entity.id()),
                () -> assertEquals(dataset.name(), entity.name()),
                () -> assertEquals(dataset.description(), entity.description()),
                () -> assertEquals(CREATED_AT, entity.createdAt()),
                () -> assertEquals(1, events.size()),
                () -> assertEquals(events, dataset.domainEvents())
        );
    }

    @Test
    void rehydrates_all_fields_with_domain_normalization_and_no_events() {
        Dataset original = dataset();
        DatasetJpaEntity entity = new DatasetJpaEntity(
                original.id().value(), "  Dataset  ", "  Description  ", CREATED_AT);

        Dataset restored = mapper.toDomain(entity);

        assertAll(
                () -> assertEquals(original.id(), restored.id()),
                () -> assertEquals("Dataset", restored.name()),
                () -> assertEquals("Description", restored.description()),
                () -> assertEquals(CREATED_AT, restored.createdAt()),
                () -> assertTrue(restored.domainEvents().isEmpty())
        );
    }

    @Test
    void preserves_domain_validation_of_persisted_text() {
        DatasetJpaEntity valid = mapper.toEntity(dataset());
        var invalidName = new DatasetJpaEntity(valid.id(), " \u2003 ", valid.description(), valid.createdAt());
        var invalidDescription = new DatasetJpaEntity(valid.id(), valid.name(), "  ", valid.createdAt());

        assertEquals("Name is required",
                assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidName)).getMessage());
        assertEquals("Description is required",
                assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidDescription)).getMessage());
    }

    private Dataset dataset() {
        return Dataset.builder()
                .id(DatasetId.generate())
                .name("Dataset")
                .description("Description")
                .createdAt(CREATED_AT)
                .build();
    }
}
