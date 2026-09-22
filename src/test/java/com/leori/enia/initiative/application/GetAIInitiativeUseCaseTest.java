package com.leori.enia.initiative.application;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.port.AIInitiativeRepository;
import com.leori.enia.initiative.application.port.LoadedAIInitiative;
import com.leori.enia.initiative.domain.AIInitiative;
import com.leori.enia.initiative.domain.AIInitiativeId;
import com.leori.enia.initiative.domain.InitiativeStatus;
import com.leori.enia.initiative.domain.RiskLevel;
import com.leori.enia.organization.domain.OrganizationId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetAIInitiativeUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-21T14:00:00Z");

    @Test
    void returns_details_for_an_existing_initiative() {
        AIInitiative initiative = AIInitiative.builder()
                .id(AIInitiativeId.generate())
                .organizationId(OrganizationId.generate())
                .name("  Read initiative  ")
                .description("  Read description  ")
                .usesPersonalData(true)
                .impactsRights(false)
                .createdAt(CREATED_AT)
                .build();
        GetAIInitiativeUseCase useCase = new GetAIInitiativeUseCase(
                repository(Optional.of(new LoadedAIInitiative(initiative, 42))));

        AIInitiativeDetails details = useCase.execute(initiative.id());

        assertEquals(initiative.id(), details.id());
        assertEquals(initiative.organizationId(), details.organizationId());
        assertEquals("Read initiative", details.name());
        assertEquals("Read description", details.description());
        assertEquals(InitiativeStatus.DRAFT, details.status());
        assertEquals(RiskLevel.NOT_ASSESSED, details.preliminaryRisk());
        assertEquals(true, details.usesPersonalData());
        assertEquals(false, details.impactsRights());
        assertEquals(CREATED_AT, details.createdAt());
    }

    @Test
    void missing_initiative_uses_the_application_not_found_exception() {
        AIInitiativeId id = AIInitiativeId.generate();
        GetAIInitiativeUseCase useCase = new GetAIInitiativeUseCase(repository(Optional.empty()));

        assertThrows(AIInitiativeNotFoundException.class, () -> useCase.execute(id));
    }

    @Test
    void public_read_result_has_no_revision_or_persistence_wrapper() {
        Set<String> fields = Arrays.stream(AIInitiativeDetails.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("id", "organizationId", "name", "description", "status",
                "preliminaryRisk", "usesPersonalData", "impactsRights", "createdAt"), fields);
    }

    private static AIInitiativeRepository repository(Optional<LoadedAIInitiative> loaded) {
        return new AIInitiativeRepository() {
            @Override
            public AIInitiative create(AIInitiative initiative) {
                throw new AssertionError("Read must not create");
            }

            @Override
            public AIInitiative save(LoadedAIInitiative initiative) {
                throw new AssertionError("Read must not save");
            }

            @Override
            public Optional<LoadedAIInitiative> findById(AIInitiativeId id) {
                return loaded;
            }
        };
    }
}
