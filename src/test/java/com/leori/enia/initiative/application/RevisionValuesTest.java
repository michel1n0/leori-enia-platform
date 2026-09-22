package com.leori.enia.initiative.application;

import com.leori.enia.initiative.domain.AIInitiativeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RevisionValuesTest {

    @Test
    void expected_revision_accepts_zero_and_positive_values() {
        AIInitiativeId id = AIInitiativeId.generate();
        assertEquals(0, new ExpectedRevision(id, 0).value());
        assertEquals(id, new ExpectedRevision(id, 12).initiativeId());
        assertEquals(12, new ExpectedRevision(id, 12).value());
    }

    @Test
    void expected_revision_rejects_negative_values() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpectedRevision(AIInitiativeId.generate(), -1));
    }

    @Test
    void versioned_details_carries_revision_and_rejects_negative_values() {
        AIInitiativeDetails details = new AIInitiativeDetails(null, null, "name", "description",
                null, null, false, false, null);
        assertEquals(details, new VersionedAIInitiativeDetails(details, 0).details());
        assertEquals(4, new VersionedAIInitiativeDetails(details, 4).revision());
        assertThrows(IllegalArgumentException.class, () -> new VersionedAIInitiativeDetails(details, -1));
    }
}
