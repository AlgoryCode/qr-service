package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.PlatformFeedbackDtos;
import com.ael.algoryqrservice.model.dto.ProductImageDtos;
import com.ael.algoryqrservice.service.PlatformFeedbackService;
import com.ael.algoryqrservice.service.ProductImageStorageService;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/platform-feedback")
@RequiredArgsConstructor
public class PlatformFeedbackController {

    private final PlatformFeedbackService platformFeedbackService;
    private final ProductImageStorageService productImageStorageService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<PlatformFeedbackDtos.FeedbackItemResponse> create(
            @Valid @RequestBody PlatformFeedbackDtos.CreateRequest request
    ) {
        return ResponseEntity.status(201).body(platformFeedbackService.create(request));
    }

    @GetMapping("/my")
    public ResponseEntity<PlatformFeedbackDtos.FeedbackPageResponse> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(platformFeedbackService.listMine(page, size));
    }

    @PostMapping("/screenshot")
    public ResponseEntity<ProductImageDtos.UploadResponse> uploadScreenshot(
            @RequestParam("file") MultipartFile file
    ) {
        Long userId = securityUtils.getCurrentUserId();
        return ResponseEntity.status(201).body(productImageStorageService.uploadFeedbackScreenshot(userId, file));
    }
}
