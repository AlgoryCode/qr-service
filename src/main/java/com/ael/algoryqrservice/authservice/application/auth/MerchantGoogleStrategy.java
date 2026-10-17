package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.RoleEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.RoleJpaRepository;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.util.Locale;
import java.util.Optional;
import com.ael.algoryqrservice.qrservice.store.model.Merchant;

@Component
@RequiredArgsConstructor
public class MerchantGoogleStrategy implements GoogleSubjectStrategy {

    static final String BASIC_ACCOUNT_MESSAGE =
            "Bu e-posta adresi e-posta/şifre ile kayıtlı. Lütfen e-posta ve şifre ile giriş yapın.";

    private final MerchantJpaRepository merchantRepository;
    private final RoleJpaRepository roleRepository;
    private final AuthSubjectMapper mapper;
    private final FindMerchantUseCase findMerchantUseCase;

    @Override
    public String audience() {
        return AuthSubject.TYPE_MERCHANT;
    }

    @Override
    @AuthTransactional
    public AuthSubject login(GoogleOidcIdentity identity) {
        requireVerified(identity);
        Optional<MerchantEntity> bySubject = merchantRepository.findByProviderSubjectAndDeletedAtIsNull(identity.subject());
        if (bySubject.isPresent()) {
            return activeGoogle(bySubject.get());
        }
        merchantRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(identity.email()).ifPresent(this::rejectNonGoogle);
        throw new UnauthorizedException("Bu e-posta adresi ile Google hesabı kayıtlı değil");
    }

    @Override
    @AuthTransactional
    public AuthSubject register(GoogleOidcIdentity identity, ClientInfo clientInfo, String provider) {
        requireVerified(identity);
        requireGoogleProvider(provider);
        if (merchantRepository.findByProviderSubjectAndDeletedAtIsNull(identity.subject()).isPresent()) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }
        merchantRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(identity.email()).ifPresent(existing -> {
            rejectNonGoogle(existing);
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        });
        MerchantEntity merchant = merchantRepository.save(newMerchant(identity, provider));
        return mapper.fromMerchant(merchant);
    }

    private AuthSubject activeGoogle(MerchantEntity merchant) {
        if (!FindMerchantUseCase.ACTIVE.equals(findMerchantUseCase.findMerchant(merchant.getId()).status())) {
            throw new UnauthorizedException("Hesap pasif");
        }
        rejectNonGoogle(merchant);
        return mapper.fromMerchant(merchant);
    }

    private void rejectNonGoogle(MerchantEntity merchant) {
        if (!GoogleProviders.isGoogle(merchant.getProvider())) {
            throw new BadRequestException(conflictMessage(merchant.getProvider()));
        }
    }

    private MerchantEntity newMerchant(GoogleOidcIdentity identity, String provider) {
        RoleEntity role = roleRepository.findByCode(AuthSubject.TYPE_MERCHANT)
                .orElseThrow(() -> new IllegalStateException("MERCHANT role missing"));
        MerchantEntity merchant = new MerchantEntity();
        merchant.setRole(role);
        merchant.setEmail(identity.email().trim().toLowerCase(Locale.ROOT));
        merchant.setFirstName(firstName(identity));
        merchant.setLastName(blankToNull(identity.lastName()));
        merchant.setPassword(null);
        merchant.setProvider(provider);
        merchant.setProviderSubject(identity.subject());
        merchant.setActive(true);
        merchant.setEmailVerified(true);
        return merchant;
    }

    private static void requireVerified(GoogleOidcIdentity identity) {
        if (identity == null || !identity.emailVerified() || identity.email() == null || identity.email().isBlank()) {
            throw new UnauthorizedException("Doğrulanmış Google e-posta adresi zorunludur");
        }
        if (identity.subject() == null || identity.subject().isBlank()) {
            throw new UnauthorizedException("Google kimlik doğrulaması tamamlanamadı");
        }
    }

    private static void requireGoogleProvider(String provider) {
        if (!GoogleProviders.isGoogle(provider)) {
            throw new BadRequestException("Geçersiz Google kimlik doğrulama yöntemi");
        }
    }

    private static String conflictMessage(String provider) {
        if (GoogleProviders.BASIC.equalsIgnoreCase(provider)) {
            return BASIC_ACCOUNT_MESSAGE;
        }
        return "Bu hesap farklı bir giriş yöntemiyle oluşturulmuş";
    }

    private static String firstName(GoogleOidcIdentity identity) {
        if (identity.firstName() != null && !identity.firstName().isBlank()) {
            return identity.firstName().trim();
        }
        int at = identity.email().indexOf('@');
        return at > 0 ? identity.email().substring(0, at) : "Merchant";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
