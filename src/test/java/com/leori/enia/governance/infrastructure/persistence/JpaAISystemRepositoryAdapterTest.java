package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.exception.AISystemAlreadyRegisteredException;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.JDBCException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;

import java.sql.SQLException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JpaAISystemRepositoryAdapterTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final JpaAISystemRepositoryAdapter adapter =
            new JpaAISystemRepositoryAdapter(entityManager, new AISystemPersistenceMapper());

    @Test
    void inserts_then_flushes_and_returns_same_aggregate_with_pending_event() {
        AISystem system = system();
        var events = system.domainEvents();

        assertSame(system, adapter.create(system));

        var calls = inOrder(entityManager);
        calls.verify(entityManager).persist(any(AISystemJpaEntity.class));
        calls.verify(entityManager).flush();
        calls.verifyNoMoreInteractions();
        assertEquals(1, events.size());
        assertEquals(events, system.domainEvents());
    }

    @Test
    void rejects_null_before_accessing_persistence() {
        assertThrows(NullPointerException.class, () -> adapter.create(null));
        verifyNoInteractions(entityManager);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void propagates_original_persist_or_flush_failure(boolean duringPersist) {
        PersistenceException failure = new PersistenceException("Storage unavailable");
        failAt(duringPersist, failure);

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(system())));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void translates_source_duplicate_at_persist_or_flush_using_metadata(boolean duringPersist) {
        AISystem system = system();
        var failure = new PersistenceException(postgres("23505", "uk_ai_systems_source_initiative", "ai_systems"));
        failAt(duringPersist, failure);

        var duplicate = assertThrows(AISystemAlreadyRegisteredException.class, () -> adapter.create(system));

        assertEquals(system.sourceInitiativeId(), duplicate.sourceInitiativeId());
        assertSame(failure, duplicate.getCause());
        assertEquals("AI system already registered for source initiative: " + system.sourceInitiativeId(),
                duplicate.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "23505, pk_ai_systems, ai_systems",
            "23505, unrelated_unique, ai_systems",
            "23505, uk_ai_systems_source_initiative, other_table",
            "23505, , ai_systems",
            "23505, uk_ai_systems_source_initiative, ",
            "23503, fk_ai_systems_source_initiative, ai_systems",
            "23502, uk_ai_systems_source_initiative, ai_systems",
            "23514, ck_ai_systems_status, ai_systems",
            "23514, uk_ai_systems_source_initiative, ai_systems"
    })
    void preserves_non_matching_integrity_failures(String state, String constraint, String table) {
        var failure = new PersistenceException(postgres(state, constraint, table));
        doThrow(failure).when(entityManager).flush();

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(system())));
    }

    @Test
    void does_not_classify_message_text_without_server_metadata() {
        var postgres = new PSQLException(
                "violates unique constraint \"uk_ai_systems_source_initiative\" on ai_systems",
                PSQLState.UNIQUE_VIOLATION);
        var failure = new PersistenceException(postgres);
        doThrow(failure).when(entityManager).flush();

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(system())));
    }

    @Test
    void traverses_wrapped_hibernate_and_next_sql_exception_links() {
        SQLException batch = new SQLException("Batch failed");
        batch.setNextException(postgres("23505", "uk_ai_systems_source_initiative", "ai_systems"));
        var failure = new PersistenceException(new IllegalStateException(new JDBCException("SQL failed", batch)));
        doThrow(failure).when(entityManager).flush();

        var duplicate = assertThrows(AISystemAlreadyRegisteredException.class, () -> adapter.create(system()));

        assertSame(failure, duplicate.getCause());
    }

    @Test
    void terminates_on_cyclic_exception_links_without_a_match() {
        SQLException first = new SQLException("First");
        SQLException second = new SQLException("Second");
        first.setNextException(second);
        second.initCause(first);
        var failure = new PersistenceException(first);
        doThrow(failure).when(entityManager).flush();

        assertSame(failure, assertThrows(PersistenceException.class, () -> adapter.create(system())));
    }

    private void failAt(boolean duringPersist, PersistenceException failure) {
        if (duringPersist) {
            doThrow(failure).when(entityManager).persist(any(AISystemJpaEntity.class));
        } else {
            doThrow(failure).when(entityManager).flush();
        }
    }

    private PSQLException postgres(String state, String constraint, String table) {
        // PostgreSQL protocol fields, deliberately unrelated to the human-readable message.
        String fields = "SERROR\0C" + state + "\0Mmensaje sin nombre de restricción\0";
        if (constraint != null) {
            fields += "n" + constraint + "\0";
        }
        if (table != null) {
            fields += "t" + table + "\0";
        }
        return new PSQLException(new ServerErrorMessage(fields + "\0"));
    }

    private AISystem system() {
        return AISystem.builder().id(AISystemId.generate()).organizationId(OrganizationId.generate())
                .sourceInitiativeId(AIInitiativeId.generate()).name("System").description("Description")
                .createdAt(Instant.parse("2026-09-25T14:00:00.123456Z")).build();
    }
}
