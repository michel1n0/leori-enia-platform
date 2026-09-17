CREATE TABLE ai_initiatives (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    preliminary_risk VARCHAR(32) NOT NULL,
    uses_personal_data BOOLEAN NOT NULL,
    impacts_rights BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
