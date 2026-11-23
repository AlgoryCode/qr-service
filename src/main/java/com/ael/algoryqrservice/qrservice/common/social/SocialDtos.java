package com.ael.algoryqrservice.qrservice.common.social;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class SocialDtos {

    private SocialDtos() {
    }

    /**
     * Social media links. In a request a null field keeps the stored value and an empty
     * string clears it. A value may be a full URL, a domain path or a bare handle.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Links {
        @Size(max = 512)
        private String facebook;
        @Size(max = 512)
        private String instagram;
        @Size(max = 512)
        private String youtube;
        @Size(max = 512)
        private String tiktok;
        @Size(max = 512)
        private String x;
    }

    /** Whether each platform is shown on the public menu. A null field keeps the stored value. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Visibility {
        private Boolean facebook;
        private Boolean instagram;
        private Boolean youtube;
        private Boolean tiktok;
        private Boolean x;
    }
}
