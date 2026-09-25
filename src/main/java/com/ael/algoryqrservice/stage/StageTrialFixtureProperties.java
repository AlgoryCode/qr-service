package com.ael.algoryqrservice.stage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.stage-trial-fixture")
@Getter
@Setter
public class StageTrialFixtureProperties {

    private boolean enabled;
    private Long templateUserId = 0L;
    private Long templateBranchId = 0L;
    private Long templateMenuId = 0L;

    public boolean isReady() {
        return enabled
                && positive(templateUserId)
                && positive(templateBranchId)
                && positive(templateMenuId);
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }
}
