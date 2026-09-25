package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.domain.AISystemStatus;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AISystemPersistenceMapperTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-25T14:00:00.123456789Z");
    private final AISystemPersistenceMapper mapper = new AISystemPersistenceMapper();

    @Test
    void maps_all_business_fields_without_consuming_events() {
        AISystem system = system();
        var events = system.domainEvents();

        AISystemJpaEntity entity = mapper.toEntity(system);

        assertAll(
                () -> assertEquals(system.id().value(), entity.id()),
                () -> assertEquals(system.organizationId().value(), entity.organizationId()),
                () -> assertEquals(system.sourceInitiativeId().value(), entity.sourceInitiativeId()),
                () -> assertEquals(system.name(), entity.name()),
                () -> assertEquals(system.description(), entity.description()),
                () -> assertEquals(system.status(), entity.status()),
                () -> assertEquals(CREATED_AT, entity.createdAt()),
                () -> assertEquals(1, events.size()),
                () -> assertEquals(events, system.domainEvents())
        );
    }

    @Test
    void restores_all_fields_with_domain_normalization_and_no_events() {
        AISystem original = system();
        AISystemJpaEntity entity = new AISystemJpaEntity(
                original.id().value(), original.organizationId().value(), original.sourceInitiativeId().value(),
                "  System  ", "  Description  ", AISystemStatus.REGISTERED, CREATED_AT);

        AISystem restored = mapper.toDomain(entity);

        assertAll(
                () -> assertEquals(original.id(), restored.id()),
                () -> assertEquals(original.organizationId(), restored.organizationId()),
                () -> assertEquals(original.sourceInitiativeId(), restored.sourceInitiativeId()),
                () -> assertEquals("System", restored.name()),
                () -> assertEquals("Description", restored.description()),
                () -> assertEquals(AISystemStatus.REGISTERED, restored.status()),
                () -> assertEquals(CREATED_AT, restored.createdAt()),
                () -> assertTrue(restored.domainEvents().isEmpty())
        );
    }

    @Test
    void does_not_bypass_domain_validation_of_persisted_text_or_status() {
        AISystemJpaEntity valid = mapper.toEntity(system());
        var invalidName = new AISystemJpaEntity(valid.id(), valid.organizationId(), valid.sourceInitiativeId(),
                " \u2003 ", valid.description(), valid.status(), valid.createdAt());
        var invalidStatus = new AISystemJpaEntity(valid.id(), valid.organizationId(), valid.sourceInitiativeId(),
                valid.name(), valid.description(), null, valid.createdAt());

        assertEquals("Name is required",
                assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidName)).getMessage());
        assertEquals("AI system status is required",
                assertThrows(NullPointerException.class, () -> mapper.toDomain(invalidStatus)).getMessage());
    }

    private AISystem system() {
        return AISystem.builder().id(AISystemId.generate()).organizationId(OrganizationId.generate())
                .sourceInitiativeId(AIInitiativeId.generate()).name("System").description("Description")
                .createdAt(CREATED_AT).build();
    }
}
