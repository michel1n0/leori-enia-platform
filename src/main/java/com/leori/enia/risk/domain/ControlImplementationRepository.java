package com.leori.enia.risk.domain;

import java.util.Optional;

/** Persists ControlImplementation aggregates. */
public interface ControlImplementationRepository {

    ControlImplementation create(ControlImplementation implementation);

    Optional<ControlImplementation> findById(ControlImplementationId id);
}
