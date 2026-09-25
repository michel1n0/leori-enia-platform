package com.leori.enia.initiative.application;

import com.leori.enia.initiative.domain.AIInitiativeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevisionValuesTest {

    @Test
    void expected_revision_matches_the_same_id_and_revision() {
        AIInitiativeId id = AIInitiativeId.generate();
        assertTrue(new ExpectedRevision(id, 7).matches(id, 7));
    }

    @Test
    void expected_revision_does_not_match_another_id() {
        ExpectedRevision expected = new ExpectedRevision(AIInitiativeId.generate(), 7);
        assertFalse(expected.matches(AIInitiativeId.generate(), 7));
    }

    @Test
    void expected_revision_does_not_match_another_revision() {
        AIInitiativeId id = AIInitiativeId.generate();
        assertFalse(new ExpectedRevision(id, 7).matches(id, 8));
    }

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
                null, null, false, false, null, null);
        assertEquals(details, new VersionedAIInitiativeDetails(details, 0).details());
        assertEquals(4, new VersionedAIInitiativeDetails(details, 4).revision());
        assertThrows(IllegalArgumentException.class, () -> new VersionedAIInitiativeDetails(details, -1));
    }
}
