package com.leori.enia.registry.application.port;

import com.leori.enia.registry.domain.AIModel;

public interface AIModelRepository {

  /**
     * Inserts a new model, never updating, merging, replacing or upserting a row.
     * Returns the same aggregate with its business state and pending events unchanged.
     * The configured repository starts a REQUIRED transaction or joins the caller's;
     * returning from a joined transaction does not imply that it has committed.
     */
  AIModel create(AIModel model);
}
