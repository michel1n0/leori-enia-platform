package com.leori.enia.risk.infrastructure.persistence;

import com.leori.enia.risk.domain.Control;
import com.leori.enia.risk.domain.ControlId;
import com.leori.enia.risk.domain.RiskAssessmentId;
import com.leori.enia.risk.domain.RiskFindingId;
import org.springframework.stereotype.Component;

@Component
class ControlPersistenceMapper {

    ControlJpaEntity toEntity(Control control) {
        return new ControlJpaEntity(
                control.id().value(),
                control.riskAssessmentId().value(),
                control.riskFindingId().value(),
                control.name(),
                control.description(),
                control.createdAt()
        );
    }

    Control toDomain(ControlJpaEntity entity) {
        return Control.rehydrate(
                new ControlId(entity.id()),
                new RiskAssessmentId(entity.riskAssessmentId()),
                new RiskFindingId(entity.riskFindingId()),
                entity.name(),
                entity.description(),
                entity.createdAt()
        );
    }
}
