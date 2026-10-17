package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.AdminJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantStaffJpaRepository;
import org.springframework.stereotype.Component;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Component
public class SubjectLoader {

    private final MerchantStaffJpaRepository staffRepository;
    private final MerchantJpaRepository merchantRepository;
    private final AdminJpaRepository adminRepository;
    private final AuthSubjectMapper mapper;
    private final FindMerchantUseCase findMerchantUseCase;
    private final Map<String, Function<Long, Optional<AuthSubject>>> loaders;

    public SubjectLoader(
            MerchantStaffJpaRepository staffRepository,
            MerchantJpaRepository merchantRepository,
            AdminJpaRepository adminRepository,
            AuthSubjectMapper mapper,
            FindMerchantUseCase findMerchantUseCase
    ) {
        this.staffRepository = staffRepository;
        this.merchantRepository = merchantRepository;
        this.adminRepository = adminRepository;
        this.mapper = mapper;
        this.findMerchantUseCase = findMerchantUseCase;
        this.loaders = Map.of(
                AuthSubject.TYPE_STAFF, this::loadStaff,
                AuthSubject.TYPE_MERCHANT, this::loadMerchant,
                AuthSubject.TYPE_ADMIN, this::loadAdmin
        );
    }

    @AuthTransactional(readOnly = true)
    public Optional<AuthSubject> load(String subjectType, Long subjectId) {
        if (subjectType == null || subjectId == null) {
            return Optional.empty();
        }
        Function<Long, Optional<AuthSubject>> loader = loaders.get(subjectType);
        if (loader == null) {
            return Optional.empty();
        }
        return loader.apply(subjectId);
    }

    private Optional<AuthSubject> loadStaff(Long id) {
        return staffRepository.findActiveByIdWithRole(id).map(mapper::fromStaff);
    }

    private Optional<AuthSubject> loadMerchant(Long id) {
        return merchantRepository.findWithRoleById(id)
                .filter(merchant -> FindMerchantUseCase.ACTIVE.equals(findMerchantUseCase.findMerchant(merchant.getId()).status()))
                .map(mapper::fromMerchant);
    }

    private Optional<AuthSubject> loadAdmin(Long id) {
        return adminRepository.findActiveByIdWithRole(id).map(mapper::fromAdmin);
    }
}
