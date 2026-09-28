package com.leori.enia.governance.application;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.governance.application.port.AISystemRepository;
import com.leori.enia.governance.domain.AISystem;
import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetAISystemUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-25T14:00:00Z");

    @Test
    void returns_existing_system() {
        AISystem system = system();
        InMemoryAISystemRepository repository = new InMemoryAISystemRepository(system);
        GetAISystemUseCase useCase = new GetAISystemUseCase(repository);

        AISystem result = useCase.execute(system.id());

        assertSame(system, result);
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void missing_system_fails() {
        InMemoryAISystemRepository repository = new InMemoryAISystemRepository();
        GetAISystemUseCase useCase = new GetAISystemUseCase(repository);
        AISystemId missingId = AISystemId.generate();

        AISystemNotFoundException exception = assertThrows(
                AISystemNotFoundException.class,
                () -> useCase.execute(missingId)
        );

        assertEquals("AI system not found: " + missingId, exception.getMessage());
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void null_id_fails_before_repository_access() {
        InMemoryAISystemRepository repository = new InMemoryAISystemRepository();
        GetAISystemUseCase useCase = new GetAISystemUseCase(repository);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("AI system id is required", exception.getMessage());
        assertEquals(0, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void read_does_not_write() {
        AISystem system = system();
        InMemoryAISystemRepository repository = new InMemoryAISystemRepository(system);
        GetAISystemUseCase useCase = new GetAISystemUseCase(repository);

        useCase.execute(system.id());

        assertEquals(0, repository.creates);
    }

    private AISystem system() {
        return AISystem.builder()
                .id(AISystemId.generate())
                .organizationId(OrganizationId.generate())
                .sourceInitiativeId(AIInitiativeId.generate())
                .name("System")
                .description("Description")
                .createdAt(CREATED_AT)
                .build();
    }

    private static final class InMemoryAISystemRepository implements AISystemRepository {
        private final Map<AISystemId, AISystem> systems = new HashMap<>();
        private int finds;
        private int creates;

        private InMemoryAISystemRepository(AISystem... systems) {
            for (AISystem system : systems) {
                this.systems.put(system.id(), system);
            }
        }

        @Override
        public AISystem create(AISystem system) {
            creates++;
            throw new AssertionError("Read use case must not create an AI system");
        }

        @Override
        public Optional<AISystem> findById(AISystemId id) {
            finds++;
            return Optional.ofNullable(systems.get(id));
        }
    }
}
