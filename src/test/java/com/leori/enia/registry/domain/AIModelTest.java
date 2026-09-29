package com.leori.enia.registry.domain;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.domain.event.AIModelRegistered;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIModelTest {

    private static final AIModelId MODEL_ID = AIModelId.of("c3cc98f2-3ede-6d73-afc9-463f7feedf59");
    private static final AISystemId SYSTEM_ID = AISystemId.of("a1aa76f0-1cbd-4b51-8eae-241d5fccfd3e");
    private static final Instant CREATED_AT = Instant.parse("2026-09-28T14:00:00.123456Z");

    @Test
    void should_register_a_model_with_normalized_text_and_one_event() {
        AIModel model = validBuilder().build();

        assertBusinessState(model);
        assertEquals(1, model.domainEvents().size());
        AIModelRegistered event = assertInstanceOf(AIModelRegistered.class, model.domainEvents().getFirst());
        assertAll(
                () -> assertEquals(model.id(), event.modelId()),
                () -> assertEquals(model.systemId(), event.systemId()),
                () -> assertEquals(model.createdAt(), event.occurredAt())
        );
    }

    @Test
    void should_require_model_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().id(null).build());

        assertEquals("AI model id is required", exception.getMessage());
    }

    @Test
    void should_require_system_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().systemId(null).build());

        assertEquals("AI system id is required", exception.getMessage());
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_provider_that_is_null_or_blank_after_trimming(String provider) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> validBuilder().provider(provider).build());

        assertEquals("Provider is required", exception.getMessage());
    }

    @Test
    void should_not_impose_arbitrary_text_length_limits() {
        String name = "n".repeat(300);
        String description = "d".repeat(5000);
        String provider = "p".repeat(300);

        AIModel model = validBuilder().name(name).description(description).provider(provider).build();

        assertEquals(name, model.name());
        assertEquals(description, model.description());
        assertEquals(provider, model.provider());
    }

    @Test
    void should_expose_an_immutable_pending_event_snapshot() {
        AIModel model = validBuilder().build();
        var snapshot = model.domainEvents();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));
        assertEquals(snapshot, model.domainEvents());
    }

    @Test
    void should_clear_pending_events_without_changing_previous_snapshot_or_business_state() {
        AIModel model = validBuilder().build();
        var snapshot = model.domainEvents();
        var event = snapshot.getFirst();

        model.clearDomainEvents();

        assertTrue(model.domainEvents().isEmpty());
        assertEquals(1, snapshot.size());
        assertEquals(event, snapshot.getFirst());
        assertBusinessState(model);
    }

    @Test
    void should_rehydrate_registered_state_with_normalized_text_and_no_events() {
        AIModel model = rehydrateModel();

        assertBusinessState(model);
        assertTrue(model.domainEvents().isEmpty());
    }

    @Test
    void should_require_model_id_when_rehydrating() {
        var exception = assertThrows(NullPointerException.class, () -> AIModel.rehydrate(
                null, SYSTEM_ID, "GPT-4", "Large language model", "OpenAI", CREATED_AT));

        assertEquals("AI model id is required", exception.getMessage());
    }

    @Test
    void should_require_system_id_when_rehydrating() {
        var exception = assertThrows(NullPointerException.class, () -> AIModel.rehydrate(
                MODEL_ID, null, "GPT-4", "Large language model", "OpenAI", CREATED_AT));

        assertEquals("AI system id is required", exception.getMessage());
    }

    @Test
    void should_require_created_at_when_rehydrating() {
        var exception = assertThrows(NullPointerException.class, () -> AIModel.rehydrate(
                MODEL_ID, SYSTEM_ID, "GPT-4", "Large language model", "OpenAI", null));

        assertEquals("CreatedAt is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_name_that_is_null_or_blank_after_trimming_when_rehydrating(String name) {
        var exception = assertThrows(IllegalArgumentException.class, () -> AIModel.rehydrate(
                MODEL_ID, SYSTEM_ID, name, "Large language model", "OpenAI", CREATED_AT));

        assertEquals("Name is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_description_that_is_null_or_blank_after_trimming_when_rehydrating(String description) {
        var exception = assertThrows(IllegalArgumentException.class, () -> AIModel.rehydrate(
                MODEL_ID, SYSTEM_ID, "GPT-4", description, "OpenAI", CREATED_AT));

        assertEquals("Description is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_provider_that_is_null_or_blank_after_trimming_when_rehydrating(String provider) {
        var exception = assertThrows(IllegalArgumentException.class, () -> AIModel.rehydrate(
                MODEL_ID, SYSTEM_ID, "GPT-4", "Large language model", provider, CREATED_AT));

        assertEquals("Provider is required", exception.getMessage());
    }

    @Test
    void should_not_impose_arbitrary_text_length_limits_when_rehydrating() {
        String name = "n".repeat(300);
        String description = "d".repeat(5000);
        String provider = "p".repeat(300);

        AIModel model = AIModel.rehydrate(
                MODEL_ID, SYSTEM_ID, name, description, provider, CREATED_AT);

        assertEquals(name, model.name());
        assertEquals(description, model.description());
        assertEquals(provider, model.provider());
        assertTrue(model.domainEvents().isEmpty());
    }

    @Test
    void should_keep_restored_events_empty_and_immutable_when_inspected_or_cleared() {
        AIModel model = rehydrateModel();
        var snapshot = model.domainEvents();

        assertTrue(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(
                new AIModelRegistered(MODEL_ID, SYSTEM_ID, CREATED_AT)));
        assertEquals(snapshot, model.domainEvents());

        model.clearDomainEvents();

        assertTrue(snapshot.isEmpty());
        assertTrue(model.domainEvents().isEmpty());
        assertBusinessState(model);
    }

    private AIModel rehydrateModel() {
        return AIModel.rehydrate(
                MODEL_ID, SYSTEM_ID,
                "  GPT-4  ", "  Large language model  ", "  OpenAI  ",
                CREATED_AT);
    }

    private void assertBusinessState(AIModel model) {
        assertAll(
                () -> assertEquals(MODEL_ID, model.id()),
                () -> assertEquals(SYSTEM_ID, model.systemId()),
                () -> assertEquals("GPT-4", model.name()),
                () -> assertEquals("Large language model", model.description()),
                () -> assertEquals("OpenAI", model.provider()),
                () -> assertEquals(CREATED_AT, model.createdAt())
        );
    }

    private AIModel.Builder validBuilder() {
        return AIModel.builder()
                .id(MODEL_ID)
                .systemId(SYSTEM_ID)
                .name("  GPT-4  ")
                .description("  Large language model  ")
                .provider("  OpenAI  ")
                .createdAt(CREATED_AT);
    }
}
