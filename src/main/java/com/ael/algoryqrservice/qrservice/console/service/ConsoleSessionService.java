package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType;
import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceRentalPricing;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.ConsoleRateSetting;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomer;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomerTxn;
import com.ael.algoryqrservice.qrservice.model.DeviceSessionItem;
import com.ael.algoryqrservice.qrservice.model.DeviceSessionSegment;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.qrservice.product.model.MenuProduct;
import com.ael.algoryqrservice.qrservice.product.repository.MenuProductRepository;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerTxnRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceSessionItemRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceSessionSegmentRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Running device rentals: start, pause/resume, move to another device or change controllers, extend the planned
 * time, add menu products, and finish with payment, discount and loyalty minutes.
 */
@Service
@RequiredArgsConstructor
public class ConsoleSessionService {

    static final int DEFAULT_CONTROLLERS = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ConsoleSessionRepository sessionRepository;
    private final GameConsoleRepository consoleRepository;
    private final ConsoleCatalogService catalogService;
    private final ConsoleAccess access;
    private final DeviceSessionSegmentRepository segmentRepository;
    private final DeviceSessionItemRepository itemRepository;
    private final DeviceCustomerRepository customerRepository;
    private final DeviceCustomerTxnRepository customerTxnRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuRepository menuRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ---- Lifecycle ---------------------------------------------------------------------------------------------

    @Transactional
    public ConsoleDtos.SessionResponse start(Long consoleId, ConsoleDtos.StartSessionRequest request) {
        GameConsole console = lockConsole(consoleId);
        return startOn(console, request, null);
    }

    /** Starts a session on an already locked and access-checked console. */
    public ConsoleDtos.SessionResponse startOn(GameConsole console, ConsoleDtos.StartSessionRequest request, Long reservationId) {
        if (sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(console.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu cihazda zaten açık bir oturum var");
        }
        int controllers = request == null || request.controllerCount() == null
                ? DEFAULT_CONTROLLERS
                : request.controllerCount();
        ConsoleCatalogService.RateContext rates = catalogService.rateContext(console.getBranchId(), console.getMerchantId());
        BigDecimal rate = rates.rate(console, controllers);
        if (rate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Saatlik ücret tanımlı değil; cihaza, cihaz türüne veya şubeye ücret girin"
            );
        }
        DeviceCustomer customer = null;
        if (request != null && request.customerId() != null) {
            customer = customerRepository.findByIdAndMerchantIdAndDeletedFalse(request.customerId(), console.getMerchantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Müşteri bulunamadı"));
        }
        String customerName = DeviceMappers.blankToNull(request == null ? null : request.customerName());
        if (customerName == null && customer != null) {
            customerName = customer.getName();
        }
        LocalDateTime now = AppTime.nowLocal();
        ConsoleSession saved = sessionRepository.save(ConsoleSession.builder()
                .merchantId(console.getMerchantId())
                .branchId(console.getBranchId())
                .consoleId(console.getId())
                .customerId(customer == null ? null : customer.getId())
                .customerName(customerName)
                .note(DeviceMappers.blankToNull(request == null ? null : request.note()))
                .hourlyRate(rate)
                .controllerCount(controllers)
                .plannedMinutes(request == null ? null : request.plannedMinutes())
                .reservationId(reservationId)
                .openedByStaffId(access.staffId())
                .startedAt(now)
                .deleted(false)
                .build());
        openSegment(saved, console, now);
        return single(saved, rates);
    }

    @Transactional
    public ConsoleDtos.SessionResponse pause(Long sessionId) {
        ConsoleSession session = lockActive(sessionId);
        if (session.isPaused()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Oturum zaten duraklatılmış");
        }
        LocalDateTime now = AppTime.nowLocal();
        closeOpenSegment(session.getId(), now);
        session.setPausedAt(now);
        return single(sessionRepository.save(session), null);
    }

    @Transactional
    public ConsoleDtos.SessionResponse resume(Long sessionId) {
        ConsoleSession session = lockActive(sessionId);
        if (!session.isPaused()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Oturum duraklatılmamış");
        }
        GameConsole console = consoleRepository.findById(session.getConsoleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cihaz bulunamadı"));
        session.setPausedAt(null);
        openSegment(session, console, AppTime.nowLocal());
        return single(sessionRepository.save(session), null);
    }

    /** Moves to another device and/or changes the controller count; resumes a paused session. */
    @Transactional
    public ConsoleDtos.SessionResponse transfer(Long sessionId, ConsoleDtos.TransferRequest request) {
        ConsoleSession session = lockActive(sessionId);
        Long targetId = request.consoleId() == null ? session.getConsoleId() : request.consoleId();
        int controllers = request.controllerCount() == null ? session.getControllerCount() : request.controllerCount();
        boolean moving = !Objects.equals(targetId, session.getConsoleId());
        if (!moving && controllers == session.getControllerCount() && !session.isPaused()) {
            return single(session, null);
        }
        GameConsole target;
        if (moving) {
            target = lockConsole(targetId);
            if (!Objects.equals(target.getBranchId(), session.getBranchId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cihaz aynı şubede olmalı");
            }
            if (sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(target.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Hedef cihazda açık oturum var");
            }
        } else {
            target = consoleRepository.findById(session.getConsoleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cihaz bulunamadı"));
        }
        ConsoleCatalogService.RateContext rates = catalogService.rateContext(session.getBranchId(), session.getMerchantId());
        BigDecimal rate = rates.rate(target, controllers);
        if (rate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hedef cihazın saatlik ücreti tanımlı değil");
        }
        LocalDateTime now = AppTime.nowLocal();
        closeOpenSegment(session.getId(), now);
        session.setConsoleId(target.getId());
        session.setHourlyRate(rate);
        session.setControllerCount(controllers);
        session.setPausedAt(null);
        ConsoleSession saved = sessionRepository.save(session);
        openSegment(saved, target, now);
        return single(saved, rates);
    }

    /** Adds minutes to the planned time; a session without a plan gets the time played so far plus the minutes. */
    @Transactional
    public ConsoleDtos.SessionResponse extend(Long sessionId, ConsoleDtos.ExtendRequest request) {
        ConsoleSession session = lockActive(sessionId);
        int base;
        if (session.getPlannedMinutes() != null) {
            base = session.getPlannedMinutes();
        } else {
            DeviceRentalPricing.Quote quote = DeviceRentalPricing.quote(
                    toPricing(segmentRepository.findBySessionIdAndDeletedFalseOrderByStartedAtAscIdAsc(session.getId())),
                    DeviceRentalPricing.Rules.NONE,
                    AppTime.nowLocal());
            base = (int) (quote.playedSeconds() / 60);
        }
        session.setPlannedMinutes(Math.min(base + request.minutes(), 24 * 60 * 2));
        return single(sessionRepository.save(session), null);
    }

    @Transactional
    public ConsoleDtos.SessionResponse addItem(Long sessionId, ConsoleDtos.AddItemRequest request) {
        ConsoleSession session = lockActive(sessionId);
        String name;
        BigDecimal unitPrice;
        Long productId = null;
        if (request.menuProductId() != null) {
            MenuProduct product = menuProductRepository.findByProductIdAndDeletedFalse(request.menuProductId())
                    .filter(p -> Objects.equals(p.getMerchantId(), session.getMerchantId()))
                    .filter(p -> p.getMenuId() != null && menuRepository.findById(p.getMenuId())
                            .filter(menu -> !menu.isDeleted())
                            .map(menu -> Objects.equals(menu.getBranchId(), session.getBranchId()))
                            .orElse(false))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
            if (product.getPrice() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ürünün fiyatı yok");
            }
            productId = product.getProductId();
            name = product.getName();
            unitPrice = product.getPrice();
        } else {
            name = DeviceMappers.blankToNull(request.name());
            if (name == null || request.unitPrice() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ürün seçin ya da ad ve fiyat girin");
            }
            unitPrice = request.unitPrice();
        }
        BigDecimal price = unitPrice.setScale(2, RoundingMode.HALF_UP);
        itemRepository.save(DeviceSessionItem.builder()
                .merchantId(session.getMerchantId())
                .sessionId(session.getId())
                .menuProductId(productId)
                .name(name.length() > 200 ? name.substring(0, 200) : name)
                .unitPrice(price)
                .quantity(request.quantity())
                .lineTotal(price.multiply(BigDecimal.valueOf(request.quantity())))
                .deleted(false)
                .build());
        return single(session, null);
    }

    @Transactional
    public ConsoleDtos.SessionResponse removeItem(Long sessionId, Long itemId) {
        ConsoleSession session = lockActive(sessionId);
        DeviceSessionItem item = itemRepository.findByIdAndSessionIdAndDeletedFalse(itemId, session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        item.setDeleted(true);
        itemRepository.save(item);
        return single(session, null);
    }

    @Transactional(readOnly = true)
    public ConsoleDtos.SessionQuoteResponse quote(Long sessionId) {
        ConsoleSession session = sessionRepository.findById(sessionId)
                .filter(s -> !s.isDeleted() && Objects.equals(s.getMerchantId(), access.merchantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oturum bulunamadı"));
        access.checkBranch(session.getBranchId());
        if (!session.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Oturum zaten bitmiş");
        }
        ConsoleCatalogService.RateContext rates = catalogService.rateContext(session.getBranchId(), session.getMerchantId());
        DeviceRentalPricing.Quote quote = DeviceRentalPricing.quote(
                toPricing(segmentRepository.findBySessionIdAndDeletedFalseOrderByStartedAtAscIdAsc(session.getId())),
                rates.rules(),
                AppTime.nowLocal());
        BigDecimal items = itemsTotal(itemRepository.findBySessionIdAndDeletedFalseOrderByIdAsc(session.getId()));
        DeviceCustomer customer = session.getCustomerId() == null
                ? null
                : customerRepository.findById(session.getCustomerId()).filter(c -> !c.isDeleted()).orElse(null);
        int available = customer == null ? 0 : customer.getFreeMinutes();
        int usable = Math.min(available, quote.billedMinutes());
        return new ConsoleDtos.SessionQuoteResponse(
                session.getId(),
                quote.playedSeconds(),
                quote.billedMinutes(),
                quote.amount(),
                items,
                quote.amount().add(items),
                customer == null ? null : customer.getId(),
                customer == null ? session.getCustomerName() : customer.getName(),
                customer == null ? null : customer.getBalance(),
                available,
                usable,
                DeviceRentalPricing.valueOfMinutes(quote.amount(), quote.billedMinutes(), usable)
        );
    }

    @Transactional
    public ConsoleDtos.SessionResponse finish(Long sessionId, ConsoleDtos.FinishSessionRequest request) {
        ConsoleSession session = lockActive(sessionId);
        DevicePaymentMethod method = request == null || request.paymentMethod() == null
                ? DevicePaymentMethod.CASH
                : request.paymentMethod();
        if (request != null && request.discountAmount() != null && request.discountPercent() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "İndirimi tutar ya da yüzde olarak girin, ikisi birden olmaz");
        }
        LocalDateTime endedAt = AppTime.nowLocal();
        closeOpenSegment(session.getId(), endedAt);
        ConsoleCatalogService.RateContext rates = catalogService.rateContext(session.getBranchId(), session.getMerchantId());
        DeviceRentalPricing.Quote quote = DeviceRentalPricing.quote(
                toPricing(segmentRepository.findBySessionIdAndDeletedFalseOrderByStartedAtAscIdAsc(session.getId())),
                rates.rules(),
                endedAt);
        BigDecimal timeAmount = quote.amount();
        BigDecimal itemsAmount = itemsTotal(itemRepository.findBySessionIdAndDeletedFalseOrderByIdAsc(session.getId()));
        BigDecimal gross = timeAmount.add(itemsAmount);

        DeviceCustomer customer = session.getCustomerId() == null
                ? null
                : customerRepository.lockOwned(session.getCustomerId(), session.getMerchantId()).orElse(null);
        int loyaltyUsed = 0;
        BigDecimal loyaltyValue = BigDecimal.ZERO.setScale(2);
        if (customer != null && request != null && Boolean.TRUE.equals(request.useLoyaltyMinutes())) {
            loyaltyUsed = Math.min(customer.getFreeMinutes(), quote.billedMinutes());
            loyaltyValue = DeviceRentalPricing.valueOfMinutes(timeAmount, quote.billedMinutes(), loyaltyUsed);
        }
        BigDecimal afterLoyalty = gross.subtract(loyaltyValue);
        BigDecimal manual = manualDiscount(request, afterLoyalty);
        BigDecimal discount = loyaltyValue.add(manual);
        BigDecimal amount = gross.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        if (method == DevicePaymentMethod.BALANCE) {
            if (customer == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bakiyeden ödeme için oturuma müşteri kartı bağlayın");
            }
            if (customer.getBalance().compareTo(amount) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Müşterinin bakiyesi yetersiz");
            }
        }

        session.setEndedAt(endedAt);
        session.setPausedAt(null);
        session.setDurationMinutes(quote.billedMinutes());
        session.setTimeAmount(timeAmount);
        session.setItemsAmount(itemsAmount);
        session.setGrossAmount(gross);
        session.setDiscountAmount(discount.setScale(2, RoundingMode.HALF_UP));
        session.setLoyaltyMinutesUsed(loyaltyUsed == 0 ? null : loyaltyUsed);
        session.setAmount(amount);
        session.setPaymentMethod(method);
        session.setClosedByStaffId(access.staffId());
        ConsoleSession saved = sessionRepository.save(session);

        if (customer != null) {
            settleCustomer(customer, saved, method, loyaltyUsed, rates.setting());
        }
        eventPublisher.publishEvent(new ConsoleSessionFinishedEvent(saved.getId()));
        return single(saved, rates);
    }

    private static BigDecimal manualDiscount(ConsoleDtos.FinishSessionRequest request, BigDecimal base) {
        if (request == null || base.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal discount = BigDecimal.ZERO;
        if (request.discountAmount() != null) {
            discount = request.discountAmount();
        } else if (request.discountPercent() != null) {
            discount = base.multiply(request.discountPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        return discount.min(base).setScale(2, RoundingMode.HALF_UP);
    }

    /** Spends the balance, uses and earns loyalty minutes, and keeps the customer's totals. */
    private void settleCustomer(
            DeviceCustomer customer,
            ConsoleSession session,
            DevicePaymentMethod method,
            int loyaltyUsed,
            ConsoleRateSetting setting
    ) {
        if (method == DevicePaymentMethod.BALANCE && session.getAmount().signum() > 0) {
            customer.setBalance(customer.getBalance().subtract(session.getAmount()));
            customerTxnRepository.save(DeviceCustomerTxn.builder()
                    .merchantId(customer.getMerchantId())
                    .customerId(customer.getId())
                    .txnType(DeviceCustomerTxnType.SPEND)
                    .amount(session.getAmount().negate())
                    .sessionId(session.getId())
                    .paymentMethod(DevicePaymentMethod.BALANCE)
                    .note("Oturum #" + session.getId())
                    .deleted(false)
                    .build());
        }
        customer.setFreeMinutes(customer.getFreeMinutes() - loyaltyUsed);
        int minutes = session.getDurationMinutes() == null ? 0 : session.getDurationMinutes();
        customer.setTotalMinutes(customer.getTotalMinutes() + minutes);
        customer.setTotalSpent(customer.getTotalSpent().add(session.getAmount()));
        if (setting != null && setting.getLoyaltyEveryMinutes() != null && setting.getLoyaltyRewardMinutes() != null) {
            int progress = customer.getLoyaltyProgressMinutes() + Math.max(minutes - loyaltyUsed, 0);
            int every = setting.getLoyaltyEveryMinutes();
            int earned = (progress / every) * setting.getLoyaltyRewardMinutes();
            customer.setLoyaltyProgressMinutes(progress % every);
            customer.setFreeMinutes(customer.getFreeMinutes() + earned);
        }
        customerRepository.save(customer);
    }

    // ---- Reading -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConsoleDtos.SessionResponse> listActive(Long branchId) {
        Branch branch = access.requireBranch(branchId);
        List<ConsoleSession> sessions = sessionRepository
                .findByBranchIdAndMerchantIdAndEndedAtIsNullAndDeletedFalseOrderByStartedAtAscIdAsc(
                        branch.getId(),
                        branch.getMerchantId()
                );
        return responses(sessions, catalogService.rateContext(branch.getId(), branch.getMerchantId()));
    }

    @Transactional(readOnly = true)
    public ConsoleDtos.SessionPageResponse listHistory(Long branchId, int page, int size) {
        Branch branch = access.requireBranch(branchId);
        Page<ConsoleSession> result = sessionRepository
                .findByBranchIdAndMerchantIdAndEndedAtIsNotNullAndDeletedFalseOrderByEndedAtDescIdDesc(
                        branch.getId(),
                        branch.getMerchantId(),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
                );
        return new ConsoleDtos.SessionPageResponse(
                responses(result.getContent(), null),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    /** Menu products of the branch's QR menus that can be added to a session. */
    @Transactional(readOnly = true)
    public List<ConsoleDtos.ProductOption> products(Long branchId) {
        Branch branch = access.requireBranch(branchId);
        List<Long> menuIds = menuRepository.findByBranchIdAndChannelAndDeletedFalse(branch.getId(), MenuChannel.QR).stream()
                .filter(menu -> Objects.equals(menu.getMerchantId(), branch.getMerchantId()))
                .map(Menu::getMenuId)
                .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuProductRepository.findByMenuIdInAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuIds).stream()
                .filter(MenuProduct::isAvailable)
                .filter(p -> p.getPrice() != null)
                .map(p -> new ConsoleDtos.ProductOption(p.getProductId(), p.getMenuId(), p.getName(), p.getPrice(), p.getImageUrl()))
                .toList();
    }

    // ---- Helpers -----------------------------------------------------------------------------------------------

    private GameConsole lockConsole(Long consoleId) {
        GameConsole console = consoleRepository.lockOwned(consoleId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cihaz bulunamadı"));
        access.checkBranch(console.getBranchId());
        return console;
    }

    private ConsoleSession lockActive(Long sessionId) {
        ConsoleSession session = sessionRepository.lockOwned(sessionId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oturum bulunamadı"));
        access.checkBranch(session.getBranchId());
        if (!session.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Oturum zaten bitmiş");
        }
        return session;
    }

    private void openSegment(ConsoleSession session, GameConsole console, LocalDateTime at) {
        segmentRepository.save(DeviceSessionSegment.builder()
                .sessionId(session.getId())
                .consoleId(console.getId())
                .deviceType(console.getDeviceType())
                .hourlyRate(session.getHourlyRate())
                .controllerCount(session.getControllerCount())
                .startedAt(at)
                .deleted(false)
                .build());
    }

    private void closeOpenSegment(Long sessionId, LocalDateTime at) {
        for (DeviceSessionSegment segment : segmentRepository.findBySessionIdAndDeletedFalseOrderByStartedAtAscIdAsc(sessionId)) {
            if (segment.getEndedAt() == null) {
                segment.setEndedAt(at.isBefore(segment.getStartedAt()) ? segment.getStartedAt() : at);
                segmentRepository.save(segment);
            }
        }
    }

    static List<DeviceRentalPricing.Segment> toPricing(List<DeviceSessionSegment> segments) {
        return segments.stream()
                .map(s -> new DeviceRentalPricing.Segment(s.getStartedAt(), s.getEndedAt(), s.getHourlyRate(), s.getDeviceType()))
                .toList();
    }

    private static BigDecimal itemsTotal(List<DeviceSessionItem> items) {
        return items.stream()
                .map(DeviceSessionItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private ConsoleDtos.SessionResponse single(ConsoleSession session, ConsoleCatalogService.RateContext rates) {
        return responses(List.of(session), rates).get(0);
    }

    /** {@code rates} may be null when every session is finished; it is loaded per branch when needed. */
    List<ConsoleDtos.SessionResponse> responses(List<ConsoleSession> sessions, ConsoleCatalogService.RateContext rates) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = sessions.stream().map(ConsoleSession::getId).toList();
        Map<Long, List<DeviceSessionSegment>> segments = segmentRepository
                .findBySessionIdInAndDeletedFalseOrderByStartedAtAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(DeviceSessionSegment::getSessionId));
        Map<Long, List<DeviceSessionItem>> items = itemRepository.findBySessionIdInAndDeletedFalseOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(DeviceSessionItem::getSessionId));
        java.util.Set<Long> consoleIds = new java.util.HashSet<>();
        sessions.forEach(s -> consoleIds.add(s.getConsoleId()));
        segments.values().forEach(list -> list.forEach(seg -> consoleIds.add(seg.getConsoleId())));
        Map<Long, GameConsole> consoles = consoles(consoleIds);
        Map<Long, ConsoleCatalogService.RateContext> contexts = new HashMap<>();
        LocalDateTime now = AppTime.nowLocal();
        return sessions.stream().map(session -> {
            List<DeviceSessionSegment> segs = segments.getOrDefault(session.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(DeviceSessionSegment::getStartedAt).thenComparing(DeviceSessionSegment::getId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            List<DeviceSessionItem> sessionItems = items.getOrDefault(session.getId(), List.of());
            ConsoleCatalogService.RateContext context = !session.isActive()
                    ? null
                    : contexts.computeIfAbsent(session.getBranchId(), branchId ->
                            rates != null ? rates : catalogService.rateContext(branchId, session.getMerchantId()));
            return toResponse(session, segs, sessionItems, consoles, context, now);
        }).toList();
    }

    private Map<Long, GameConsole> consoles(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return consoleRepository.findByIdIn(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(GameConsole::getId, Function.identity(), (a, b) -> a));
    }

    private static ConsoleDtos.SessionResponse toResponse(
            ConsoleSession session,
            List<DeviceSessionSegment> segments,
            List<DeviceSessionItem> items,
            Map<Long, GameConsole> consoles,
            ConsoleCatalogService.RateContext rates,
            LocalDateTime now
    ) {
        long playedSeconds;
        int minutes;
        BigDecimal timeAmount;
        BigDecimal itemsAmount;
        BigDecimal gross;
        BigDecimal discount;
        BigDecimal amount;
        if (session.isActive()) {
            DeviceRentalPricing.Quote quote = DeviceRentalPricing.quote(
                    toPricing(segments),
                    rates == null ? DeviceRentalPricing.Rules.NONE : rates.rules(),
                    now);
            playedSeconds = quote.playedSeconds();
            minutes = quote.billedMinutes();
            timeAmount = quote.amount();
            itemsAmount = itemsTotal(items);
            gross = timeAmount.add(itemsAmount);
            discount = BigDecimal.ZERO.setScale(2);
            amount = gross;
        } else {
            playedSeconds = DeviceRentalPricing.quote(toPricing(segments), DeviceRentalPricing.Rules.NONE, now).playedSeconds();
            minutes = session.getDurationMinutes() == null ? 0 : session.getDurationMinutes();
            amount = session.getAmount();
            timeAmount = session.getTimeAmount() != null ? session.getTimeAmount() : session.getAmount();
            itemsAmount = session.getItemsAmount() != null ? session.getItemsAmount() : BigDecimal.ZERO.setScale(2);
            gross = session.getGrossAmount() != null ? session.getGrossAmount() : session.getAmount();
            discount = session.getDiscountAmount() != null ? session.getDiscountAmount() : BigDecimal.ZERO.setScale(2);
        }
        GameConsole console = consoles.get(session.getConsoleId());
        return new ConsoleDtos.SessionResponse(
                session.getId(),
                session.getBranchId(),
                session.getConsoleId(),
                console == null ? null : console.getName(),
                console == null ? null : console.getDeviceType(),
                session.getCustomerId(),
                session.getCustomerName(),
                session.getNote(),
                session.getHourlyRate(),
                session.getControllerCount(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getPausedAt(),
                session.isPaused(),
                session.getPlannedMinutes(),
                playedSeconds,
                minutes,
                timeAmount,
                itemsAmount,
                gross,
                discount,
                session.getLoyaltyMinutesUsed(),
                amount,
                session.getPaymentMethod(),
                session.isActive(),
                session.getReservationId(),
                items.stream().map(DeviceMappers::item).toList(),
                segments.stream().map(seg -> {
                    GameConsole segConsole = consoles.get(seg.getConsoleId());
                    return new ConsoleDtos.SessionSegmentResponse(
                            seg.getConsoleId(),
                            segConsole == null ? null : segConsole.getName(),
                            seg.getDeviceType(),
                            seg.getHourlyRate(),
                            seg.getControllerCount(),
                            seg.getStartedAt(),
                            seg.getEndedAt()
                    );
                }).toList()
        );
    }
}
