package com.leori.enia.registry.application.port;

import com.leori.enia.registry.domain.Dataset;

public interface DatasetRepository {

    /** Inserts a new dataset without updating, merging, replacing, or upserting an existing row. */
    Dataset create(Dataset dataset);
}
