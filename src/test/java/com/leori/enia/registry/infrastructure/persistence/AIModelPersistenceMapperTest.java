package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.domain.AIModel;
import com.leori.enia.registry.domain.AIModelId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIModelPersistenceMapperTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-28T14:00:00.123456789Z");
    private final AIModelPersistenceMapper mapper = new AIModelPersistenceMapper();

    @Test
    void maps_all_business_fields_without_consuming_events() {
        AIModel model = model();
        var events = model.domainEvents();

        AIModelJpaEntity entity = mapper.toEntity(model);

        assertAll(
                () -> assertEquals(model.id().value(), entity.id()),
                () -> assertEquals(model.systemId().value(), entity.systemId()),
                () -> assertEquals(model.name(), entity.name()),
                () -> assertEquals(model.description(), entity.description()),
                () -> assertEquals(model.provider(), entity.provider()),
                () -> assertEquals(CREATED_AT, entity.createdAt()),
                () -> assertEquals(1, events.size()),
                () -> assertEquals(events, model.domainEvents())
        );
    }

    @Test
    void restores_all_fields_with_domain_normalization_and_no_events() {
        AIModel original = model();
        AIModelJpaEntity entity = new AIModelJpaEntity(
                original.id().value(), original.systemId().value(),
                "  Model  ", "  Description  ", "  OpenAI  ", CREATED_AT);

        AIModel restored = mapper.toDomain(entity);

        assertAll(
                () -> assertEquals(original.id(), restored.id()),
                () -> assertEquals(original.systemId(), restored.systemId()),
                () -> assertEquals("Model", restored.name()),
                () -> assertEquals("Description", restored.description()),
                () -> assertEquals("OpenAI", restored.provider()),
                () -> assertEquals(CREATED_AT, restored.createdAt()),
                () -> assertTrue(restored.domainEvents().isEmpty())
        );
    }

    @Test
    void does_not_bypass_domain_validation_of_persisted_text() {
        AIModelJpaEntity valid = mapper.toEntity(model());
        var invalidName = new AIModelJpaEntity(valid.id(), valid.systemId(),
                " \u2003 ", valid.description(), valid.provider(), valid.createdAt());
        var invalidDescription = new AIModelJpaEntity(valid.id(), valid.systemId(),
                valid.name(), "  ", valid.provider(), valid.createdAt());
        var invalidProvider = new AIModelJpaEntity(valid.id(), valid.systemId(),
                valid.name(), valid.description(), null, valid.createdAt());

        assertEquals("Name is required",
                assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidName)).getMessage());
        assertEquals("Description is required",
                assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidDescription)).getMessage());
        assertEquals("Provider is required",
                assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidProvider)).getMessage());
    }

    private AIModel model() {
        return AIModel.builder()
                .id(AIModelId.generate())
                .systemId(AISystemId.generate())
                .name("Model")
                .description("Description")
                .provider("OpenAI")
                .createdAt(CREATED_AT)
                .build();
    }
}
