CREATE TABLE ai_system_datasets (
    system_id UUID NOT NULL,
    dataset_id UUID NOT NULL,
    associated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_ai_system_datasets
        PRIMARY KEY (system_id, dataset_id),

    CONSTRAINT fk_ai_system_datasets_system
        FOREIGN KEY (system_id)
        REFERENCES ai_systems(id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    CONSTRAINT fk_ai_system_datasets_dataset
        FOREIGN KEY (dataset_id)
        REFERENCES ai_datasets(id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION
);
