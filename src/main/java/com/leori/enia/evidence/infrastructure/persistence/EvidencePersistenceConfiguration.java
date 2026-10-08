package com.leori.enia.evidence.infrastructure.persistence;

import com.leori.enia.evidence.domain.EvidenceRepository;
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
@EntityScan(basePackageClasses = EvidenceJpaEntity.class)
@Import(EvidencePersistenceMapper.class)
public class EvidencePersistenceConfiguration {

    @Bean
    EvidenceRepository evidenceRepository(
            EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager,
            EvidencePersistenceMapper mapper
    ) {
        var adapter = new JpaEvidenceRepositoryAdapter(
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
        factory.setInterfaces(EvidenceRepository.class);
        factory.addAdvice(transactions);
        return (EvidenceRepository) factory.getProxy();
    }
}
