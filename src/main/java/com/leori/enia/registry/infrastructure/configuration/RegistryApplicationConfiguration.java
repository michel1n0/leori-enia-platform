package com.leori.enia.registry.infrastructure.configuration;

import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.registry.application.GetAIModelUseCase;
import com.leori.enia.registry.application.RegisterAIModelUseCase;
import com.leori.enia.registry.application.RegisterDatasetUseCase;
import com.leori.enia.registry.application.port.AIModelRepository;
import com.leori.enia.registry.application.port.DatasetRepository;
import com.leori.enia.registry.infrastructure.persistence.RegistryPersistenceConfiguration;
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
@Import({RegistryPersistenceConfiguration.class, GovernancePersistenceConfiguration.class})
public class RegistryApplicationConfiguration {

    private final TransactionInterceptor transactions;

    public RegistryApplicationConfiguration(PlatformTransactionManager transactionManager) {
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
    @ConditionalOnMissingBean(name = "registryClock")
    Clock registryClock() {
        return Clock.systemUTC();
    }

    @Bean
    RegisterAIModelUseCase registerAIModelUseCase(
            AISystemRepository systemRepository,
            AIModelRepository modelRepository,
            @Qualifier("registryClock") Clock clock
    ) {
        return transactional(
                new RegisterAIModelUseCase(systemRepository, modelRepository, clock),
                RegisterAIModelUseCase.class
        );
    }

    @Bean
    RegisterDatasetUseCase registerDatasetUseCase(
            DatasetRepository datasetRepository,
            @Qualifier("registryClock") Clock clock
    ) {
        return transactional(
                new RegisterDatasetUseCase(datasetRepository, clock),
                RegisterDatasetUseCase.class
        );
    }

    @Bean
    GetAIModelUseCase getAIModelUseCase(AIModelRepository modelRepository) {
        return transactional(
                new GetAIModelUseCase(modelRepository),
                GetAIModelUseCase.class
        );
    }

    private <T> T transactional(T target, Class<T> useCaseType) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(transactions);
        return useCaseType.cast(factory.getProxy());
    }
}
