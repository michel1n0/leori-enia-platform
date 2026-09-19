package com.leori.enia.initiative.application.port;

import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadedAIInitiativeTest {

    private static final Instant NOW = Instant.parse("2026-09-18T14:00:00Z");

    @Test
    void should_retain_the_loaded_version_while_domain_state_changes() {
        AIInitiative initiative = initiative();
        LoadedAIInitiative loaded = new LoadedAIInitiative(initiative, 7);

        initiative.submit(NOW);

        assertSame(initiative, loaded.initiative());
        assertEquals(7L, loaded.version());
    }

    @Test
    void should_reject_a_negative_loaded_version() {
        assertThrows(IllegalArgumentException.class, () -> new LoadedAIInitiative(initiative(), -1));
    }

    @Test
    void should_require_an_aggregate() {
        assertThrows(NullPointerException.class, () -> new LoadedAIInitiative(null, 0));
    }

    private AIInitiative initiative() {
        return AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("AI initiative")
                .description("Loaded revision verification")
                .createdAt(NOW)
                .build();
    }
}
