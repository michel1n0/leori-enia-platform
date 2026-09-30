package com.leori.enia.risk.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskFindingTest {

    @Test
    void should_normalize_description_and_keep_required_classification() {
        RiskFinding finding = new RiskFinding(
                "  Inaccurate   recommendations\nmay affect eligibility  ",
                Likelihood.MEDIUM,
                ImpactMagnitude.HIGH
        );

        assertEquals("Inaccurate   recommendations\nmay affect eligibility", finding.description());
        assertEquals(Likelihood.MEDIUM, finding.likelihood());
        assertEquals(ImpactMagnitude.HIGH, finding.impactMagnitude());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_description_that_is_null_or_blank_after_trimming(String description) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new RiskFinding(description, Likelihood.LOW, ImpactMagnitude.LOW));

        assertEquals("Description is required", exception.getMessage());
    }

    @Test
    void should_require_likelihood() {
        var exception = assertThrows(NullPointerException.class,
                () -> new RiskFinding("Risk", null, ImpactMagnitude.LOW));

        assertEquals("Likelihood is required", exception.getMessage());
    }

    @Test
    void should_require_impact_magnitude() {
        var exception = assertThrows(NullPointerException.class,
                () -> new RiskFinding("Risk", Likelihood.LOW, null));

        assertEquals("Impact magnitude is required", exception.getMessage());
    }

    @Test
    void should_not_impose_arbitrary_text_length_limits() {
        String description = "d".repeat(5000);

        RiskFinding finding = new RiskFinding(description, Likelihood.HIGH, ImpactMagnitude.HIGH);

        assertEquals(description, finding.description());
    }
}
