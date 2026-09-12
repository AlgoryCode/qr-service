package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public final class ThemeDtos {

    private ThemeDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThemeResponse {
        private Long id;
        private String code;
        private String name;
        private String description;
        private Map<String, Object> previewMeta;
        private int sortOrder;
        private boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignThemesRequest {
        /** Theme codes matching FE theme keys / catalog {@code code}. */
        @NotNull
        @NotEmpty
        private List<@NotNull @Size(min = 1, max = 64) String> themeCodes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplaceThemesRequest {
        /**
         * Full replacement set for the member. Empty list clears all assignments.
         * Null is rejected by validation.
         */
        @NotNull
        private List<@NotNull @Size(min = 1, max = 64) String> themeCodes;
    }
}
