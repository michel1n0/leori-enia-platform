CREATE TABLE ai_models (
    id UUID CONSTRAINT pk_ai_models PRIMARY KEY,
    system_id UUID NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    provider TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_ai_models_system
        FOREIGN KEY (system_id)
        REFERENCES ai_systems(id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION
);
