package com.leori.enia.registry.domain;

import com.leori.enia.governance.domain.AISystemId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AISystemDatasetTest {

    private static final Instant ASSOCIATED_AT = Instant.parse("2026-10-06T10:15:30Z");

    @Test
    void creates_association_preserving_values() {
        AISystemId aiSystemId = AISystemId.generate();
        DatasetId datasetId = DatasetId.generate();

        AISystemDataset association = new AISystemDataset(aiSystemId, datasetId, ASSOCIATED_AT);

        assertEquals(aiSystemId, association.aiSystemId());
        assertEquals(datasetId, association.datasetId());
        assertEquals(ASSOCIATED_AT, association.associatedAt());
    }

    @Test
    void requires_ai_system_id() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new AISystemDataset(null, DatasetId.generate(), ASSOCIATED_AT));

        assertEquals("AI system id is required", exception.getMessage());
    }

    @Test
    void requires_dataset_id() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new AISystemDataset(AISystemId.generate(), null, ASSOCIATED_AT));

        assertEquals("Dataset id is required", exception.getMessage());
    }

    @Test
    void requires_associated_at() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new AISystemDataset(AISystemId.generate(), DatasetId.generate(), null));

        assertEquals("Associated at is required", exception.getMessage());
    }

}
