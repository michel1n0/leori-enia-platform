package com.leori.enia.risk.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskAssessmentIdTest {

    private static final UUID VALUE = UUID.fromString("1a4d8475-3d05-49ef-b5a0-4e8bd5167801");

    @Test
    void should_preserve_the_supplied_uuid() {
        assertEquals(VALUE, new RiskAssessmentId(VALUE).value());
    }

    @Test
    void should_compare_ids_by_uuid_value() {
        RiskAssessmentId first = new RiskAssessmentId(VALUE);
        RiskAssessmentId second = new RiskAssessmentId(UUID.fromString(VALUE.toString()));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new RiskAssessmentId(UUID.fromString("427fd8cc-47c8-4379-ae2a-33ac1407858f")));
    }

    @Test
    void should_reject_a_null_uuid() {
        var exception = assertThrows(NullPointerException.class, () -> new RiskAssessmentId(null));

        assertEquals("Risk assessment id is required", exception.getMessage());
    }

    @Test
    void should_generate_distinct_non_null_ids() {
        RiskAssessmentId first = RiskAssessmentId.generate();
        RiskAssessmentId second = RiskAssessmentId.generate();

        assertNotNull(first.value());
        assertNotNull(second.value());
        assertNotEquals(first, second);
    }

    @Test
    void should_parse_a_uuid_string() {
        assertEquals(new RiskAssessmentId(VALUE), RiskAssessmentId.of(VALUE.toString()));
    }

    @Test
    void should_reject_a_malformed_uuid_string() {
        assertThrows(IllegalArgumentException.class, () -> RiskAssessmentId.of("not-a-uuid"));
    }
}
