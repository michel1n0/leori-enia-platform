CREATE TABLE ai_datasets (
    id UUID NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_ai_datasets PRIMARY KEY (id)
);
