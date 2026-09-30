package com.leori.enia.risk.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextOfUseTest {

    @Test
    void should_normalize_boundary_whitespace_and_preserve_internal_whitespace() {
        ContextOfUse context = new ContextOfUse(
                "  Fraud   detection\tfor lending  ",
                "  Production   credit\nworkflow  "
        );

        assertEquals("Fraud   detection\tfor lending", context.purpose());
        assertEquals("Production   credit\nworkflow", context.deploymentContext());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_purpose_that_is_null_or_blank_after_trimming(String purpose) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new ContextOfUse(purpose, "Production workflow"));

        assertEquals("Purpose is required", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\u0000", "\u2003", "\u0000 \u2003\t", "\t\u2003\u0000"})
    void should_reject_deployment_context_that_is_null_or_blank_after_trimming(String deploymentContext) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new ContextOfUse("Fraud detection", deploymentContext));

        assertEquals("Deployment context is required", exception.getMessage());
    }

    @Test
    void should_not_impose_arbitrary_text_length_limits() {
        String purpose = "p".repeat(5000);
        String deploymentContext = "d".repeat(5000);

        ContextOfUse context = new ContextOfUse(purpose, deploymentContext);

        assertEquals(purpose, context.purpose());
        assertEquals(deploymentContext, context.deploymentContext());
    }
}
