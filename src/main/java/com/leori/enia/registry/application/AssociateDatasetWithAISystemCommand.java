package com.leori.enia.registry.application;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.domain.DatasetId;

public record AssociateDatasetWithAISystemCommand(
        AISystemId aiSystemId,
        DatasetId datasetId
) {
}
