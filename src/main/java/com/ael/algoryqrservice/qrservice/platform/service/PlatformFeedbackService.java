package com.ael.algoryqrservice.qrservice.platform.service;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.PlatformFeedback;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.platform.dto.PlatformFeedbackDtos;
import com.ael.algoryqrservice.qrservice.model.enums.PlatformFeedbackStatus;
import com.ael.algoryqrservice.qrservice.repository.PlatformFeedbackRepository;
import com.ael.algoryqrservice.qrservice.repository.PlatformFeedbackSpecifications;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformFeedbackService {

    private static final Set<String> ALLOWED_SUBJECTS = Set.of(
            "Teknik sorun",
            "Abonelik / Ödeme",
            "Dijital menü",
            "Garson paneli",
            "Öneri",
            "Diğer"
    );

    private final PlatformFeedbackRepository platformFeedbackRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public PlatformFeedbackDtos.FeedbackItemResponse create(PlatformFeedbackDtos.CreateRequest request) {
        if (request == null) {
            throw new BadRequestException("İstek gövdesi zorunludur");
        }

        User user = securityUtils.getCurrentUser();
        String title = requireText(request.getTitle(), "Başlık zorunludur", 120);
        String subject = requireText(request.getSubject(), "Konu zorunludur", 60);
        String description = requireText(request.getDescription(), "Açıklama zorunludur", 5000);

        if (!ALLOWED_SUBJECTS.contains(subject)) {
            throw new BadRequestException("Geçersiz konu seçimi");
        }

        validateScreenshotOwnership(user.getId(), request.getScreenshotKey());

        LocalDateTime now = LocalDateTime.now();
        PlatformFeedback feedback = PlatformFeedback.builder()
                .merchantId(user.getId())
                .title(title)
                .subject(subject)
                .description(description)
                .screenshotUrl(trimToNull(request.getScreenshotUrl()))
                .screenshotKey(trimToNull(request.getScreenshotKey()))
                .status(PlatformFeedbackStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toItemResponse(platformFeedbackRepository.save(feedback));
    }

    @Transactional(readOnly = true)
    public PlatformFeedbackDtos.FeedbackPageResponse listMine(int page, int size) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return list(merchantId, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public PlatformFeedbackDtos.FeedbackPageResponse listForAdmin(
            PlatformFeedbackStatus status,
            String q,
            int page,
            int size
    ) {
        return list(null, status, q, page, size);
    }

    @Transactional(readOnly = true)
    public PlatformFeedbackDtos.FeedbackItemResponse getByIdForAdmin(Long id) {
        PlatformFeedback feedback = platformFeedbackRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Geri bildirim bulunamadı"));
        return toItemResponse(feedback);
    }

    @Transactional
    public PlatformFeedbackDtos.FeedbackItemResponse updateForAdmin(
            Long id,
            PlatformFeedbackDtos.AdminUpdateRequest request
    ) {
        PlatformFeedback feedback = platformFeedbackRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Geri bildirim bulunamadı"));

        if (request != null) {
            if (request.getStatus() != null) {
                feedback.setStatus(request.getStatus());
            }
            if (request.getAdminNote() != null) {
                feedback.setAdminNote(trimToNull(request.getAdminNote()));
            }
        }

        feedback.setUpdatedAt(LocalDateTime.now());
        return toItemResponse(platformFeedbackRepository.save(feedback));
    }

    private PlatformFeedbackDtos.FeedbackPageResponse list(
            Long merchantId,
            PlatformFeedbackStatus status,
            String q,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<PlatformFeedback> spec = Specification
                .where(PlatformFeedbackSpecifications.hasMerchantId(merchantId))
                .and(PlatformFeedbackSpecifications.hasStatus(status))
                .and(PlatformFeedbackSpecifications.matchesQuery(q));

        Page<PlatformFeedback> result = platformFeedbackRepository.findAll(spec, pageable);
        Map<Long, User> usersById = loadUsers(result);

        return new PlatformFeedbackDtos.FeedbackPageResponse(
                result.getContent().stream()
                        .map(item -> toItemResponse(item, usersById.get(item.getMerchantId())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    private Map<Long, User> loadUsers(Page<PlatformFeedback> result) {
        Set<Long> userIds = result.getContent().stream()
                .map(PlatformFeedback::getMerchantId)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private PlatformFeedbackDtos.FeedbackItemResponse toItemResponse(PlatformFeedback feedback) {
        User user = userRepository.findById(feedback.getMerchantId()).orElse(null);
        return toItemResponse(feedback, user);
    }

    private PlatformFeedbackDtos.FeedbackItemResponse toItemResponse(PlatformFeedback feedback, User user) {
        String fullName = user == null
                ? "Bilinmeyen kullanıcı"
                : ((user.getFirstName() != null ? user.getFirstName() : "")
                + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();

        return new PlatformFeedbackDtos.FeedbackItemResponse(
                feedback.getId(),
                feedback.getMerchantId(),
                user != null ? user.getEmail() : null,
                fullName.isBlank() ? (user != null ? user.getEmail() : null) : fullName,
                feedback.getTitle(),
                feedback.getSubject(),
                feedback.getDescription(),
                feedback.getScreenshotUrl(),
                feedback.getStatus(),
                feedback.getAdminNote(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt()
        );
    }

    private void validateScreenshotOwnership(Long merchantId, String screenshotKey) {
        if (screenshotKey == null || screenshotKey.isBlank()) {
            return;
        }
        String expectedPrefix = "platform-feedback/" + merchantId + "/";
        if (!screenshotKey.startsWith(expectedPrefix)) {
            throw new BadRequestException("Ekran görüntüsü bu kullanıcıya ait değil");
        }
    }

    private String requireText(String value, String message, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BadRequestException(message);
        }
        if (trimmed.length() > maxLength) {
            throw new BadRequestException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
