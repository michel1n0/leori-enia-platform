package com.leori.enia.evidence.infrastructure.persistence;

import com.leori.enia.evidence.domain.Evidence;
import com.leori.enia.evidence.domain.EvidenceId;
import com.leori.enia.risk.domain.ControlImplementationId;
import org.springframework.stereotype.Component;

@Component
class EvidencePersistenceMapper {

    EvidenceJpaEntity toEntity(Evidence evidence) {
        return new EvidenceJpaEntity(
                evidence.id().value(),
                evidence.controlImplementationId().value(),
                evidence.description(),
                evidence.reference(),
                evidence.recordedAt()
        );
    }

    Evidence toDomain(EvidenceJpaEntity entity) {
        return Evidence.rehydrate(
                new EvidenceId(entity.id()),
                new ControlImplementationId(entity.controlImplementationId()),
                entity.description(),
                entity.reference(),
                entity.recordedAt()
        );
    }
}
