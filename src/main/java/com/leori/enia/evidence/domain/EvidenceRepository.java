package com.leori.enia.evidence.domain;

import com.leori.enia.risk.domain.ControlImplementationId;

import java.util.List;
import java.util.Optional;

public interface EvidenceRepository {

    Evidence create(Evidence evidence);

    Optional<Evidence> findById(EvidenceId id);

    List<Evidence> findByControlImplementationId(ControlImplementationId controlImplementationId);
}
