package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeInvalidTransitionException;
import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.exception.AIInitiativeRevisionMismatchException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.application.port.SavedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.initiative.domain.event.AIInitiativeRejected;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RejectAIInitiativeUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-15T14:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-16T13:00:00Z");
    private static final Instant ASSESSED_AT = Instant.parse("2026-09-16T14:00:00Z");
    private static final Instant REJECTED_AT = Instant.parse("2026-09-16T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(REJECTED_AT, ZoneOffset.UTC);

    private final AIInitiativeRepository repository = mock(AIInitiativeRepository.class);

    @ParameterizedTest
    @EnumSource(value = RiskLevel.class, names = {"LOW", "MEDIUM", "HIGH"})
    void should_reject_preserving_risk_events_and_the_actual_saved_revision(RiskLevel risk) {
        AIInitiative initiative = createRiskAssessedInitiative(risk);
        var eventsBefore = initiative.domainEvents();
        LoadedAIInitiative loaded = arrangeLoaded(initiative);
        when(repository.save(same(loaded))).thenReturn(new SavedAIInitiative(initiative, 42));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(REJECTED_AT);

        VersionedAIInitiativeDetails result = new RejectAIInitiativeUseCase(repository, clock).execute(
                new RejectAIInitiativeCommand(initiative.id(), "  Riesgo residual no aceptable  ",
                        new ExpectedRevision(initiative.id(), 7)));

        assertEquals(InitiativeStatus.REJECTED, initiative.status());
        assertEquals(risk, initiative.preliminaryRisk());
        assertEquals("Riesgo residual no aceptable", initiative.rejectionReason());
        assertEquals(AIInitiativeDetails.from(initiative), result.details());
        assertEquals(42, result.revision());
        assertNotEquals(8, result.revision());
        var order = inOrder(repository, clock);
        order.verify(repository).findById(initiative.id());
        order.verify(clock).instant();
        order.verify(repository).save(same(loaded));
        verifyNoMoreInteractions(repository, clock);
        assertEquals(eventsBefore.size() + 1, initiative.domainEvents().size());
        assertEquals(eventsBefore, initiative.domainEvents().subList(0, eventsBefore.size()));
        AIInitiativeRejected event = assertInstanceOf(AIInitiativeRejected.class,
                initiative.domainEvents().getLast());
        assertEquals(initiative.id(), event.initiativeId());
        assertEquals(initiative.rejectionReason(), event.reason());
        assertEquals(REJECTED_AT, event.occurredAt());
    }

    @Test
    void should_return_details_from_the_repository_saved_aggregate() {
        AIInitiative initiative = createRiskAssessedInitiative(RiskLevel.HIGH);
        LoadedAIInitiative loaded = arrangeLoaded(initiative);
        AIInitiative saved = AIInitiative.rehydrate(initiative.id(), initiative.organizationId(),
                initiative.name(), initiative.description(), InitiativeStatus.REJECTED, RiskLevel.HIGH,
                initiative.usesPersonalData(), initiative.impactsRights(), CREATED_AT, "Stored reason");
        when(repository.save(same(loaded))).thenReturn(new SavedAIInitiative(saved, 42));

        var result = new RejectAIInitiativeUseCase(repository, CLOCK).execute(command(initiative, "Reason"));

        assertEquals(AIInitiativeDetails.from(saved), result.details());
        assertEquals(42, result.revision());
        verify(repository).findById(initiative.id());
        verify(repository).save(same(loaded));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void should_fail_when_initiative_does_not_exist() {
        AIInitiativeId missingId = AIInitiativeId.generate();
        when(repository.findById(missingId)).thenReturn(Optional.empty());
        Clock clock = mock(Clock.class);

        var exception = assertThrows(AIInitiativeNotFoundException.class,
                () -> new RejectAIInitiativeUseCase(repository, clock).execute(
                        new RejectAIInitiativeCommand(missingId, "Reason", new ExpectedRevision(missingId, 7))));

        assertEquals("AI initiative not found: " + missingId, exception.getMessage());
        verify(repository).findById(missingId);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(clock);
    }

    @Test
    void should_reject_null_command_before_lookup() {
        assertInvalidCommand(null, "Reject AI initiative command is required");
    }

    @Test
    void should_reject_null_initiative_id_before_expected_revision_and_lookup() {
        assertInvalidCommand(new RejectAIInitiativeCommand(null, "Reason", null), "AI initiative id is required");
    }

    @Test
    void should_reject_null_expected_revision_before_lookup() {
        assertInvalidCommand(new RejectAIInitiativeCommand(AIInitiativeId.generate(), "Reason", null),
                "Expected revision is required");
    }

    private void assertInvalidCommand(RejectAIInitiativeCommand command, String message) {
        Clock clock = mock(Clock.class);
        var exception = assertThrows(NullPointerException.class,
                () -> new RejectAIInitiativeUseCase(repository, clock).execute(command));
        assertEquals(message, exception.getMessage());
        verifyNoInteractions(repository, clock);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stale_or_foreign_revision_does_not_access_clock_mutate_reason_or_events(boolean foreign) {
        AIInitiative initiative = createRiskAssessedInitiative(RiskLevel.HIGH);
        var before = AIInitiativeDetails.from(initiative);
        var eventsBefore = initiative.domainEvents();
        arrangeLoaded(initiative);
        Clock clock = mock(Clock.class);
        ExpectedRevision expected = foreign
                ? new ExpectedRevision(AIInitiativeId.generate(), 7)
                : new ExpectedRevision(initiative.id(), 6);

        assertThrows(AIInitiativeRevisionMismatchException.class,
                () -> new RejectAIInitiativeUseCase(repository, clock).execute(
                        new RejectAIInitiativeCommand(initiative.id(), "Reason", expected)));

        assertEquals(before, AIInitiativeDetails.from(initiative));
        assertNull(initiative.rejectionReason());
        assertEquals(eventsBefore, initiative.domainEvents());
        verify(repository).findById(initiative.id());
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(clock);
    }

    @Test
    void should_translate_only_invalid_domain_lifecycle_without_mutation() {
        AIInitiative initiative = createInitiative();
        var before = AIInitiativeDetails.from(initiative);
        var eventsBefore = initiative.domainEvents();
        arrangeLoaded(initiative);

        var exception = assertThrows(AIInitiativeInvalidTransitionException.class,
                () -> new RejectAIInitiativeUseCase(repository, CLOCK).execute(command(initiative, "Reason")));

        assertEquals("Expected initiative status RISK_ASSESSED but was DRAFT", exception.getMessage());
        assertEquals(IllegalStateException.class, exception.getCause().getClass());
        assertEquals(exception.getMessage(), exception.getCause().getMessage());
        assertEquals(before, AIInitiativeDetails.from(initiative));
        assertEquals(eventsBefore, initiative.domainEvents());
        verify(repository).findById(initiative.id());
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "\u0000", "\u2003", "\u0000 \u2003\t"})
    void should_delegate_invalid_reason_to_domain_without_translation_or_mutation(String reason) {
        AIInitiative initiative = createRiskAssessedInitiative(RiskLevel.HIGH);
        var before = AIInitiativeDetails.from(initiative);
        var eventsBefore = initiative.domainEvents();
        arrangeLoaded(initiative);

        var exception = assertThrows(IllegalArgumentException.class,
                () -> new RejectAIInitiativeUseCase(repository, CLOCK).execute(command(initiative, reason)));

        assertEquals("Rejection reason is required", exception.getMessage());
        assertEquals(before, AIInitiativeDetails.from(initiative));
        assertEquals(eventsBefore, initiative.domainEvents());
        verify(repository).findById(initiative.id());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void clock_failure_is_not_translated_as_a_lifecycle_failure() {
        AIInitiative initiative = createRiskAssessedInitiative(RiskLevel.HIGH);
        var before = AIInitiativeDetails.from(initiative);
        var eventsBefore = initiative.domainEvents();
        arrangeLoaded(initiative);
        Clock clock = mock(Clock.class);
        var failure = new IllegalStateException("Clock unavailable");
        when(clock.instant()).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new RejectAIInitiativeUseCase(repository, clock).execute(command(initiative, "Reason"))));

        assertEquals(before, AIInitiativeDetails.from(initiative));
        assertEquals(eventsBefore, initiative.domainEvents());
        verify(repository).findById(initiative.id());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void save_failure_is_not_translated_as_a_lifecycle_failure() {
        AIInitiative initiative = createRiskAssessedInitiative(RiskLevel.HIGH);
        LoadedAIInitiative loaded = arrangeLoaded(initiative);
        var failure = new IllegalStateException("Save unavailable");
        when(repository.save(same(loaded))).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new RejectAIInitiativeUseCase(repository, CLOCK).execute(command(initiative, "Reason"))));

        verify(repository).findById(initiative.id());
        verify(repository).save(same(loaded));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void load_failure_is_not_translated_or_followed_by_clock_access() {
        AIInitiative initiative = createInitiative();
        var failure = new IllegalStateException("Load unavailable");
        when(repository.findById(initiative.id())).thenThrow(failure);
        Clock clock = mock(Clock.class);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new RejectAIInitiativeUseCase(repository, clock).execute(command(initiative, "Reason"))));

        verify(repository).findById(initiative.id());
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(clock);
    }

    private LoadedAIInitiative arrangeLoaded(AIInitiative initiative) {
        var loaded = new LoadedAIInitiative(initiative, 7);
        when(repository.findById(initiative.id())).thenReturn(Optional.of(loaded));
        return loaded;
    }

    private RejectAIInitiativeCommand command(AIInitiative initiative, String reason) {
        return new RejectAIInitiativeCommand(initiative.id(), reason, new ExpectedRevision(initiative.id(), 7));
    }

    private AIInitiative createRiskAssessedInitiative(RiskLevel risk) {
        AIInitiative initiative = createInitiative();
        initiative.submit(SUBMITTED_AT);
        initiative.startAssessment();
        initiative.assessRisk(risk, ASSESSED_AT);
        return initiative;
    }

    private AIInitiative createInitiative() {
        return AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("Detección de anomalías de asistencia")
                .description("Detectar patrones anómalos de asistencia laboral")
                .usesPersonalData(true)
                .impactsRights(true)
                .createdAt(CREATED_AT)
                .build();
    }
}
