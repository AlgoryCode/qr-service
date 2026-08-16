package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PlatformFeedbackStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

public final class PlatformFeedbackDtos {

    private PlatformFeedbackDtos() {
    }

    @Getter
    @Setter
    public static class CreateRequest {
        @NotBlank
        @Size(max = 120)
        private String title;

        @NotBlank
        @Size(max = 60)
        private String subject;

        @NotBlank
        @Size(max = 5000)
        private String description;

        @Size(max = 2048)
        private String screenshotUrl;

        @Size(max = 512)
        private String screenshotKey;
    }

    @Getter
    @Setter
    public static class AdminUpdateRequest {
        private PlatformFeedbackStatus status;

        @Size(max = 5000)
        private String adminNote;
    }

    public record FeedbackItemResponse(
            Long id,
            Long userId,
            String userEmail,
            String userFullName,
            String title,
            String subject,
            String description,
            String screenshotUrl,
            PlatformFeedbackStatus status,
            String adminNote,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record FeedbackPageResponse(
            List<FeedbackItemResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
