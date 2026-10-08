package com.leori.enia.governance.application.port;

import com.leori.enia.governance.application.AISystemGovernanceSummary;
import com.leori.enia.governance.domain.AISystemId;

public interface AISystemGovernanceSummaryRepository {

    AISystemGovernanceSummary summarize(AISystemId aiSystemId);
}
