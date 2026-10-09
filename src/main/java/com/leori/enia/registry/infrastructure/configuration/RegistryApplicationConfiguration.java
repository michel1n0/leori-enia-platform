package com.leori.enia.registry.infrastructure.configuration;

import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.infrastructure.persistence.GovernancePersistenceConfiguration;
import com.leori.enia.registry.application.AssociateDatasetWithAISystemUseCase;
import com.leori.enia.registry.application.GetAIModelUseCase;
import com.leori.enia.registry.application.GetDatasetUseCase;
import com.leori.enia.registry.application.GetDatasetsByAISystemUseCase;
import com.leori.enia.registry.application.RegisterAIModelUseCase;
import com.leori.enia.registry.application.RegisterDatasetUseCase;
import com.leori.enia.registry.application.port.AIModelRepository;
import com.leori.enia.registry.application.port.AISystemDatasetRepository;
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
    private final TransactionInterceptor readOnlyTransactions;

    public RegistryApplicationConfiguration(PlatformTransactionManager transactionManager) {
        RuleBasedTransactionAttribute writableAttribute = new RuleBasedTransactionAttribute();
        writableAttribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        writableAttribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));

        NameMatchTransactionAttributeSource writableSource = new NameMatchTransactionAttributeSource();
        writableSource.addTransactionalMethod("execute", writableAttribute);

        transactions = new TransactionInterceptor();
        transactions.setTransactionManager(transactionManager);
        transactions.setTransactionAttributeSource(writableSource);

        RuleBasedTransactionAttribute readOnlyAttribute = new RuleBasedTransactionAttribute();
        readOnlyAttribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        readOnlyAttribute.setReadOnly(true);
        readOnlyAttribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));

        NameMatchTransactionAttributeSource readOnlySource = new NameMatchTransactionAttributeSource();
        readOnlySource.addTransactionalMethod("execute", readOnlyAttribute);

        readOnlyTransactions = new TransactionInterceptor();
        readOnlyTransactions.setTransactionManager(transactionManager);
        readOnlyTransactions.setTransactionAttributeSource(readOnlySource);
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

    @Bean
    GetDatasetUseCase getDatasetUseCase(DatasetRepository datasetRepository) {
        return transactional(
                new GetDatasetUseCase(datasetRepository),
                GetDatasetUseCase.class
        );
    }

    @Bean
    AssociateDatasetWithAISystemUseCase associateDatasetWithAISystemUseCase(
            AISystemRepository systemRepository,
            DatasetRepository datasetRepository,
            AISystemDatasetRepository associationRepository,
            @Qualifier("registryClock") Clock clock
    ) {
        return transactional(
                new AssociateDatasetWithAISystemUseCase(
                        systemRepository,
                        datasetRepository,
                        associationRepository,
                        clock
                ),
                AssociateDatasetWithAISystemUseCase.class
        );
    }

    @Bean
    GetDatasetsByAISystemUseCase getDatasetsByAISystemUseCase(
            AISystemRepository systemRepository,
            AISystemDatasetRepository associationRepository
    ) {
        return transactionalReadOnly(
                new GetDatasetsByAISystemUseCase(systemRepository, associationRepository),
                GetDatasetsByAISystemUseCase.class
        );
    }

    private <T> T transactional(T target, Class<T> useCaseType) {
        return proxied(target, useCaseType, transactions);
    }

    private <T> T transactionalReadOnly(T target, Class<T> useCaseType) {
        return proxied(target, useCaseType, readOnlyTransactions);
    }

    private <T> T proxied(T target, Class<T> useCaseType, TransactionInterceptor transactionInterceptor) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(transactionInterceptor);
        return useCaseType.cast(factory.getProxy());
    }
}
