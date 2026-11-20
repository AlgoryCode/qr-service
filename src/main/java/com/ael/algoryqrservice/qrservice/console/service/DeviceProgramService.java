package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType;
import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceType;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomer;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomerTxn;
import com.ael.algoryqrservice.qrservice.model.DeviceMembership;
import com.ael.algoryqrservice.qrservice.model.DeviceMembershipPlan;
import com.ael.algoryqrservice.qrservice.model.DeviceProgramSetting;
import com.ael.algoryqrservice.qrservice.model.DeviceTier;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerTxnRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceMembershipPlanRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceMembershipRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceProgramSettingRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTierRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The merchant-wide customer programme: levels by total spending, referral bonuses and memberships. Customer cards
 * are shared by every branch, so all of this is per merchant.
 */
@Service
@RequiredArgsConstructor
public class DeviceProgramService {

    private final DeviceProgramSettingRepository settingRepository;
    private final DeviceTierRepository tierRepository;
    private final DeviceMembershipPlanRepository planRepository;
    private final DeviceMembershipRepository membershipRepository;
    private final DeviceCustomerRepository customerRepository;
    private final DeviceCustomerTxnRepository txnRepository;
    private final DeviceSaleAccounting saleAccounting;
    private final ConsoleAccess access;

    // ---- Settings and levels -----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ConsoleDtos.ProgramResponse getProgram() {
        Long merchantId = access.merchantId();
        DeviceProgramSetting setting = setting(merchantId).orElse(null);
        return new ConsoleDtos.ProgramResponse(
                setting == null ? null : setting.getReferralRewardAmount(),
                setting == null ? null : setting.getReferralWelcomeAmount(),
                tiers(merchantId).stream().map(DeviceProgramService::tier).toList()
        );
    }

    @Transactional
    public ConsoleDtos.ProgramResponse updateProgram(ConsoleDtos.ProgramRequest request) {
        Long merchantId = access.merchantId();
        DeviceProgramSetting setting = settingRepository.findByMerchantId(merchantId)
                .orElseGet(() -> DeviceProgramSetting.builder().merchantId(merchantId).build());
        setting.setDeleted(false);
        setting.setReferralRewardAmount(scale(request.referralRewardAmount()));
        setting.setReferralWelcomeAmount(scale(request.referralWelcomeAmount()));
        settingRepository.save(setting);

        Set<String> names = new HashSet<>();
        Set<BigDecimal> thresholds = new HashSet<>();
        for (ConsoleDtos.TierRequest tier : request.tiers()) {
            if (!names.add(tier.name().trim().toLowerCase(Locale.forLanguageTag("tr")))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seviye adları farklı olmalı");
            }
            if (!thresholds.add(tier.minSpent().setScale(2, RoundingMode.HALF_UP))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Her seviyenin harcama sınırı farklı olmalı");
            }
        }
        List<DeviceTier> existing = tierRepository.findByMerchantId(merchantId);
        List<DeviceTier> toSave = new ArrayList<>();
        for (int i = 0; i < request.tiers().size(); i++) {
            ConsoleDtos.TierRequest tier = request.tiers().get(i);
            DeviceTier row = i < existing.size()
                    ? existing.get(i)
                    : DeviceTier.builder().merchantId(merchantId).build();
            row.setName(tier.name().trim());
            row.setMinSpent(tier.minSpent().setScale(2, RoundingMode.HALF_UP));
            row.setDiscountPercent(tier.discountPercent().setScale(2, RoundingMode.HALF_UP));
            row.setDeleted(false);
            toSave.add(row);
        }
        for (int i = request.tiers().size(); i < existing.size(); i++) {
            DeviceTier row = existing.get(i);
            if (!row.isDeleted()) {
                row.setDeleted(true);
                toSave.add(row);
            }
        }
        tierRepository.saveAll(toSave);
        return getProgram();
    }

    public Optional<DeviceProgramSetting> setting(Long merchantId) {
        return settingRepository.findByMerchantId(merchantId).filter(s -> !s.isDeleted());
    }

    public List<DeviceTier> tiers(Long merchantId) {
        return tierRepository.findByMerchantIdAndDeletedFalseOrderByMinSpentAscIdAsc(merchantId);
    }

    /** The highest level whose threshold the spending reaches; null below every level. */
    public static DeviceTier tierFor(List<DeviceTier> tiers, BigDecimal totalSpent) {
        BigDecimal spent = totalSpent == null ? BigDecimal.ZERO : totalSpent;
        DeviceTier best = null;
        for (DeviceTier tier : tiers) {
            if (spent.compareTo(tier.getMinSpent()) >= 0
                    && (best == null || tier.getMinSpent().compareTo(best.getMinSpent()) > 0)) {
                best = tier;
            }
        }
        return best;
    }

    /** Customer cards with their level and the membership minutes they can use now. */
    public List<ConsoleDtos.CustomerResponse> customers(List<DeviceCustomer> customers) {
        if (customers.isEmpty()) {
            return List.of();
        }
        List<DeviceTier> tiers = tiers(customers.get(0).getMerchantId());
        LocalDateTime now = AppTime.nowLocal();
        return customers.stream()
                .map(c -> DeviceMappers.customer(c, tierFor(tiers, c.getTotalSpent()),
                        membershipRepository.findUsable(c.getId(), c.getMerchantId(), now).stream()
                                .mapToInt(DeviceMembership::getMinutesLeft)
                                .sum()))
                .toList();
    }

    public ConsoleDtos.CustomerResponse customer(DeviceCustomer customer) {
        return customers(List.of(customer)).get(0);
    }

    // ---- Membership plans --------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConsoleDtos.MembershipPlanResponse> listPlans(boolean activeOnly) {
        return planRepository.findByMerchantIdAndDeletedFalseOrderByPriceAscIdAsc(access.merchantId()).stream()
                .filter(plan -> !activeOnly || plan.isActive())
                .map(DeviceProgramService::plan)
                .toList();
    }

    @Transactional
    public ConsoleDtos.MembershipPlanResponse createPlan(ConsoleDtos.MembershipPlanRequest request) {
        DeviceMembershipPlan plan = DeviceMembershipPlan.builder().merchantId(access.merchantId()).deleted(false).build();
        apply(plan, request);
        return plan(planRepository.save(plan));
    }

    @Transactional
    public ConsoleDtos.MembershipPlanResponse updatePlan(Long planId, ConsoleDtos.MembershipPlanRequest request) {
        DeviceMembershipPlan plan = requirePlan(planId);
        apply(plan, request);
        return plan(planRepository.save(plan));
    }

    /** Memberships already sold keep running. */
    @Transactional
    public void deletePlan(Long planId) {
        DeviceMembershipPlan plan = requirePlan(planId);
        plan.setDeleted(true);
        planRepository.save(plan);
    }

    private static void apply(DeviceMembershipPlan plan, ConsoleDtos.MembershipPlanRequest request) {
        plan.setName(request.name().trim());
        plan.setPrice(request.price().setScale(2, RoundingMode.HALF_UP));
        plan.setMinutes(request.minutes());
        plan.setPeriodDays(request.periodDays());
        plan.setDeviceType(request.deviceType());
        plan.setActive(request.active() == null || request.active());
    }

    private DeviceMembershipPlan requirePlan(Long planId) {
        return planRepository.findByIdAndMerchantIdAndDeletedFalse(planId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Üyelik planı bulunamadı"));
    }

    // ---- Memberships -------------------------------------------------------------------------------------------

    /** Sells a membership; the price is income (booked now), unlike a balance top-up. */
    @Transactional
    public ConsoleDtos.MembershipResponse sell(Long customerId, ConsoleDtos.SellMembershipRequest request) {
        Long merchantId = access.merchantId();
        DeviceCustomer customer = customerRepository.lockOwned(customerId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Müşteri bulunamadı"));
        DeviceMembershipPlan plan = requirePlan(request.planId());
        if (!plan.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu üyelik planı satışta değil");
        }
        Long branchId = branchOfSale(request.branchId());
        DevicePaymentMethod method = request.paymentMethod() == null ? DevicePaymentMethod.CASH : request.paymentMethod();
        LocalDateTime now = AppTime.nowLocal();
        if (method == DevicePaymentMethod.BALANCE && plan.getPrice().signum() > 0) {
            if (customer.getBalance().compareTo(plan.getPrice()) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Müşterinin bakiyesi yetersiz");
            }
            customer.setBalance(customer.getBalance().subtract(plan.getPrice()));
            customerRepository.save(customer);
            txnRepository.save(DeviceCustomerTxn.builder()
                    .merchantId(merchantId)
                    .customerId(customer.getId())
                    .branchId(branchId)
                    .staffId(access.staffId())
                    .txnType(DeviceCustomerTxnType.SPEND)
                    .amount(plan.getPrice().negate())
                    .paymentMethod(DevicePaymentMethod.BALANCE)
                    .note(truncate("Üyelik: " + plan.getName(), 255))
                    .deleted(false)
                    .build());
        }
        DeviceMembership saved = membershipRepository.save(DeviceMembership.builder()
                .merchantId(merchantId)
                .branchId(branchId)
                .customerId(customer.getId())
                .planId(plan.getId())
                .planName(plan.getName())
                .deviceType(plan.getDeviceType())
                .minutesTotal(plan.getMinutes())
                .minutesLeft(plan.getMinutes())
                .price(plan.getPrice())
                .paymentMethod(method)
                .startsAt(now)
                .endsAt(now.plusDays(plan.getPeriodDays()))
                .cancelled(false)
                .staffId(access.staffId())
                .deleted(false)
                .build());
        saleAccounting.book(
                merchantId,
                "MEMBERSHIP:" + saved.getId(),
                "Cihaz üyeliği - " + plan.getName(),
                saved.getPrice(),
                now,
                customer.getName() + " · " + plan.getMinutes() + " dk / " + plan.getPeriodDays() + " gün · Ödeme: "
                        + method.label()
        );
        return membership(saved, now);
    }

    @Transactional(readOnly = true)
    public List<ConsoleDtos.MembershipResponse> memberships(Long customerId) {
        Long merchantId = access.merchantId();
        customerRepository.findByIdAndMerchantIdAndDeletedFalse(customerId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Müşteri bulunamadı"));
        LocalDateTime now = AppTime.nowLocal();
        return membershipRepository.findByCustomerIdAndMerchantIdAndDeletedFalseOrderByEndsAtDescIdDesc(customerId, merchantId)
                .stream()
                .map(m -> membership(m, now))
                .toList();
    }

    /** Stops the remaining minutes; nothing is refunded. */
    @Transactional
    public ConsoleDtos.MembershipResponse cancel(Long membershipId) {
        DeviceMembership membership = membershipRepository.findByIdAndMerchantIdAndDeletedFalse(membershipId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Üyelik bulunamadı"));
        membership.setCancelled(true);
        return membership(membershipRepository.save(membership), AppTime.nowLocal());
    }

    /** Memberships of the customer usable on {@code deviceType} now, the one ending first first. */
    public List<DeviceMembership> usable(Long customerId, Long merchantId, DeviceType deviceType, LocalDateTime now) {
        return membershipRepository.findUsable(customerId, merchantId, now).stream()
                .filter(m -> m.getDeviceType() == null || m.getDeviceType() == deviceType)
                .toList();
    }

    private Long branchOfSale(Long requested) {
        if (access.staffId() != null) {
            return access.staffBranchId();
        }
        return requested == null ? null : access.requireBranch(requested).getId();
    }

    // ---- Mapping -----------------------------------------------------------------------------------------------

    static ConsoleDtos.TierResponse tier(DeviceTier tier) {
        return new ConsoleDtos.TierResponse(tier.getId(), tier.getName(), tier.getMinSpent(), tier.getDiscountPercent());
    }

    static ConsoleDtos.MembershipPlanResponse plan(DeviceMembershipPlan plan) {
        return new ConsoleDtos.MembershipPlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getPrice(),
                plan.getMinutes(),
                plan.getPeriodDays(),
                plan.getDeviceType(),
                plan.isActive()
        );
    }

    static ConsoleDtos.MembershipResponse membership(DeviceMembership m, LocalDateTime now) {
        boolean usable = !m.isCancelled() && m.getMinutesLeft() > 0 && !m.getStartsAt().isAfter(now) && m.getEndsAt().isAfter(now);
        return new ConsoleDtos.MembershipResponse(
                m.getId(),
                m.getCustomerId(),
                m.getPlanId(),
                m.getPlanName(),
                m.getDeviceType(),
                m.getMinutesTotal(),
                m.getMinutesLeft(),
                m.getPrice(),
                m.getPaymentMethod(),
                m.getStartsAt(),
                m.getEndsAt(),
                m.isCancelled(),
                usable
        );
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
