package com.leori.enia.risk.application;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.application.exception.RiskAssessmentNotFoundException;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.ContextOfUse;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFinding;
import com.leori.enia.risk.domain.RiskFindingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetRiskAssessmentUseCaseTest {

    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00Z");

    @Test
    void returns_existing_assessment() {
        RiskAssessment assessment = assessment();
        InMemoryRiskAssessmentRepository repository = new InMemoryRiskAssessmentRepository(assessment);
        GetRiskAssessmentUseCase useCase = new GetRiskAssessmentUseCase(repository);

        RiskAssessment result = useCase.execute(assessment.id());

        assertSame(assessment, result);
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void missing_assessment_fails() {
        InMemoryRiskAssessmentRepository repository = new InMemoryRiskAssessmentRepository();
        GetRiskAssessmentUseCase useCase = new GetRiskAssessmentUseCase(repository);
        RiskAssessmentId missingId = RiskAssessmentId.generate();

        RiskAssessmentNotFoundException exception = assertThrows(
                RiskAssessmentNotFoundException.class,
                () -> useCase.execute(missingId)
        );

        assertEquals("Risk assessment not found: " + missingId, exception.getMessage());
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void null_id_fails_before_repository_access() {
        InMemoryRiskAssessmentRepository repository = new InMemoryRiskAssessmentRepository();
        GetRiskAssessmentUseCase useCase = new GetRiskAssessmentUseCase(repository);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Risk assessment id is required", exception.getMessage());
        assertEquals(0, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void read_does_not_write() {
        RiskAssessment assessment = assessment();
        InMemoryRiskAssessmentRepository repository = new InMemoryRiskAssessmentRepository(assessment);
        GetRiskAssessmentUseCase useCase = new GetRiskAssessmentUseCase(repository);

        useCase.execute(assessment.id());

        assertEquals(0, repository.creates);
    }

    private RiskAssessment assessment() {
        return RiskAssessment.builder()
                .id(RiskAssessmentId.generate())
                .systemId(AISystemId.generate())
                .contextOfUse(new ContextOfUse("Governance approval", "Public sector deployment"))
                .findings(List.of(new RiskFinding(RiskFindingId.generate(), "Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH)))
                .assessedAt(ASSESSED_AT)
                .build();
    }

    private static final class InMemoryRiskAssessmentRepository implements RiskAssessmentRepository {
        private final Map<RiskAssessmentId, RiskAssessment> assessments = new HashMap<>();
        private int finds;
        private int creates;

        private InMemoryRiskAssessmentRepository(RiskAssessment... assessments) {
            for (RiskAssessment assessment : assessments) {
                this.assessments.put(assessment.id(), assessment);
            }
        }

        @Override
        public RiskAssessment create(RiskAssessment assessment) {
            creates++;
            throw new AssertionError("Read use case must not create a risk assessment");
        }

        @Override
        public Optional<RiskAssessment> findById(RiskAssessmentId id) {
            finds++;
            return Optional.ofNullable(assessments.get(id));
        }
    }
}
