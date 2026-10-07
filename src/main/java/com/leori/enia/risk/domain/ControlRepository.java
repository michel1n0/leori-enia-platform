package com.leori.enia.risk.domain;

import java.util.Optional;

/** Persists Control aggregates. */
public interface ControlRepository {

    Control create(Control control);

    Optional<Control> findById(ControlId id);
}
