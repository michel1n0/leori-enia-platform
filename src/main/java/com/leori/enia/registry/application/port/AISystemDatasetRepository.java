package com.leori.enia.registry.application.port;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.domain.AISystemDataset;
import com.leori.enia.registry.domain.Dataset;

import java.util.List;

public interface AISystemDatasetRepository {

    AISystemDataset create(AISystemDataset association);

    List<Dataset> findDatasetsByAISystemId(AISystemId aiSystemId);
}
