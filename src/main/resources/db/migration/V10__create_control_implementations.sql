CREATE TABLE control_implementations (
    id UUID NOT NULL,
    control_id UUID NOT NULL,
    description TEXT NOT NULL,
    implemented_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_control_implementations
        PRIMARY KEY (id),

    CONSTRAINT fk_control_implementations_control
        FOREIGN KEY (control_id)
        REFERENCES controls(id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION
);
