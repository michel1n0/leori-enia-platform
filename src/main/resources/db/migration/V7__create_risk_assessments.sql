CREATE TABLE risk_assessments (
    id UUID NOT NULL,
    system_id UUID NOT NULL,
    purpose TEXT NOT NULL,
    deployment_context TEXT NOT NULL,
    assessed_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_risk_assessments
        PRIMARY KEY (id),

    CONSTRAINT fk_risk_assessments_system
        FOREIGN KEY (system_id)
        REFERENCES ai_systems(id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION
);

CREATE TABLE risk_assessment_findings (
    risk_assessment_id UUID NOT NULL,
    position INTEGER NOT NULL,
    description TEXT NOT NULL,
    likelihood VARCHAR(16) NOT NULL,
    impact_magnitude VARCHAR(16) NOT NULL,

    CONSTRAINT pk_risk_assessment_findings
        PRIMARY KEY (risk_assessment_id, position),

    CONSTRAINT fk_risk_assessment_findings_assessment
        FOREIGN KEY (risk_assessment_id)
        REFERENCES risk_assessments(id)
        ON DELETE CASCADE
        ON UPDATE NO ACTION,

    CONSTRAINT ck_risk_assessment_findings_position
        CHECK (position >= 0),

    CONSTRAINT ck_risk_assessment_findings_likelihood
        CHECK (likelihood IN ('LOW', 'MEDIUM', 'HIGH')),

    CONSTRAINT ck_risk_assessment_findings_impact_magnitude
        CHECK (impact_magnitude IN ('LOW', 'MEDIUM', 'HIGH'))
);
