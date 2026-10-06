package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.domain.ContextOfUse;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFinding;
import com.leori.enia.risk.domain.RiskFindingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskAssessmentPersistenceMapperTest {

    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00.123456789Z");
    private final RiskAssessmentPersistenceMapper mapper = new RiskAssessmentPersistenceMapper();

    @Test
    void maps_all_business_fields_and_ordered_duplicate_findings_without_consuming_events() {
        RiskAssessment assessment = assessment();
        var events = assessment.domainEvents();

        RiskAssessmentJpaEntity entity = mapper.toEntity(assessment);

        assertAll(
                () -> assertEquals(assessment.id().value(), entity.id()),
                () -> assertEquals(assessment.systemId().value(), entity.systemId()),
                () -> assertEquals(assessment.contextOfUse().purpose(), entity.purpose()),
                () -> assertEquals(assessment.contextOfUse().deploymentContext(), entity.deploymentContext()),
                () -> assertEquals(ASSESSED_AT, entity.assessedAt()),
                () -> assertEquals(3, entity.findings().size()),
                () -> assertFinding(assessment.findings().get(0), entity.findings().get(0)),
                () -> assertFinding(assessment.findings().get(1), entity.findings().get(1)),
                () -> assertFinding(assessment.findings().get(2), entity.findings().get(2)),
                () -> assertEquals(assessment.findings().get(0).description(), assessment.findings().get(2).description()),
                () -> assertTrue(!assessment.findings().get(0).id().equals(assessment.findings().get(2).id())),
                () -> assertEquals(1, events.size()),
                () -> assertEquals(events, assessment.domainEvents())
        );
    }

    @Test
    void restores_all_fields_with_domain_normalization_ordered_duplicates_and_no_events() {
        RiskAssessment original = assessment();
        RiskAssessmentJpaEntity entity = new RiskAssessmentJpaEntity(
                original.id().value(),
                original.systemId().value(),
                "  Governance approval  ",
                "  Public sector deployment  ",
                List.of(
                        new RiskAssessmentFindingJpaEntity(
                                original.findings().get(0).id().value(),
                                "  Bias risk  ", Likelihood.MEDIUM, ImpactMagnitude.HIGH),
                        new RiskAssessmentFindingJpaEntity(
                                original.findings().get(1).id().value(),
                                "Privacy risk", Likelihood.LOW, ImpactMagnitude.MEDIUM),
                        new RiskAssessmentFindingJpaEntity(
                                original.findings().get(2).id().value(),
                                "  Bias risk  ", Likelihood.MEDIUM, ImpactMagnitude.HIGH)
                ),
                ASSESSED_AT);

        RiskAssessment restored = mapper.toDomain(entity);

        assertAll(
                () -> assertEquals(original.id(), restored.id()),
                () -> assertEquals(original.systemId(), restored.systemId()),
                () -> assertEquals("Governance approval", restored.contextOfUse().purpose()),
                () -> assertEquals("Public sector deployment", restored.contextOfUse().deploymentContext()),
                () -> assertEquals(ASSESSED_AT, restored.assessedAt()),
                () -> assertEquals(original.findings(), restored.findings()),
                () -> assertTrue(restored.domainEvents().isEmpty())
        );
    }

    @Test
    void does_not_bypass_domain_validation_of_persisted_context_or_findings() {
        RiskAssessmentJpaEntity valid = mapper.toEntity(assessment());
        var invalidPurpose = new RiskAssessmentJpaEntity(valid.id(), valid.systemId(), " \u2003 ",
                valid.deploymentContext(), valid.findings(), valid.assessedAt());
        var invalidFinding = new RiskAssessmentJpaEntity(valid.id(), valid.systemId(), valid.purpose(),
                valid.deploymentContext(), List.of(new RiskAssessmentFindingJpaEntity(
                RiskFindingId.generate().value(), " ", Likelihood.LOW, ImpactMagnitude.LOW)), valid.assessedAt());

        assertEquals("Purpose is required",
                assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidPurpose)).getMessage());
        assertEquals("Description is required",
                assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(invalidFinding)).getMessage());
    }

    private void assertFinding(RiskFinding expected, RiskAssessmentFindingJpaEntity actual) {
        assertAll(
                () -> assertEquals(expected.id().value(), actual.id()),
                () -> assertEquals(expected.description(), actual.description()),
                () -> assertEquals(expected.likelihood(), actual.likelihood()),
                () -> assertEquals(expected.impactMagnitude(), actual.impactMagnitude())
        );
    }

    private RiskAssessment assessment() {
        return RiskAssessment.builder()
                .id(RiskAssessmentId.generate())
                .systemId(AISystemId.generate())
                .contextOfUse(new ContextOfUse("Governance approval", "Public sector deployment"))
                .findings(List.of(
                        new RiskFinding(RiskFindingId.generate(), "Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH),
                        new RiskFinding(RiskFindingId.generate(), "Privacy risk", Likelihood.LOW, ImpactMagnitude.MEDIUM),
                        new RiskFinding(RiskFindingId.generate(), "Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH)
                ))
                .assessedAt(ASSESSED_AT)
                .build();
    }
}
