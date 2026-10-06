ALTER TABLE risk_assessment_findings
    ADD COLUMN id UUID;

UPDATE risk_assessment_findings
SET id = CAST(
    '00000000-0000-0000-0000-' || LPAD(CAST((
        SELECT COUNT(*)
        FROM risk_assessment_findings existing_finding
        WHERE CAST(existing_finding.risk_assessment_id AS VARCHAR) < CAST(risk_assessment_findings.risk_assessment_id AS VARCHAR)
            OR (
                existing_finding.risk_assessment_id = risk_assessment_findings.risk_assessment_id
                AND existing_finding.position <= risk_assessment_findings.position
            )
    ) AS VARCHAR), 12, '0')
    AS UUID
)
WHERE id IS NULL;

ALTER TABLE risk_assessment_findings
    ALTER COLUMN id SET NOT NULL;

ALTER TABLE risk_assessment_findings
    DROP CONSTRAINT pk_risk_assessment_findings;

ALTER TABLE risk_assessment_findings
    ADD CONSTRAINT pk_risk_assessment_findings
        PRIMARY KEY (id);

ALTER TABLE risk_assessment_findings
    ADD CONSTRAINT uq_risk_assessment_findings_assessment_position
        UNIQUE (risk_assessment_id, position);
