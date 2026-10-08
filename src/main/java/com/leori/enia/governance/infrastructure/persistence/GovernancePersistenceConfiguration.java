package com.leori.enia.governance.infrastructure.persistence;

import com.leori.enia.governance.application.port.AISystemGovernanceGapsRepository;
import com.leori.enia.governance.application.port.AISystemGovernanceSummaryRepository;
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

        RuleBasedTransactionAttribute attribute = requiredAttribute(false);
        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("create", attribute);
        source.addTransactionalMethod("findById", attribute);

        return transactional(adapter, AISystemRepository.class, transactionManager, source);
    }

    @Bean
    AISystemGovernanceSummaryRepository aiSystemGovernanceSummaryRepository(
            EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager
    ) {
        var adapter = new JpaAISystemGovernanceSummaryRepositoryAdapter(
                SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));

        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("summarize", requiredAttribute(true));

        return transactional(adapter, AISystemGovernanceSummaryRepository.class, transactionManager, source);
    }

    @Bean
    AISystemGovernanceGapsRepository aiSystemGovernanceGapsRepository(
            EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager
    ) {
        var adapter = new JpaAISystemGovernanceGapsRepositoryAdapter(
                SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));

        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("findByAISystemId", requiredAttribute(true));

        return transactional(adapter, AISystemGovernanceGapsRepository.class, transactionManager, source);
    }

    private RuleBasedTransactionAttribute requiredAttribute(boolean readOnly) {
        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        attribute.setReadOnly(readOnly);
        attribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));
        return attribute;
    }

    private <T> T transactional(
            Object target,
            Class<T> repositoryType,
            PlatformTransactionManager transactionManager,
            NameMatchTransactionAttributeSource source
    ) {
        TransactionInterceptor transactions = new TransactionInterceptor();
        transactions.setTransactionManager(transactionManager);
        transactions.setTransactionAttributeSource(source);

        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(repositoryType);
        factory.addAdvice(transactions);
        return repositoryType.cast(factory.getProxy());
    }
}
