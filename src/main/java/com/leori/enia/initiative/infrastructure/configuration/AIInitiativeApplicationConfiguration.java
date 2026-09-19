package com.leori.enia.initiative.infrastructure.configuration;

import com.leori.enia.initiative.application.ApproveAIInitiativeUseCase;
import com.leori.enia.initiative.application.AssessRiskAIInitiativeUseCase;
import com.leori.enia.initiative.application.CreateAIInitiativeUseCase;
import com.leori.enia.initiative.application.RejectAIInitiativeUseCase;
import com.leori.enia.initiative.application.StartAssessmentAIInitiativeUseCase;
import com.leori.enia.initiative.application.SubmitAIInitiativeUseCase;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.infrastructure.persistence.AIInitiativePersistenceConfiguration;
import org.springframework.aop.framework.ProxyFactory;
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
 * Import into a context providing JPA, a data source, and its transaction manager.
 * Callers must use these beans: directly constructed use cases are unwrapped.
 * REQUIRED starts a transaction at execute, or joins the caller's transaction.
 */
@Configuration(proxyBeanMethods = false)
@Import(AIInitiativePersistenceConfiguration.class)
public class AIInitiativeApplicationConfiguration {

    private final TransactionInterceptor transactions;

    public AIInitiativeApplicationConfiguration(PlatformTransactionManager transactionManager) {
        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        attribute.setRollbackRules(List.of(new RollbackRuleAttribute(Throwable.class)));

        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("execute", attribute);

        transactions = new TransactionInterceptor();
        transactions.setTransactionManager(transactionManager);
        transactions.setTransactionAttributeSource(source);
    }

    @Bean
    Clock aiInitiativeClock() {
        return Clock.systemUTC();
    }

    @Bean
    CreateAIInitiativeUseCase createAIInitiativeUseCase(
            AIInitiativeRepository repository,
            Clock clock
    ) {
        return transactional(
                new CreateAIInitiativeUseCase(repository, clock),
                CreateAIInitiativeUseCase.class
        );
    }

    @Bean
    SubmitAIInitiativeUseCase submitAIInitiativeUseCase(
            AIInitiativeRepository repository,
            Clock clock
    ) {
        return transactional(
                new SubmitAIInitiativeUseCase(repository, clock),
                SubmitAIInitiativeUseCase.class
        );
    }

    @Bean
    StartAssessmentAIInitiativeUseCase startAssessmentAIInitiativeUseCase(
            AIInitiativeRepository repository
    ) {
        return transactional(
                new StartAssessmentAIInitiativeUseCase(repository),
                StartAssessmentAIInitiativeUseCase.class
        );
    }

    @Bean
    AssessRiskAIInitiativeUseCase assessRiskAIInitiativeUseCase(
            AIInitiativeRepository repository,
            Clock clock
    ) {
        return transactional(
                new AssessRiskAIInitiativeUseCase(repository, clock),
                AssessRiskAIInitiativeUseCase.class
        );
    }

    @Bean
    ApproveAIInitiativeUseCase approveAIInitiativeUseCase(
            AIInitiativeRepository repository,
            Clock clock
    ) {
        return transactional(
                new ApproveAIInitiativeUseCase(repository, clock),
                ApproveAIInitiativeUseCase.class
        );
    }

    @Bean
    RejectAIInitiativeUseCase rejectAIInitiativeUseCase(
            AIInitiativeRepository repository,
            Clock clock
    ) {
        return transactional(
                new RejectAIInitiativeUseCase(repository, clock),
                RejectAIInitiativeUseCase.class
        );
    }

    private <T> T transactional(T target, Class<T> useCaseType) {
        // Class proxies preserve the existing concrete use-case contracts.
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(transactions);
        return useCaseType.cast(factory.getProxy());
    }
}
