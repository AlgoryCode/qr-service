package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.model.dto.AccountOverviewDtos;
import com.ael.algoryqrservice.model.dto.BillingAddressPageResponse;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.SessionPageResponse;
import com.ael.algoryqrservice.model.dto.SubscriptionOverviewResponse;
import com.ael.algoryqrservice.service.AccountFacadeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountFacadeService accountFacade;

    @GetMapping("/myprofile")
    public AccountDtos.MyProfileResponse myProfile() {
        return accountFacade.getMyProfile();
    }

    @PatchMapping("/myprofile")
    public AccountDtos.MyProfileResponse updateMyProfile(@Valid @RequestBody AccountDtos.MyProfilePatchRequest request) {
        return accountFacade.updateMyProfile(request);
    }

    @GetMapping("/billing-addresses")
    public BillingAddressPageResponse billingAddresses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return accountFacade.listBillingAddresses(page, size);
    }

    @GetMapping("/payment-methods")
    public List<BillingPaymentDtos.PaymentMethod> paymentMethods() {
        return accountFacade.listPaymentMethods();
    }

    @GetMapping("/purchases")
    public List<PurchaseResponse> purchases() {
        return accountFacade.listPurchases();
    }

    @GetMapping("/subscription-overview")
    public SubscriptionOverviewResponse subscriptionOverview() {
        return accountFacade.subscriptionOverview();
    }

    @GetMapping("/sessions")
    public SessionPageResponse sessions(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return accountFacade.listSessions(readBearerToken(request), page, size);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId) {
        accountFacade.revokeSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/overview")
    public ResponseEntity<AccountOverviewDtos.OverviewResponse> overview(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int sessionPage,
            @RequestParam(defaultValue = "" + AccountOverviewDtos.DEFAULT_SESSION_PAGE_SIZE) int sessionSize
    ) {
        AccountOverviewDtos.OverviewResponse body = accountFacade.getOverview(
                readBearerToken(request),
                sessionPage,
                sessionSize
        );
        if (body.getProfile() != null && !body.getProfile().isOk()) {
            int status = body.getProfile().getStatus() != null ? body.getProfile().getStatus() : 502;
            if (status == 401) {
                return ResponseEntity.status(401).body(body);
            }
            if (status >= 500) {
                return ResponseEntity.status(502).body(body);
            }
            return ResponseEntity.status(status).body(body);
        }
        return ResponseEntity.ok(body);
    }

    private static String readBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }
}
