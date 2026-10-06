package com.leori.enia.risk.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskFindingIdTest {

    @Test
    void should_wrap_uuid_value() {
        UUID value = UUID.fromString("10000000-0000-0000-0000-000000000001");

        RiskFindingId id = new RiskFindingId(value);

        assertEquals(value, id.value());
    }

    @Test
    void should_generate_unique_ids() {
        RiskFindingId first = RiskFindingId.generate();
        RiskFindingId second = RiskFindingId.generate();

        assertNotNull(first.value());
        assertNotNull(second.value());
        assertNotEquals(first, second);
    }

    @Test
    void should_parse_string_value() {
        RiskFindingId id = RiskFindingId.of("10000000-0000-0000-0000-000000000001");

        assertEquals(UUID.fromString("10000000-0000-0000-0000-000000000001"), id.value());
    }

    @Test
    void should_require_uuid_value() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> new RiskFindingId(null));

        assertEquals("Risk finding id is required", exception.getMessage());
    }
}
