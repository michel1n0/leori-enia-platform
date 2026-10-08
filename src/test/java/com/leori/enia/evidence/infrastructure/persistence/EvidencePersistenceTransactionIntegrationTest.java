package com.leori.enia.evidence.infrastructure.persistence;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.evidence.domain.EvidenceRepository;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.risk.domain.ControlImplementationId;
import com.leori.enia.risk.infrastructure.persistence.RiskPersistenceConfiguration;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(EvidencePersistenceTransactionIntegrationTest.PersistenceConfiguration.class)
class EvidencePersistenceTransactionIntegrationTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T12:30:45.123456Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private EvidenceRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void evidence_repository_is_transactionally_proxied() throws Exception {
        assertTrue(repository instanceof Advised);
        assertNotNull(transactionInterceptor(repository));
    }

    @Test
    void evidence_create_is_required_writable_and_rolls_back_on_throwable() throws Exception {
        TransactionInterceptor interceptor = transactionInterceptor(repository);
        Method create = EvidenceRepository.class.getMethod("create", Evidence.class);

        TransactionAttribute attribute = interceptor.getTransactionAttributeSource()
                .getTransactionAttribute(create, repository.getClass());

        assertNotNull(attribute);
        assertTrue(attribute.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED);
        assertFalse(attribute.isReadOnly());
        assertTrue(attribute.rollbackOn(new Throwable()));
    }

    @Test
    void evidence_find_by_id_is_required_read_only_and_rolls_back_on_throwable() throws Exception {
        TransactionInterceptor interceptor = transactionInterceptor(repository);
        Method findById = EvidenceRepository.class.getMethod("findById", EvidenceId.class);

        TransactionAttribute attribute = interceptor.getTransactionAttributeSource()
                .getTransactionAttribute(findById, repository.getClass());

        assertNotNull(attribute);
        assertTrue(attribute.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED);
        assertTrue(attribute.isReadOnly());
        assertTrue(attribute.rollbackOn(new Throwable()));
    }

    @Test
    void create_rolls_back_on_persistence_failure() {
        Evidence input = evidence(ControlImplementationId.generate());

        assertThrows(PersistenceException.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(transaction -> repository.create(input)));

        assertEquals(0, jdbc.queryForObject("select count(*) from evidence", Integer.class));
        assertEquals(1, input.domainEvents().size());
    }

    private TransactionInterceptor transactionInterceptor(Object bean) throws Exception {
        return Arrays.stream(((Advised) bean).getAdvisors())
                .map(Advisor::getAdvice)
                .filter(TransactionInterceptor.class::isInstance)
                .map(TransactionInterceptor.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private Evidence evidence(ControlImplementationId implementationId) {
        return Evidence.builder()
                .id(EvidenceId.generate())
                .controlImplementationId(implementationId)
                .description("Signed approval minutes")
                .reference("evidence-vault:item-123")
                .recordedAt(RECORDED_AT)
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({EvidencePersistenceConfiguration.class, RiskPersistenceConfiguration.class,
            GovernancePersistenceConfiguration.class, AIInitiativePersistenceConfiguration.class})
    static class PersistenceConfiguration {
    }
}
