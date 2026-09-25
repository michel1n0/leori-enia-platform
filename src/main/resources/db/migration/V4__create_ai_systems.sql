CREATE TABLE ai_systems (
    id UUID CONSTRAINT pk_ai_systems PRIMARY KEY,
    organization_id UUID NOT NULL,
    source_initiative_id UUID NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT ck_ai_systems_status
        CHECK (status = 'REGISTERED'),

    CONSTRAINT uk_ai_systems_source_initiative
        UNIQUE (source_initiative_id),

    CONSTRAINT fk_ai_systems_source_initiative
        FOREIGN KEY (source_initiative_id)
        REFERENCES ai_initiatives(id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION
);
