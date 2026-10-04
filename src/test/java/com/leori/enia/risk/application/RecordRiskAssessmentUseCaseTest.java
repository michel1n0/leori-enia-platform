package com.leori.enia.risk.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.governance.domain.AISystemStatus;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.ImpactMagnitude;
import com.leori.enia.risk.domain.Likelihood;
import com.leori.enia.risk.domain.RiskAssessment;
import com.leori.enia.risk.domain.event.RiskAssessmentRecorded;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RecordRiskAssessmentUseCaseTest {

    private static final Instant SYSTEM_CREATED_AT = Instant.parse("2026-09-25T13:00:00Z");
    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T14:00:00Z");
    private static final Clock CLOCK = Clock.fixed(ASSESSED_AT, ZoneOffset.UTC);

    @Test
    void records_risk_assessment_for_existing_system() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository();
        CountingClock clock = new CountingClock(ASSESSED_AT);
        RecordRiskAssessmentUseCase useCase = new RecordRiskAssessmentUseCase(
                systemRepository, assessmentRepository, clock);

        RiskAssessment result = useCase.execute(command(system.id()));

        assertSame(result, assessmentRepository.createdAssessment);
        assertNotNull(result.id());
        assertEquals(system.id(), result.systemId());
        assertEquals("Governance approval", result.contextOfUse().purpose());
        assertEquals("Public sector deployment", result.contextOfUse().deploymentContext());
        assertEquals(ASSESSED_AT, result.assessedAt());
        assertEquals(List.of(
                "Bias risk",
                "Privacy risk",
                "Bias risk"
        ), result.findings().stream().map(finding -> finding.description()).toList());
        assertEquals(List.of(
                Likelihood.MEDIUM,
                Likelihood.LOW,
                Likelihood.MEDIUM
        ), result.findings().stream().map(finding -> finding.likelihood()).toList());
        assertEquals(List.of(
                ImpactMagnitude.HIGH,
                ImpactMagnitude.MEDIUM,
                ImpactMagnitude.HIGH
        ), result.findings().stream().map(finding -> finding.impactMagnitude()).toList());
        assertEquals(1, systemRepository.findCount);
        assertEquals(system.id(), systemRepository.lastFindId);
        assertEquals(1, assessmentRepository.createCount);
        assertEquals(1, clock.instantCalls);
    }

    @Test
    void generated_risk_assessment_id_is_unique_per_call() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository();
        RecordRiskAssessmentUseCase useCase = new RecordRiskAssessmentUseCase(
                systemRepository, assessmentRepository, CLOCK);

        RiskAssessment first = useCase.execute(command(system.id()));
        assessmentRepository.reset();
        RiskAssessment second = useCase.execute(command(system.id()));

        assertNotNull(first.id());
        assertNotNull(second.id());
        assertNotEquals(first.id(), second.id());
    }

    @Test
    void pending_risk_assessment_recorded_event_is_preserved() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository();
        RecordRiskAssessmentUseCase useCase = new RecordRiskAssessmentUseCase(
                systemRepository, assessmentRepository, CLOCK);

        RiskAssessment result = useCase.execute(command(system.id()));

        assertEquals(1, result.domainEvents().size());
        RiskAssessmentRecorded event = assertInstanceOf(
                RiskAssessmentRecorded.class,
                result.domainEvents().getFirst()
        );
        assertEquals(result.id(), event.riskAssessmentId());
        assertEquals(system.id(), event.systemId());
        assertEquals(ASSESSED_AT, event.occurredAt());
    }

    @Test
    void missing_system_throws_without_risk_assessment_persistence_or_clock_access() {
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository();
        Clock clock = mock(Clock.class);
        RecordRiskAssessmentUseCase useCase = new RecordRiskAssessmentUseCase(
                systemRepository, assessmentRepository, clock);
        AISystemId missingId = AISystemId.generate();

        AISystemNotFoundException exception = assertThrows(
                AISystemNotFoundException.class,
                () -> useCase.execute(command(missingId))
        );

        assertEquals("AI system not found: " + missingId, exception.getMessage());
        assertEquals(1, systemRepository.findCount);
        assertEquals(missingId, systemRepository.lastFindId);
        assertEquals(0, assessmentRepository.createCount);
        verifyNoInteractions(clock);
    }

    @Test
    void null_command_fails_before_repository_access() {
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository();
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository();
        Clock clock = mock(Clock.class);
        RecordRiskAssessmentUseCase useCase = new RecordRiskAssessmentUseCase(
                systemRepository, assessmentRepository, clock);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("Record risk assessment command is required", exception.getMessage());
        assertEquals(0, systemRepository.findCount);
        assertEquals(0, assessmentRepository.createCount);
        verifyNoInteractions(clock);
    }

    @Test
    void invalid_domain_input_does_not_reach_persistence() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository();
        CountingClock clock = new CountingClock(ASSESSED_AT);
        RecordRiskAssessmentUseCase useCase = new RecordRiskAssessmentUseCase(
                systemRepository, assessmentRepository, clock);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(new RecordRiskAssessmentCommand(
                        system.id(), "   ", "Deployment", findings())));

        assertEquals("Purpose is required", exception.getMessage());
        assertEquals(1, systemRepository.findCount);
        assertEquals(1, clock.instantCalls);
        assertEquals(0, assessmentRepository.createCount);
    }

    @Test
    void null_finding_is_rejected_before_persistence() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository();
        RecordRiskAssessmentUseCase useCase = new RecordRiskAssessmentUseCase(
                systemRepository, assessmentRepository, CLOCK);

        List<RecordRiskFindingCommand> findings = new ArrayList<>();
        findings.add(null);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> useCase.execute(new RecordRiskAssessmentCommand(
                        system.id(), "Purpose", "Deployment", findings)));

        assertEquals("Risk finding is required", exception.getMessage());
        assertEquals(0, assessmentRepository.createCount);
    }

    @Test
    void repository_failure_propagates_unchanged() {
        AISystem system = registeredSystem();
        InMemoryAISystemRepository systemRepository = new InMemoryAISystemRepository(system);
        InMemoryRiskAssessmentRepository assessmentRepository = new InMemoryRiskAssessmentRepository();
        RuntimeException failure = new RuntimeException("Storage unavailable");
        assessmentRepository.failure = failure;
        RecordRiskAssessmentUseCase useCase = new RecordRiskAssessmentUseCase(
                systemRepository, assessmentRepository, CLOCK);

        assertSame(failure, assertThrows(RuntimeException.class,
                () -> useCase.execute(command(system.id()))));

        assertEquals(1, assessmentRepository.createCount);
    }

    @Test
    void command_defensively_copies_findings_collection() {
        List<RecordRiskFindingCommand> findings = new ArrayList<>(findings());
        RecordRiskAssessmentCommand command = new RecordRiskAssessmentCommand(
                AISystemId.generate(), "Purpose", "Deployment", findings);

        findings.clear();

        assertEquals(3, command.findings().size());
        assertThrows(UnsupportedOperationException.class, () -> command.findings().add(
                new RecordRiskFindingCommand("New risk", Likelihood.HIGH, ImpactMagnitude.HIGH)));
    }

    @Test
    void command_requires_system_id() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new RecordRiskAssessmentCommand(null, "Purpose", "Deployment", findings()));

        assertEquals("System id is required", exception.getMessage());
    }

    private RecordRiskAssessmentCommand command(AISystemId systemId) {
        return new RecordRiskAssessmentCommand(
                systemId,
                "  Governance approval  ",
                "  Public sector deployment  ",
                findings()
        );
    }

    private List<RecordRiskFindingCommand> findings() {
        return List.of(
                new RecordRiskFindingCommand("  Bias risk  ", Likelihood.MEDIUM, ImpactMagnitude.HIGH),
                new RecordRiskFindingCommand("Privacy risk", Likelihood.LOW, ImpactMagnitude.MEDIUM),
                new RecordRiskFindingCommand("  Bias risk  ", Likelihood.MEDIUM, ImpactMagnitude.HIGH)
        );
    }

    private AISystem registeredSystem() {
        return AISystem.rehydrate(
                AISystemId.generate(),
                OrganizationId.generate(),
                AIInitiativeId.generate(),
                "AI System",
                "System description",
                AISystemStatus.REGISTERED,
                SYSTEM_CREATED_AT
        );
    }

    private static final class InMemoryAISystemRepository implements AISystemRepository {
        private final java.util.Map<AISystemId, AISystem> systems = new java.util.HashMap<>();
        private AISystemId lastFindId;
        private int findCount;

        InMemoryAISystemRepository(AISystem... systems) {
            for (AISystem system : systems) {
                this.systems.put(system.id(), system);
            }
        }

        @Override
        public AISystem create(AISystem system) {
            throw new AssertionError("Recording risk must not create an AI system");
        }

        @Override
        public Optional<AISystem> findById(AISystemId id) {
            findCount++;
            lastFindId = id;
            return Optional.ofNullable(systems.get(id));
        }
    }

    private static final class InMemoryRiskAssessmentRepository implements RiskAssessmentRepository {
        private RiskAssessment createdAssessment;
        private RuntimeException failure;
        private int createCount;

        @Override
        public RiskAssessment create(RiskAssessment assessment) {
            createCount++;
            createdAssessment = assessment;
            if (failure != null) {
                throw failure;
            }
            return assessment;
        }

        void reset() {
            createdAssessment = null;
            failure = null;
            createCount = 0;
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
