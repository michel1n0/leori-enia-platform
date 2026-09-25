package com.leori.enia.governance.domain;

import com.leori.enia.governance.domain.event.AISystemRegistered;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AISystemTest {

    private static final AISystemId SYSTEM_ID = AISystemId.of("a1aa76f0-1cbd-4b51-8eae-241d5fccfd3e");
    private static final OrganizationId ORGANIZATION_ID =
            new OrganizationId(UUID.fromString("7d6ebd96-13e3-48f2-913f-13c9b7013a0c"));
    private static final AIInitiativeId SOURCE_ID = AIInitiativeId.of("d8cc78c8-91c9-4211-8241-abd8606c00d9");
    private static final Instant CREATED_AT = Instant.parse("2026-09-25T14:00:00.123456Z");

    @Test
    void should_register_a_system_with_normalized_text_and_one_event() {
        AISystem system = validBuilder().build();

        assertBusinessState(system);
        assertEquals(1, system.domainEvents().size());
        AISystemRegistered event = assertInstanceOf(AISystemRegistered.class, system.domainEvents().getFirst());
        assertAll(
                () -> assertEquals(system.id(), event.systemId()),
                () -> assertEquals(system.organizationId(), event.organizationId()),
                () -> assertEquals(system.sourceInitiativeId(), event.sourceInitiativeId()),
                () -> assertEquals(system.createdAt(), event.occurredAt())
        );
    }

    @Test
    void should_require_system_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().id(null).build());

        assertEquals("AI system id is required", exception.getMessage());
    }

    @Test
    void should_require_organization_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().organizationId(null).build());

        assertEquals("Organization id is required", exception.getMessage());
    }

    @Test
    void should_require_source_initiative_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().sourceInitiativeId(null).build());

        assertEquals("Source initiative id is required", exception.getMessage());
    }

    @Test
    void should_require_created_at() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().createdAt(null).build());

        assertEquals("CreatedAt is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_name_that_is_null_or_blank_after_trimming(String name) {
        var exception = assertThrows(IllegalArgumentException.class, () -> validBuilder().name(name).build());

        assertEquals("Name is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_description_that_is_null_or_blank_after_trimming(String description) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> validBuilder().description(description).build());

        assertEquals("Description is required", exception.getMessage());
    }

    @Test
    void should_not_impose_arbitrary_text_length_limits() {
        String name = "n".repeat(300);
        String description = "d".repeat(5000);

        AISystem system = validBuilder().name(name).description(description).build();

        assertEquals(name, system.name());
        assertEquals(description, system.description());
    }

    @Test
    void should_expose_an_immutable_pending_event_snapshot() {
        AISystem system = validBuilder().build();
        var snapshot = system.domainEvents();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));
        assertEquals(snapshot, system.domainEvents());
    }

    @Test
    void should_clear_pending_events_without_changing_previous_snapshot_or_business_state() {
        AISystem system = validBuilder().build();
        var snapshot = system.domainEvents();
        var event = snapshot.getFirst();

        system.clearDomainEvents();

        assertTrue(system.domainEvents().isEmpty());
        assertEquals(1, snapshot.size());
        assertEquals(event, snapshot.getFirst());
        assertBusinessState(system);
    }

    @Test
    void should_rehydrate_registered_state_with_normalized_text_and_no_events() {
        AISystem system = rehydrateSystem();

        assertBusinessState(system);
        assertTrue(system.domainEvents().isEmpty());
    }

    @Test
    void should_require_system_id_when_rehydrating() {
        var exception = assertThrows(NullPointerException.class, () -> AISystem.rehydrate(
                null, ORGANIZATION_ID, SOURCE_ID, "Name", "Description", AISystemStatus.REGISTERED, CREATED_AT));

        assertEquals("AI system id is required", exception.getMessage());
    }

    @Test
    void should_require_organization_id_when_rehydrating() {
        var exception = assertThrows(NullPointerException.class, () -> AISystem.rehydrate(
                SYSTEM_ID, null, SOURCE_ID, "Name", "Description", AISystemStatus.REGISTERED, CREATED_AT));

        assertEquals("Organization id is required", exception.getMessage());
    }

    @Test
    void should_require_source_initiative_id_when_rehydrating() {
        var exception = assertThrows(NullPointerException.class, () -> AISystem.rehydrate(
                SYSTEM_ID, ORGANIZATION_ID, null, "Name", "Description", AISystemStatus.REGISTERED, CREATED_AT));

        assertEquals("Source initiative id is required", exception.getMessage());
    }

    @Test
    void should_require_status_when_rehydrating() {
        var exception = assertThrows(NullPointerException.class, () -> AISystem.rehydrate(
                SYSTEM_ID, ORGANIZATION_ID, SOURCE_ID, "Name", "Description", null, CREATED_AT));

        assertEquals("AI system status is required", exception.getMessage());
    }

    @Test
    void should_require_created_at_when_rehydrating() {
        var exception = assertThrows(NullPointerException.class, () -> AISystem.rehydrate(
                SYSTEM_ID, ORGANIZATION_ID, SOURCE_ID, "Name", "Description", AISystemStatus.REGISTERED, null));

        assertEquals("CreatedAt is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_name_that_is_null_or_blank_after_trimming_when_rehydrating(String name) {
        var exception = assertThrows(IllegalArgumentException.class, () -> AISystem.rehydrate(
                SYSTEM_ID, ORGANIZATION_ID, SOURCE_ID, name, "Description", AISystemStatus.REGISTERED, CREATED_AT));

        assertEquals("Name is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_description_that_is_null_or_blank_after_trimming_when_rehydrating(String description) {
        var exception = assertThrows(IllegalArgumentException.class, () -> AISystem.rehydrate(
                SYSTEM_ID, ORGANIZATION_ID, SOURCE_ID, "Name", description, AISystemStatus.REGISTERED, CREATED_AT));

        assertEquals("Description is required", exception.getMessage());
    }

    @Test
    void should_not_impose_arbitrary_text_length_limits_when_rehydrating() {
        String name = "n".repeat(300);
        String description = "d".repeat(5000);

        AISystem system = AISystem.rehydrate(
                SYSTEM_ID, ORGANIZATION_ID, SOURCE_ID, name, description, AISystemStatus.REGISTERED, CREATED_AT);

        assertEquals(name, system.name());
        assertEquals(description, system.description());
        assertTrue(system.domainEvents().isEmpty());
    }

    @Test
    void should_keep_restored_events_empty_and_immutable_when_inspected_or_cleared() {
        AISystem system = rehydrateSystem();
        var snapshot = system.domainEvents();

        assertTrue(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(
                new AISystemRegistered(SYSTEM_ID, ORGANIZATION_ID, SOURCE_ID, CREATED_AT)));
        assertEquals(snapshot, system.domainEvents());

        system.clearDomainEvents();

        assertTrue(snapshot.isEmpty());
        assertTrue(system.domainEvents().isEmpty());
        assertBusinessState(system);
    }

    private AISystem rehydrateSystem() {
        return AISystem.rehydrate(
                SYSTEM_ID, ORGANIZATION_ID, SOURCE_ID,
                "  Fraud Detection System  ", "  Detect suspicious transactions  ",
                AISystemStatus.REGISTERED, CREATED_AT);
    }

    private void assertBusinessState(AISystem system) {
        assertAll(
                () -> assertEquals(SYSTEM_ID, system.id()),
                () -> assertEquals(ORGANIZATION_ID, system.organizationId()),
                () -> assertEquals(SOURCE_ID, system.sourceInitiativeId()),
                () -> assertEquals("Fraud Detection System", system.name()),
                () -> assertEquals("Detect suspicious transactions", system.description()),
                () -> assertEquals(AISystemStatus.REGISTERED, system.status()),
                () -> assertEquals(CREATED_AT, system.createdAt())
        );
    }

    private AISystem.Builder validBuilder() {
        return AISystem.builder()
                .id(SYSTEM_ID)
                .organizationId(ORGANIZATION_ID)
                .sourceInitiativeId(SOURCE_ID)
                .name("  Fraud Detection System  ")
                .description("  Detect suspicious transactions  ")
                .createdAt(CREATED_AT);
    }
}
