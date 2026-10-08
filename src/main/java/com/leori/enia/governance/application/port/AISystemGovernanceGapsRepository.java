package com.leori.enia.governance.application.port;

import com.leori.enia.governance.application.AISystemGovernanceGaps;
import com.leori.enia.governance.domain.AISystemId;

public interface AISystemGovernanceGapsRepository {

    AISystemGovernanceGaps findByAISystemId(AISystemId aiSystemId);
}
