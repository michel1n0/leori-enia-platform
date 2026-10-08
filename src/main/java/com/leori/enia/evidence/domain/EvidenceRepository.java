package com.leori.enia.evidence.domain;

import java.util.Optional;

public interface EvidenceRepository {

    Evidence create(Evidence evidence);

    Optional<Evidence> findById(EvidenceId id);
}
