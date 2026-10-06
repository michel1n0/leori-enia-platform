ALTER TABLE risk_assessment_findings
    ADD CONSTRAINT uq_risk_assessment_findings_assessment_id
        UNIQUE (risk_assessment_id, id);

CREATE TABLE controls (
    id UUID NOT NULL,
    risk_assessment_id UUID NOT NULL,
    risk_finding_id UUID NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_controls
        PRIMARY KEY (id),

    CONSTRAINT fk_controls_risk_assessment_finding
        FOREIGN KEY (risk_assessment_id, risk_finding_id)
        REFERENCES risk_assessment_findings(risk_assessment_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION
);
