package com.leori.enia.risk.infrastructure.configuration;

import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.risk.application.RecordRiskAssessmentUseCase;
import com.leori.enia.risk.application.port.RiskAssessmentRepository;
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
 * Import into a context providing governance persistence, JPA, a data source,
 * and its transaction manager. Callers must use these beans; directly
 * constructed use cases are unwrapped.
 */
@Configuration(proxyBeanMethods = false)
@Import({RiskPersistenceConfiguration.class, GovernancePersistenceConfiguration.class})
public class RiskApplicationConfiguration {

    private final TransactionInterceptor transactions;

    public RiskApplicationConfiguration(PlatformTransactionManager transactionManager) {
        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        attribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));

        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("execute", attribute);

        transactions = new TransactionInterceptor();
        transactions.setTransactionManager(transactionManager);
        transactions.setTransactionAttributeSource(source);
    }

    @Bean(defaultCandidate = false)
    @ConditionalOnMissingBean(name = "riskClock")
    Clock riskClock() {
        return Clock.systemUTC();
    }

    @Bean
    RecordRiskAssessmentUseCase recordRiskAssessmentUseCase(
            AISystemRepository systemRepository,
            RiskAssessmentRepository assessmentRepository,
            @Qualifier("riskClock") Clock clock
    ) {
        return transactional(
                new RecordRiskAssessmentUseCase(systemRepository, assessmentRepository, clock),
                RecordRiskAssessmentUseCase.class
        );
    }

    private <T> T transactional(T target, Class<T> useCaseType) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(transactions);
        return useCaseType.cast(factory.getProxy());
    }
}
