CREATE TABLE evidence (
    id UUID NOT NULL,
    control_implementation_id UUID NOT NULL,
    description TEXT NOT NULL,
    reference TEXT NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_evidence
        PRIMARY KEY (id),

    CONSTRAINT fk_evidence_control_implementation
        FOREIGN KEY (control_implementation_id)
        REFERENCES control_implementations(id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION
);
