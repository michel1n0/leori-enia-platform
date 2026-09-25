package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.port.AISystemRepository;
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
@EntityScan(basePackageClasses = AISystemJpaEntity.class)
@Import(AISystemPersistenceMapper.class)
public class GovernancePersistenceConfiguration {

    @Bean
    AISystemRepository aiSystemRepository(
            EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager,
            AISystemPersistenceMapper mapper
    ) {
        var adapter = new JpaAISystemRepositoryAdapter(
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
        factory.setInterfaces(AISystemRepository.class);
        factory.addAdvice(transactions);
        return (AISystemRepository) factory.getProxy();
    }
}
