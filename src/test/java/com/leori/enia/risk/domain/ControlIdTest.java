package com.leori.enia.risk.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlIdTest {

    @Test
    void should_wrap_uuid_value() {
        UUID value = UUID.fromString("20000000-0000-0000-0000-000000000001");

        ControlId id = new ControlId(value);

        assertEquals(value, id.value());
    }

    @Test
    void should_generate_unique_ids() {
        ControlId first = ControlId.generate();
        ControlId second = ControlId.generate();

        assertNotNull(first.value());
        assertNotNull(second.value());
        assertNotEquals(first, second);
    }

    @Test
    void should_parse_string_value() {
        ControlId id = ControlId.of("20000000-0000-0000-0000-000000000001");

        assertEquals(UUID.fromString("20000000-0000-0000-0000-000000000001"), id.value());
    }

    @Test
    void should_require_uuid_value() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> new ControlId(null));

        assertEquals("Control id is required", exception.getMessage());
    }
}
