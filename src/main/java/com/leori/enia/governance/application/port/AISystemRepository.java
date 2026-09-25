package com.leori.enia.governance.application.port;

import com.leori.enia.governance.application.exception.AISystemAlreadyRegisteredException;
import com.leori.enia.governance.domain.AISystem;

public interface AISystemRepository {

    /**
     * Inserts a new system, never updating, merging, replacing or upserting a row.
     * Returns the same aggregate with its business state and pending events unchanged.
     * The configured repository starts a REQUIRED transaction or joins the caller's;
     * returning from a joined transaction does not imply that it has committed.
     * An existing system ID fails without being classified as a source duplicate.
     *
     * @throws AISystemAlreadyRegisteredException if the source already has a system
     */
    AISystem create(AISystem system);
}
