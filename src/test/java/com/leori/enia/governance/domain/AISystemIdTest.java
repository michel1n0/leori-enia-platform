package com.leori.enia.governance.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AISystemIdTest {

    private static final UUID VALUE = UUID.fromString("a1aa76f0-1cbd-4b51-8eae-241d5fccfd3e");

    @Test
    void should_preserve_the_supplied_uuid() {
        assertEquals(VALUE, new AISystemId(VALUE).value());
    }

    @Test
    void should_compare_ids_by_uuid_value() {
        AISystemId first = new AISystemId(VALUE);
        AISystemId second = new AISystemId(UUID.fromString(VALUE.toString()));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new AISystemId(UUID.fromString("32a47865-d834-4b92-bfa4-ab658a52cf11")));
    }

    @Test
    void should_reject_a_null_uuid() {
        var exception = assertThrows(NullPointerException.class, () -> new AISystemId(null));

        assertEquals("AI system id is required", exception.getMessage());
    }

    @Test
    void should_generate_distinct_non_null_ids() {
        AISystemId first = AISystemId.generate();
        AISystemId second = AISystemId.generate();

        assertNotNull(first.value());
        assertNotNull(second.value());
        assertNotEquals(first, second);
    }

    @Test
    void should_parse_a_uuid_string() {
        assertEquals(new AISystemId(VALUE), AISystemId.of(VALUE.toString()));
    }

    @Test
    void should_reject_a_malformed_uuid_string() {
        assertThrows(IllegalArgumentException.class, () -> AISystemId.of("not-a-uuid"));
    }
}
