package com.leori.enia.registry.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasetIdTest {

    private static final UUID VALUE = UUID.fromString("d4dd09f3-4ffe-7e84-beda-574f8ffef064");

    @Test
    void should_preserve_the_supplied_uuid() {
        assertEquals(VALUE, new DatasetId(VALUE).value());
    }

    @Test
    void should_compare_ids_by_uuid_value() {
        DatasetId first = new DatasetId(VALUE);
        DatasetId second = new DatasetId(UUID.fromString(VALUE.toString()));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new DatasetId(UUID.fromString("54c69087-f056-6db4-d0a6-cd87ace0f175")));
    }

    @Test
    void should_reject_a_null_uuid() {
        var exception = assertThrows(NullPointerException.class, () -> new DatasetId(null));

        assertEquals("Dataset id is required", exception.getMessage());
    }

    @Test
    void should_generate_distinct_non_null_ids() {
        DatasetId first = DatasetId.generate();
        DatasetId second = DatasetId.generate();

        assertNotNull(first.value());
        assertNotNull(second.value());
        assertNotEquals(first, second);
    }

    @Test
    void should_parse_a_uuid_string() {
        assertEquals(new DatasetId(VALUE), DatasetId.of(VALUE.toString()));
    }

    @Test
    void should_reject_a_malformed_uuid_string() {
        assertThrows(IllegalArgumentException.class, () -> DatasetId.of("not-a-uuid"));
    }
}
