package com.leori.enia.initiative.infrastructure.persistence;

import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.infrastructure.configuration.AIInitiativeApplicationConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class PostgreSQLAIInitiativeRejectionReasonMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Test
    void v3_preserves_v2_rows_and_restores_legacy_rejection_without_inventing_a_reason() {
        String schema = "rejection_upgrade";
        Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).target("2").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword()));
        UUID organizationId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID draftId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID approvedId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        UUID rejectedId = UUID.fromString("20000000-0000-0000-0000-000000000003");
        UUID[] ids = {draftId, approvedId, rejectedId};
        String[] statuses = {"DRAFT", "APPROVED", "REJECTED"};
        String[] risks = {"NOT_ASSESSED", "HIGH", "MEDIUM"};
        for (int i = 0; i < ids.length; i++) {
            jdbc.update("""
                    insert into rejection_upgrade.ai_initiatives
                        (id, organization_id, name, description, status, preliminary_risk,
                         uses_personal_data, impacts_rights, created_at, version)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, ids[i], organizationId, "Historical " + statuses[i], "Existing description",
                    statuses[i], risks[i], true, false,
                    Timestamp.from(Instant.parse("2026-09-16T14:00:00.123456Z")), 7L + i);
        }
        var before = jdbc.queryForList("select * from rejection_upgrade.ai_initiatives order by id");

        Flyway migration = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas(schema).defaultSchema(schema).target("3").load();
        assertEquals(1, migration.migrate().migrationsExecuted);
        assertEquals("3", migration.info().current().getVersion().toString());
        migration.validate();

        var after = jdbc.queryForList("select * from rejection_upgrade.ai_initiatives order by id");
        assertEquals(3, after.size());
        for (int i = 0; i < before.size(); i++) {
            Map<String, Object> restored = new HashMap<>(after.get(i));
            assertTrue(restored.containsKey("rejection_reason"));
            assertNull(restored.remove("rejection_reason"));
            assertEquals(before.get(i), restored, "Every pre-existing column, including version, must survive");
        }
        Map<String, Object> column = jdbc.queryForMap("""
                select data_type, is_nullable, column_default
                from information_schema.columns
                where table_schema = 'rejection_upgrade' and table_name = 'ai_initiatives'
                  and column_name = 'rejection_reason'
                """);
        assertEquals("text", column.get("data_type"));
        assertEquals("YES", column.get("is_nullable"));
        assertNull(column.get("column_default"));

        // Start production JPA against the upgraded schema: ddl-auto validates it,
        // and the real adapter/mapper must accept the historical REJECTED row.
        String schemaUrl = POSTGRESQL.getJdbcUrl()
                + (POSTGRESQL.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        new ApplicationContextRunner().withUserConfiguration(PersistenceConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.url=" + schemaUrl,
                        "spring.datasource.username=" + POSTGRESQL.getUsername(),
                        "spring.datasource.password=" + POSTGRESQL.getPassword(),
                        "spring.flyway.enabled=false",
                        "spring.jpa.hibernate.ddl-auto=validate")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var repository = context.getBean(AIInitiativeRepository.class);
                    var legacy = repository.findById(new AIInitiativeId(rejectedId)).orElseThrow();
                    assertEquals(InitiativeStatus.REJECTED, legacy.initiative().status());
                    assertNull(legacy.initiative().rejectionReason());
                    assertEquals(9L, legacy.version());
                    assertTrue(legacy.initiative().domainEvents().isEmpty());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(AIInitiativeApplicationConfiguration.class)
    static class PersistenceConfiguration {
    }
}
