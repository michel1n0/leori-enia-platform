package com.leori.enia.evidence.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceIdTest {

    private static final UUID VALUE = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Test
    void should_preserve_the_supplied_uuid() {
        assertEquals(VALUE, new EvidenceId(VALUE).value());
    }

    @Test
    void should_compare_ids_by_uuid_value() {
        EvidenceId first = new EvidenceId(VALUE);
        EvidenceId second = new EvidenceId(UUID.fromString(VALUE.toString()));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new EvidenceId(
                UUID.fromString("40000000-0000-0000-0000-000000000002")));
    }

    @Test
    void should_reject_a_null_uuid() {
        var exception = assertThrows(NullPointerException.class, () -> new EvidenceId(null));

        assertEquals("Evidence id is required", exception.getMessage());
    }

    @Test
    void should_generate_distinct_non_null_ids() {
        EvidenceId first = EvidenceId.generate();
        EvidenceId second = EvidenceId.generate();

        assertNotNull(first.value());
        assertNotNull(second.value());
        assertNotEquals(first, second);
    }

    @Test
    void should_parse_a_uuid_string() {
        assertEquals(new EvidenceId(VALUE), EvidenceId.of(VALUE.toString()));
    }

    @Test
    void should_reject_a_malformed_uuid_string() {
        assertThrows(IllegalArgumentException.class, () -> EvidenceId.of("not-a-uuid"));
    }
}
