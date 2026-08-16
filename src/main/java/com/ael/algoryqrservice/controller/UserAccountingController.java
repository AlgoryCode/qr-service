package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.UserAccountingDtos;
import com.ael.algoryqrservice.service.UserAccountingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/accounting/entries")
@RequiredArgsConstructor
public class UserAccountingController {

    private final UserAccountingService userAccountingService;

    @GetMapping
    public ResponseEntity<UserAccountingDtos.EntryPageResponse> list(
            @RequestParam(required = false, defaultValue = "all") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(userAccountingService.listForCurrentUser(type, from, to, q, page, size));
    }

    @PostMapping
    public ResponseEntity<UserAccountingDtos.EntryResponse> create(
            @Valid @RequestBody UserAccountingDtos.CreateRequest request
    ) {
        return ResponseEntity.status(201).body(userAccountingService.createManual(request));
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> delete(@PathVariable Long entryId) {
        userAccountingService.deleteManual(entryId);
        return ResponseEntity.noContent().build();
    }
}
