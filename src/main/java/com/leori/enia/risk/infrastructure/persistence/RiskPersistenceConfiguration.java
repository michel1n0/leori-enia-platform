package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.application.port.RiskAssessmentRepository;
import com.leori.enia.risk.domain.ControlRepository;
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
@EntityScan(basePackageClasses = {RiskAssessmentJpaEntity.class, ControlJpaEntity.class})
@Import({RiskAssessmentPersistenceMapper.class, ControlPersistenceMapper.class})
public class RiskPersistenceConfiguration {

    @Bean
    RiskAssessmentRepository riskAssessmentRepository(
            EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager,
            RiskAssessmentPersistenceMapper mapper
    ) {
        var adapter = new JpaRiskAssessmentRepositoryAdapter(
                SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory), mapper);

        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        attribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));
        RuleBasedTransactionAttribute readOnlyAttribute = new RuleBasedTransactionAttribute();
        readOnlyAttribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        readOnlyAttribute.setReadOnly(true);
        readOnlyAttribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));

        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("create", attribute);
        source.addTransactionalMethod("findById", readOnlyAttribute);

        TransactionInterceptor transactions = new TransactionInterceptor();
        transactions.setTransactionManager(transactionManager);
        transactions.setTransactionAttributeSource(source);

        ProxyFactory factory = new ProxyFactory(adapter);
        factory.setInterfaces(RiskAssessmentRepository.class);
        factory.addAdvice(transactions);
        return (RiskAssessmentRepository) factory.getProxy();
    }

    @Bean
    ControlRepository controlRepository(
            EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager,
            ControlPersistenceMapper mapper
    ) {
        var adapter = new JpaControlRepositoryAdapter(
                SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory), mapper);

        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        attribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));
        RuleBasedTransactionAttribute readOnlyAttribute = new RuleBasedTransactionAttribute();
        readOnlyAttribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        readOnlyAttribute.setReadOnly(true);
        readOnlyAttribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));

        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("create", attribute);
        source.addTransactionalMethod("findById", readOnlyAttribute);

        TransactionInterceptor transactions = new TransactionInterceptor();
        transactions.setTransactionManager(transactionManager);
        transactions.setTransactionAttributeSource(source);

        ProxyFactory factory = new ProxyFactory(adapter);
        factory.setInterfaces(ControlRepository.class);
        factory.addAdvice(transactions);
        return (ControlRepository) factory.getProxy();
    }
}
