package com.leori.enia.risk.application;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.risk.application.exception.RiskAssessmentNotFoundException;
import com.leori.enia.risk.application.exception.RiskFindingNotFoundException;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.ContextOfUse;
import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlRepository;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFinding;
import com.leori.enia.risk.domain.RiskFindingId;
import com.leori.enia.risk.domain.event.ControlDefined;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefineControlUseCaseTest {

    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-02T10:15:30Z");
    private static final RiskFindingId FINDING_ID = RiskFindingId.of("10000000-0000-0000-0000-000000000001");

    @Test
    void defines_control_for_existing_assessment_finding() {
        RiskAssessment assessment = assessment(FINDING_ID);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository(assessment);
        InMemoryControlRepository controlRepository = new InMemoryControlRepository();
        CountingClock clock = new CountingClock(CREATED_AT);
        DefineControlUseCase useCase = new DefineControlUseCase(assessmentRepository, controlRepository, clock);

        Control result = useCase.execute(command(assessment.id(), FINDING_ID));

        assertSame(result, controlRepository.createdControl);
        assertNotNull(result.id());
        assertEquals(assessment.id(), result.riskAssessmentId());
        assertEquals(FINDING_ID, result.riskFindingId());
        assertEquals("Human review gate", result.name());
        assertEquals("Require documented human approval before deployment.", result.description());
        assertEquals(CREATED_AT, result.createdAt());
        assertEquals(1, assessmentRepository.finds);
        assertEquals(assessment.id(), assessmentRepository.lastFindId);
        assertEquals(1, controlRepository.creates);
        assertEquals(1, clock.instantCalls);
    }

    @Test
    void generated_control_ids_are_new() {
        RiskAssessment assessment = assessment(FINDING_ID);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository(assessment);
        InMemoryControlRepository controlRepository = new InMemoryControlRepository();
        DefineControlUseCase useCase = new DefineControlUseCase(
                assessmentRepository, controlRepository, Clock.fixed(CREATED_AT, ZoneOffset.UTC));

        Control first = useCase.execute(command(assessment.id(), FINDING_ID));
        Control second = useCase.execute(command(assessment.id(), FINDING_ID));

        assertNotNull(first.id());
        assertNotNull(second.id());
        assertNotEquals(first.id(), second.id());
    }

    @Test
    void pending_control_defined_event_is_preserved() {
        RiskAssessment assessment = assessment(FINDING_ID);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository(assessment);
        InMemoryControlRepository controlRepository = new InMemoryControlRepository();
        DefineControlUseCase useCase = new DefineControlUseCase(
                assessmentRepository, controlRepository, Clock.fixed(CREATED_AT, ZoneOffset.UTC));

        Control result = useCase.execute(command(assessment.id(), FINDING_ID));

        assertEquals(1, result.domainEvents().size());
        ControlDefined event = assertInstanceOf(ControlDefined.class, result.domainEvents().getFirst());
        assertEquals(result.id(), event.controlId());
        assertEquals(assessment.id(), event.riskAssessmentId());
        assertEquals(FINDING_ID, event.riskFindingId());
        assertEquals(CREATED_AT, event.occurredAt());
    }

    @Test
    void missing_assessment_throws_without_control_persistence_or_clock_access() {
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository();
        InMemoryControlRepository controlRepository = new InMemoryControlRepository();
        CountingClock clock = new CountingClock(CREATED_AT);
        DefineControlUseCase useCase = new DefineControlUseCase(assessmentRepository, controlRepository, clock);
        RiskAssessmentId missingId = RiskAssessmentId.generate();

        RiskAssessmentNotFoundException exception = assertThrows(
                RiskAssessmentNotFoundException.class,
                () -> useCase.execute(command(missingId, FINDING_ID))
        );

        assertEquals("Risk assessment not found: " + missingId, exception.getMessage());
        assertEquals(1, assessmentRepository.finds);
        assertEquals(0, controlRepository.creates);
        assertEquals(0, clock.instantCalls);
    }

    @Test
    void finding_not_belonging_to_assessment_throws_without_control_persistence_or_clock_access() {
        RiskAssessment assessment = assessment(FINDING_ID);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository(assessment);
        InMemoryControlRepository controlRepository = new InMemoryControlRepository();
        CountingClock clock = new CountingClock(CREATED_AT);
        DefineControlUseCase useCase = new DefineControlUseCase(assessmentRepository, controlRepository, clock);
        RiskFindingId otherFindingId = RiskFindingId.generate();

        RiskFindingNotFoundException exception = assertThrows(
                RiskFindingNotFoundException.class,
                () -> useCase.execute(command(assessment.id(), otherFindingId))
        );

        assertEquals("Risk finding not found: " + otherFindingId + " in risk assessment: " + assessment.id(),
                exception.getMessage());
        assertEquals(1, assessmentRepository.finds);
        assertEquals(0, controlRepository.creates);
        assertEquals(0, clock.instantCalls);
    }

    @Test
    void null_command_fails_before_repository_access() {
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository();
        InMemoryControlRepository controlRepository = new InMemoryControlRepository();
        CountingClock clock = new CountingClock(CREATED_AT);
        DefineControlUseCase useCase = new DefineControlUseCase(assessmentRepository, controlRepository, clock);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Define control command is required", exception.getMessage());
        assertEquals(0, assessmentRepository.finds);
        assertEquals(0, controlRepository.creates);
        assertEquals(0, clock.instantCalls);
    }

    @Test
    void invalid_control_domain_input_propagates_without_persistence() {
        RiskAssessment assessment = assessment(FINDING_ID);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository(assessment);
        InMemoryControlRepository controlRepository = new InMemoryControlRepository();
        CountingClock clock = new CountingClock(CREATED_AT);
        DefineControlUseCase useCase = new DefineControlUseCase(assessmentRepository, controlRepository, clock);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(new DefineControlCommand(
                        assessment.id(), FINDING_ID, "   ", "Valid description")));

        assertEquals("Name is required", exception.getMessage());
        assertEquals(1, assessmentRepository.finds);
        assertEquals(1, clock.instantCalls);
        assertEquals(0, controlRepository.creates);
    }

    @Test
    void control_repository_failure_propagates_unchanged() {
        RiskAssessment assessment = assessment(FINDING_ID);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository(assessment);
        InMemoryControlRepository controlRepository = new InMemoryControlRepository();
        RuntimeException failure = new RuntimeException("Storage unavailable");
        controlRepository.failure = failure;
        DefineControlUseCase useCase = new DefineControlUseCase(
                assessmentRepository, controlRepository, Clock.fixed(CREATED_AT, ZoneOffset.UTC));

        assertSame(failure, assertThrows(RuntimeException.class,
                () -> useCase.execute(command(assessment.id(), FINDING_ID))));

        assertEquals(1, controlRepository.creates);
    }

    private DefineControlCommand command(RiskAssessmentId assessmentId, RiskFindingId findingId) {
        return new DefineControlCommand(
                assessmentId,
                findingId,
                "  Human review gate  ",
                "  Require documented human approval before deployment.  "
        );
    }

    private RiskAssessment assessment(RiskFindingId findingId) {
        return RiskAssessment.builder()
                .id(RiskAssessmentId.generate())
                .systemId(AISystemId.generate())
                .contextOfUse(new ContextOfUse("Governance approval", "Public sector deployment"))
                .findings(List.of(new RiskFinding(findingId, "Bias risk", Likelihood.MEDIUM, ImpactMagnitude.HIGH)))
                .assessedAt(ASSESSED_AT)
                .build();
    }

    private static final class InMemoryRiskAssessmentRepository implements RiskAssessmentRepository {
        private final Map<RiskAssessmentId, RiskAssessment> assessments = new HashMap<>();
        private RiskAssessmentId lastFindId;
        private int finds;

        private InMemoryRiskAssessmentRepository(RiskAssessment... assessments) {
            for (RiskAssessment assessment : assessments) {
                this.assessments.put(assessment.id(), assessment);
            }
        }

        @Override
        public RiskAssessment create(RiskAssessment assessment) {
            throw new AssertionError("Defining a control must not create a risk assessment");
        }

        @Override
        public Optional<RiskAssessment> findById(RiskAssessmentId id) {
            finds++;
            lastFindId = id;
            return Optional.ofNullable(assessments.get(id));
        }
    }

    private static final class InMemoryControlRepository implements ControlRepository {
        private Control createdControl;
        private RuntimeException failure;
        private int creates;

        @Override
        public Control create(Control control) {
            creates++;
            createdControl = control;
            if (failure != null) {
                throw failure;
            }
            return control;
        }
    }

    private static final class CountingClock extends Clock {
        private final Instant fixedInstant;
        private int instantCalls;

        CountingClock(Instant fixedInstant) {
            this.fixedInstant = fixedInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            instantCalls++;
            return fixedInstant;
        }
    }
}
