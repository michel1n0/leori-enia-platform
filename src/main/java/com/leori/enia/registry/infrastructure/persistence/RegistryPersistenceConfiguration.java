package com.leori.enia.registry.infrastructure.persistence;

import com.leori.enia.registry.application.port.AIModelRepository;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.List;

/** Uses the existing JPA factory and transaction manager; callers must use the repository bean. */
@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = AIModelJpaEntity.class)
@Import(AIModelPersistenceMapper.class)
public class RegistryPersistenceConfiguration {

    @Bean
    AIModelRepository aiModelRepository(
            EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager,
            AIModelPersistenceMapper mapper
    ) {
        var adapter = new JpaAIModelRepositoryAdapter(
                SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory), mapper);

        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        attribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));
        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("create", attribute);

        TransactionInterceptor transactions = new TransactionInterceptor();
        transactions.setTransactionManager(transactionManager);
        transactions.setTransactionAttributeSource(source);

        ProxyFactory factory = new ProxyFactory(adapter);
        factory.setInterfaces(AIModelRepository.class);
        factory.addAdvice(transactions);
        return (AIModelRepository) factory.getProxy();
    }
}
