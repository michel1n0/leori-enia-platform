package com.leori.enia.evidence.infrastructure.configuration;

import com.leori.enia.evidence.application.GetEvidenceByControlImplementationUseCase;
import com.leori.enia.evidence.application.GetEvidenceUseCase;
import com.leori.enia.evidence.application.RecordEvidenceUseCase;
import com.leori.enia.evidence.domain.EvidenceRepository;
import com.leori.enia.evidence.infrastructure.persistence.EvidencePersistenceConfiguration;
import com.leori.enia.risk.domain.ControlImplementationRepository;
import com.leori.enia.risk.infrastructure.persistence.RiskPersistenceConfiguration;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Clock;
import java.util.List;

/**
 * Import into a context providing risk and evidence persistence, JPA, a data source,
 * and its transaction manager. Callers must use these beans; directly
 * constructed use cases are unwrapped.
 */
@Configuration(proxyBeanMethods = false)
@Import({EvidencePersistenceConfiguration.class, RiskPersistenceConfiguration.class})
public class EvidenceApplicationConfiguration {

    private final TransactionInterceptor transactions;
    private final TransactionInterceptor readOnlyTransactions;

    public EvidenceApplicationConfiguration(PlatformTransactionManager transactionManager) {
        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        attribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));

        RuleBasedTransactionAttribute readOnlyAttribute = new RuleBasedTransactionAttribute();
        readOnlyAttribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        readOnlyAttribute.setReadOnly(true);
        readOnlyAttribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));

        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("execute", attribute);

        NameMatchTransactionAttributeSource readOnlySource = new NameMatchTransactionAttributeSource();
        readOnlySource.addTransactionalMethod("execute", readOnlyAttribute);

        transactions = new TransactionInterceptor();
        transactions.setTransactionManager(transactionManager);
        transactions.setTransactionAttributeSource(source);

        readOnlyTransactions = new TransactionInterceptor();
        readOnlyTransactions.setTransactionManager(transactionManager);
        readOnlyTransactions.setTransactionAttributeSource(readOnlySource);
    }

    @Bean(defaultCandidate = false)
    @ConditionalOnMissingBean(name = "evidenceClock")
    Clock evidenceClock() {
        return Clock.systemUTC();
    }

    @Bean
    RecordEvidenceUseCase recordEvidenceUseCase(
            EvidenceRepository evidenceRepository,
            ControlImplementationRepository controlImplementationRepository,
            @Qualifier("evidenceClock") Clock evidenceClock
    ) {
        return transactional(
                new RecordEvidenceUseCase(evidenceRepository, controlImplementationRepository, evidenceClock),
                RecordEvidenceUseCase.class
        );
    }

    @Bean
    GetEvidenceUseCase getEvidenceUseCase(EvidenceRepository evidenceRepository) {
        return readOnlyTransactional(
                new GetEvidenceUseCase(evidenceRepository),
                GetEvidenceUseCase.class
        );
    }

    @Bean
    GetEvidenceByControlImplementationUseCase getEvidenceByControlImplementationUseCase(
            ControlImplementationRepository controlImplementationRepository,
            EvidenceRepository evidenceRepository
    ) {
        return readOnlyTransactional(
                new GetEvidenceByControlImplementationUseCase(controlImplementationRepository, evidenceRepository),
                GetEvidenceByControlImplementationUseCase.class
        );
    }

    private <T> T transactional(T target, Class<T> useCaseType) {
        return proxied(target, useCaseType, transactions);
    }

    private <T> T readOnlyTransactional(T target, Class<T> useCaseType) {
        return proxied(target, useCaseType, readOnlyTransactions);
    }

    private <T> T proxied(T target, Class<T> useCaseType, TransactionInterceptor interceptor) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return useCaseType.cast(factory.getProxy());
    }
}
