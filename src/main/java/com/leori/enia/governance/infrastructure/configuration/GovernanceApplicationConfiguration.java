package com.leori.enia.governance.infrastructure.configuration;

import com.leori.enia.governance.application.GetAISystemGovernanceGapsUseCase;
import com.leori.enia.governance.application.GetAISystemGovernanceSummaryUseCase;
import com.leori.enia.governance.application.GetAISystemUseCase;
import com.leori.enia.governance.application.RegisterAISystemUseCase;
import com.leori.enia.governance.application.port.AISystemGovernanceGapsRepository;
import com.leori.enia.governance.application.port.AISystemGovernanceSummaryRepository;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
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
 * Import into a context providing initiative persistence, JPA, a data source,
 * and its transaction manager. Callers must use these beans: directly
 * constructed use cases are unwrapped.
 */
@Configuration(proxyBeanMethods = false)
@Import(GovernancePersistenceConfiguration.class)
public class GovernanceApplicationConfiguration {

    private final TransactionInterceptor transactions;
    private final TransactionInterceptor readOnlyTransactions;

    public GovernanceApplicationConfiguration(PlatformTransactionManager transactionManager) {
        transactions = transactionInterceptor(transactionManager, false);
        readOnlyTransactions = transactionInterceptor(transactionManager, true);
    }

    @Bean(defaultCandidate = false)
    @ConditionalOnMissingBean(name = "governanceClock")
    Clock governanceClock() {
        return Clock.systemUTC();
    }

    @Bean
    RegisterAISystemUseCase registerAISystemUseCase(
            AIInitiativeRepository initiativeRepository,
            AISystemRepository systemRepository,
            @Qualifier("governanceClock") Clock clock
    ) {
        return transactional(
                new RegisterAISystemUseCase(initiativeRepository, systemRepository, clock),
                RegisterAISystemUseCase.class,
                transactions
        );
    }

    @Bean
    GetAISystemUseCase getAISystemUseCase(AISystemRepository systemRepository) {
        return transactional(
                new GetAISystemUseCase(systemRepository),
                GetAISystemUseCase.class,
                transactions
        );
    }

    @Bean
    GetAISystemGovernanceSummaryUseCase getAISystemGovernanceSummaryUseCase(
            AISystemRepository systemRepository,
            AISystemGovernanceSummaryRepository summaryRepository
    ) {
        return transactional(
                new GetAISystemGovernanceSummaryUseCase(systemRepository, summaryRepository),
                GetAISystemGovernanceSummaryUseCase.class,
                readOnlyTransactions
        );
    }

    @Bean
    GetAISystemGovernanceGapsUseCase getAISystemGovernanceGapsUseCase(
            AISystemRepository systemRepository,
            AISystemGovernanceGapsRepository gapsRepository
    ) {
        return transactional(
                new GetAISystemGovernanceGapsUseCase(systemRepository, gapsRepository),
                GetAISystemGovernanceGapsUseCase.class,
                readOnlyTransactions
        );
    }

    private TransactionInterceptor transactionInterceptor(
            PlatformTransactionManager transactionManager,
            boolean readOnly
    ) {
        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        attribute.setReadOnly(readOnly);
        attribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));

        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("execute", attribute);

        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(source);
        return interceptor;
    }

    private <T> T transactional(T target, Class<T> useCaseType, TransactionInterceptor transactionInterceptor) {
        // Class proxies preserve the existing concrete use-case contracts.
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(transactionInterceptor);
        return useCaseType.cast(factory.getProxy());
    }
}
