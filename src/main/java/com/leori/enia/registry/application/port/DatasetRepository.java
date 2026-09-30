package com.leori.enia.registry.application.port;

import com.leori.enia.registry.domain.Dataset;
import com.leori.enia.registry.domain.DatasetId;

import java.util.Optional;

public interface DatasetRepository {

    /** Inserts a new dataset without updating, merging, replacing, or upserting an existing row. */
    Dataset create(Dataset dataset);

    /** Returns the dataset with the given identifier, or empty if none exists. */
    Optional<Dataset> findById(DatasetId id);
}
