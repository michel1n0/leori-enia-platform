package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringJUnitConfig(RiskPersistenceTransactionIntegrationTest.PersistenceConfiguration.class)
class RiskPersistenceTransactionIntegrationTest {

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
    private ControlImplementationRepository repository;

    @Test
    void control_implementation_create_is_required_writable_and_rolls_back_on_throwable() throws Exception {
        TransactionInterceptor interceptor = transactionInterceptor(repository);
        Method create = ControlImplementationRepository.class.getMethod("create", com.leori.enia.risk.domain.ControlImplementation.class);

        TransactionAttribute attribute = interceptor.getTransactionAttributeSource()
                .getTransactionAttribute(create, repository.getClass());

        assertNotNull(attribute);
        assertTrue(attribute.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED);
        assertFalse(attribute.isReadOnly());
        assertTrue(attribute.rollbackOn(new Throwable()));
    }

    @Test
    void control_implementation_find_by_id_is_required_read_only_and_rolls_back_on_throwable() throws Exception {
        TransactionInterceptor interceptor = transactionInterceptor(repository);
        Method findById = ControlImplementationRepository.class.getMethod(
                "findById",
                com.leori.enia.risk.domain.ControlImplementationId.class
        );

        TransactionAttribute attribute = interceptor.getTransactionAttributeSource()
                .getTransactionAttribute(findById, repository.getClass());

        assertNotNull(attribute);
        assertTrue(attribute.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED);
        assertTrue(attribute.isReadOnly());
        assertTrue(attribute.rollbackOn(new Throwable()));
    }

    private TransactionInterceptor transactionInterceptor(Object bean) throws Exception {
        return Arrays.stream(((Advised) bean).getAdvisors())
                .map(Advisor::getAdvice)
                .filter(TransactionInterceptor.class::isInstance)
                .map(TransactionInterceptor.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RiskPersistenceConfiguration.class, GovernancePersistenceConfiguration.class,
            AIInitiativePersistenceConfiguration.class})
    static class PersistenceConfiguration {
    }
}
