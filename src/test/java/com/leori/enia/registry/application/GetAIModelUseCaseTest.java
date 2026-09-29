package com.leori.enia.registry.application;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.application.exception.AIModelNotFoundException;
import com.leori.enia.registry.application.port.AIModelRepository;
import com.leori.enia.registry.domain.AIModel;
import com.leori.enia.registry.domain.AIModelId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetAIModelUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-29T10:00:00Z");

    @Test
    void returns_existing_model() {
        AIModel model = model();
        InMemoryAIModelRepository repository = new InMemoryAIModelRepository(model);
        GetAIModelUseCase useCase = new GetAIModelUseCase(repository);

        AIModel result = useCase.execute(model.id());

        assertSame(model, result);
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void missing_model_fails() {
        InMemoryAIModelRepository repository = new InMemoryAIModelRepository();
        GetAIModelUseCase useCase = new GetAIModelUseCase(repository);
        AIModelId missingId = AIModelId.generate();

        AIModelNotFoundException exception = assertThrows(
                AIModelNotFoundException.class,
                () -> useCase.execute(missingId)
        );

        assertEquals("AI model not found: " + missingId, exception.getMessage());
        assertEquals(1, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void null_id_fails_before_repository_access() {
        InMemoryAIModelRepository repository = new InMemoryAIModelRepository();
        GetAIModelUseCase useCase = new GetAIModelUseCase(repository);

        NullPointerException exception = assertThrows(NullPointerException.class, () -> useCase.execute(null));

        assertEquals("AI model id is required", exception.getMessage());
        assertEquals(0, repository.finds);
        assertEquals(0, repository.creates);
    }

    @Test
    void read_does_not_write() {
        AIModel model = model();
        InMemoryAIModelRepository repository = new InMemoryAIModelRepository(model);
        GetAIModelUseCase useCase = new GetAIModelUseCase(repository);

        useCase.execute(model.id());

        assertEquals(0, repository.creates);
    }

    private AIModel model() {
        return AIModel.builder()
                .id(AIModelId.generate())
                .systemId(AISystemId.generate())
                .name("Vision Model")
                .description("Object detection")
                .provider("OpenAI")
                .createdAt(CREATED_AT)
                .build();
    }

    private static final class InMemoryAIModelRepository implements AIModelRepository {
        private final Map<AIModelId, AIModel> models = new HashMap<>();
        private int finds;
        private int creates;

        private InMemoryAIModelRepository(AIModel... models) {
            for (AIModel model : models) {
                this.models.put(model.id(), model);
            }
        }

        @Override
        public AIModel create(AIModel model) {
            creates++;
            throw new AssertionError("Read use case must not create an AI model");
        }

        @Override
        public Optional<AIModel> findById(AIModelId id) {
            finds++;
            return Optional.ofNullable(models.get(id));
        }
    }
}
