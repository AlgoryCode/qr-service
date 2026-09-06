package com.ael.algoryqrservice.trial.extend;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.trial.domain.TrialLifecycle;
import com.ael.algoryqrservice.trial.domain.TrialSnapshot;

public interface TrialExtendHandler {

    TrialLifecycle supports();

    Purchase extend(Long userId, TrialSnapshot snapshot, int days);
}
