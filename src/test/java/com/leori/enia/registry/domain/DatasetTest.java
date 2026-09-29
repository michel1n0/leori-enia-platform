package com.leori.enia.registry.domain;

import com.leori.enia.registry.domain.event.DatasetRegistered;
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

class DatasetTest {

    private static final DatasetId DATASET_ID = DatasetId.of("d4dd09f3-4ffe-7e84-beda-574f8ffef064");
    private static final Instant CREATED_AT = Instant.parse("2026-09-29T12:00:00.000000Z");

    @Test
    void should_register_a_dataset_with_normalized_text_and_one_event() {
        Dataset dataset = validBuilder().build();

        assertBusinessState(dataset);
        assertEquals(1, dataset.domainEvents().size());
        DatasetRegistered event = assertInstanceOf(DatasetRegistered.class, dataset.domainEvents().getFirst());
        assertAll(
                () -> assertEquals(dataset.id(), event.datasetId()),
                () -> assertEquals(dataset.createdAt(), event.occurredAt())
        );
    }

    @Test
    void should_require_dataset_id() {
        var exception = assertThrows(NullPointerException.class, () -> validBuilder().id(null).build());

        assertEquals("Dataset id is required", exception.getMessage());
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

        Dataset dataset = validBuilder().name(name).description(description).build();

        assertEquals(name, dataset.name());
        assertEquals(description, dataset.description());
    }

    @Test
    void should_expose_an_immutable_pending_event_snapshot() {
        Dataset dataset = validBuilder().build();
        var snapshot = dataset.domainEvents();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));
        assertEquals(snapshot, dataset.domainEvents());
    }

    @Test
    void should_clear_pending_events_without_changing_previous_snapshot_or_business_state() {
        Dataset dataset = validBuilder().build();
        var snapshot = dataset.domainEvents();
        var event = snapshot.getFirst();

        dataset.clearDomainEvents();

        assertTrue(dataset.domainEvents().isEmpty());
        assertEquals(1, snapshot.size());
        assertEquals(event, snapshot.getFirst());
        assertBusinessState(dataset);
    }

    @Test
    void should_rehydrate_registered_state_with_normalized_text_and_no_events() {
        Dataset dataset = rehydrateDataset();

        assertBusinessState(dataset);
        assertTrue(dataset.domainEvents().isEmpty());
    }

    @Test
    void should_require_dataset_id_when_rehydrating() {
        var exception = assertThrows(NullPointerException.class, () -> Dataset.rehydrate(
                null, "CIFAR-10", "Image classification dataset", CREATED_AT));

        assertEquals("Dataset id is required", exception.getMessage());
    }

    @Test
    void should_require_created_at_when_rehydrating() {
        var exception = assertThrows(NullPointerException.class, () -> Dataset.rehydrate(
                DATASET_ID, "CIFAR-10", "Image classification dataset", null));

        assertEquals("CreatedAt is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_name_that_is_null_or_blank_after_trimming_when_rehydrating(String name) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Dataset.rehydrate(
                DATASET_ID, name, "Image classification dataset", CREATED_AT));

        assertEquals("Name is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_description_that_is_null_or_blank_after_trimming_when_rehydrating(String description) {
        var exception = assertThrows(IllegalArgumentException.class, () -> Dataset.rehydrate(
                DATASET_ID, "CIFAR-10", description, CREATED_AT));

        assertEquals("Description is required", exception.getMessage());
    }

    @Test
    void should_not_impose_arbitrary_text_length_limits_when_rehydrating() {
        String name = "n".repeat(300);
        String description = "d".repeat(5000);

        Dataset dataset = Dataset.rehydrate(DATASET_ID, name, description, CREATED_AT);

        assertEquals(name, dataset.name());
        assertEquals(description, dataset.description());
        assertTrue(dataset.domainEvents().isEmpty());
    }

    @Test
    void should_keep_restored_events_empty_and_immutable_when_inspected_or_cleared() {
        Dataset dataset = rehydrateDataset();
        var snapshot = dataset.domainEvents();

        assertTrue(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(
                new DatasetRegistered(DATASET_ID, CREATED_AT)));
        assertEquals(snapshot, dataset.domainEvents());

        dataset.clearDomainEvents();

        assertTrue(snapshot.isEmpty());
        assertTrue(dataset.domainEvents().isEmpty());
        assertBusinessState(dataset);
    }

    private Dataset rehydrateDataset() {
        return Dataset.rehydrate(
                DATASET_ID,
                "  CIFAR-10  ", "  Image classification dataset  ",
                CREATED_AT);
    }

    private void assertBusinessState(Dataset dataset) {
        assertAll(
                () -> assertEquals(DATASET_ID, dataset.id()),
                () -> assertEquals("CIFAR-10", dataset.name()),
                () -> assertEquals("Image classification dataset", dataset.description()),
                () -> assertEquals(CREATED_AT, dataset.createdAt())
        );
    }

    private Dataset.Builder validBuilder() {
        return Dataset.builder()
                .id(DATASET_ID)
                .name("  CIFAR-10  ")
                .description("  Image classification dataset  ")
                .createdAt(CREATED_AT);
    }
}
