package com.leori.enia.registry.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AIModelIdTest {

    private static final UUID VALUE = UUID.fromString("b2bb87f1-2dce-5c62-9fbf-352e6fddef4f");

    @Test
    void should_preserve_the_supplied_uuid() {
        assertEquals(VALUE, new AIModelId(VALUE).value());
    }

    @Test
    void should_compare_ids_by_uuid_value() {
        AIModelId first = new AIModelId(VALUE);
        AIModelId second = new AIModelId(UUID.fromString(VALUE.toString()));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new AIModelId(UUID.fromString("43b58976-e945-5ca3-cf95-bc769b63df22")));
    }

    @Test
    void should_reject_a_null_uuid() {
        var exception = assertThrows(NullPointerException.class, () -> new AIModelId(null));

        assertEquals("AI model id is required", exception.getMessage());
    }

    @Test
    void should_generate_distinct_non_null_ids() {
        AIModelId first = AIModelId.generate();
        AIModelId second = AIModelId.generate();

        assertNotNull(first.value());
        assertNotNull(second.value());
        assertNotEquals(first, second);
    }

    @Test
    void should_parse_a_uuid_string() {
        assertEquals(new AIModelId(VALUE), AIModelId.of(VALUE.toString()));
    }

    @Test
    void should_reject_a_malformed_uuid_string() {
        assertThrows(IllegalArgumentException.class, () -> AIModelId.of("not-a-uuid"));
    }
}
