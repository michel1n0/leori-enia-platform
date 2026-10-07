package com.leori.enia.risk.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlImplementationIdTest {

    private static final UUID VALUE = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void should_preserve_the_supplied_uuid() {
        assertEquals(VALUE, new ControlImplementationId(VALUE).value());
    }

    @Test
    void should_compare_ids_by_uuid_value() {
        ControlImplementationId first = new ControlImplementationId(VALUE);
        ControlImplementationId second = new ControlImplementationId(UUID.fromString(VALUE.toString()));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new ControlImplementationId(
                UUID.fromString("30000000-0000-0000-0000-000000000002")));
    }

    @Test
    void should_reject_a_null_uuid() {
        var exception = assertThrows(NullPointerException.class, () -> new ControlImplementationId(null));

        assertEquals("Control implementation id is required", exception.getMessage());
    }

    @Test
    void should_generate_distinct_non_null_ids() {
        ControlImplementationId first = ControlImplementationId.generate();
        ControlImplementationId second = ControlImplementationId.generate();

        assertNotNull(first.value());
        assertNotNull(second.value());
        assertNotEquals(first, second);
    }

    @Test
    void should_parse_a_uuid_string() {
        assertEquals(new ControlImplementationId(VALUE), ControlImplementationId.of(VALUE.toString()));
    }

    @Test
    void should_reject_a_malformed_uuid_string() {
        assertThrows(IllegalArgumentException.class, () -> ControlImplementationId.of("not-a-uuid"));
    }
}
