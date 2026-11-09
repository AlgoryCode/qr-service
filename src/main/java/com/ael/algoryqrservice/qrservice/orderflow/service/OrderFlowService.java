package com.ael.algoryqrservice.qrservice.orderflow.service;

import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.OrderFlow;
import com.ael.algoryqrservice.qrservice.model.OrderFlowRequest;
import com.ael.algoryqrservice.qrservice.model.OrderFlowStep;
import com.ael.algoryqrservice.qrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowChannel;
import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowStepType;
import com.ael.algoryqrservice.qrservice.order.service.KitchenCloseGuard;
import com.ael.algoryqrservice.qrservice.orderflow.OrderFlowTemplate;
import com.ael.algoryqrservice.qrservice.orderflow.dto.OrderFlowDtos;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.repository.OrderFlowRepository;
import com.ael.algoryqrservice.qrservice.repository.OrderFlowRequestRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * İşletmenin sipariş akışlarını yönetir. Statüler sabittir; akış yalnızca hangi adımın kimde olduğunu belirler.
 * Şubeye atanan akış, mevcut sipariş kurallarının okuduğu {@code kitchenEnabled} / {@code courierEnabled}
 * bayraklarını da günceller, böylece eski uçlar akışla tutarlı davranır.
 */
@Service
@RequiredArgsConstructor
public class OrderFlowService {

    private final OrderFlowRepository orderFlowRepository;
    private final OrderFlowRequestRepository orderFlowRequestRepository;
    private final BranchRepository branchRepository;
    private final BranchService branchService;
    private final KitchenCloseGuard kitchenCloseGuard;
    private final SecurityUtils securityUtils;

    public OrderFlowDtos.CatalogResponse catalog() {
        List<OrderFlowDtos.StatusInfo> statuses = List.of(
                new OrderFlowDtos.StatusInfo(MenuOrderStatus.SUBMITTED, "Onay bekliyor"),
                new OrderFlowDtos.StatusInfo(MenuOrderStatus.CONFIRMED, "Onaylandı"),
                new OrderFlowDtos.StatusInfo(MenuOrderStatus.PREPARING, "Hazırlanıyor"),
                new OrderFlowDtos.StatusInfo(MenuOrderStatus.READY, "Hazır"),
                new OrderFlowDtos.StatusInfo(MenuOrderStatus.SERVED, "Servis edildi"),
                new OrderFlowDtos.StatusInfo(MenuOrderStatus.PAID, "Ödendi"),
                new OrderFlowDtos.StatusInfo(MenuOrderStatus.REJECTED, "Reddedildi"),
                new OrderFlowDtos.StatusInfo(MenuOrderStatus.CANCELLED, "İptal")
        );
        List<OrderFlowDtos.ChannelInfo> channels = Arrays.stream(OrderFlowChannel.values())
                .map(channel -> new OrderFlowDtos.ChannelInfo(channel, channel.getLabel()))
                .toList();
        List<OrderFlowDtos.StepInfo> steps = Arrays.stream(OrderFlowStepType.values())
                .map(step -> new OrderFlowDtos.StepInfo(
                        step,
                        step.getLabel(),
                        step.getRole(),
                        step.getDescription(),
                        step.getChannels().stream().sorted().toList(),
                        step.isAvailable()
                ))
                .toList();
        List<OrderFlowDtos.TemplateInfo> templates = Arrays.stream(OrderFlowTemplate.values())
                .map(template -> new OrderFlowDtos.TemplateInfo(
                        template.name(),
                        template.getLabel(),
                        template.getDescription(),
                        stepsByChannel(template::stepsFor)
                ))
                .toList();
        return new OrderFlowDtos.CatalogResponse(statuses, channels, steps, templates);
    }

    @Transactional(readOnly = true)
    public List<OrderFlowDtos.FlowResponse> list() {
        Long merchantId = ownerMerchantId();
        Map<Long, List<OrderFlowDtos.BranchRef>> branchesByFlow = branchRepository.findByMerchantIdAndDeletedFalse(merchantId)
                .stream()
                .filter(branch -> branch.getOrderFlowId() != null)
                .collect(Collectors.groupingBy(
                        Branch::getOrderFlowId,
                        Collectors.mapping(branch -> new OrderFlowDtos.BranchRef(branch.getId(), branch.getName()), Collectors.toList())
                ));
        return orderFlowRepository.findByMerchantIdAndDeletedFalseOrderByIdAsc(merchantId).stream()
                .map(flow -> toResponse(flow, branchesByFlow.getOrDefault(flow.getId(), List.of())))
                .toList();
    }

    @Transactional
    public OrderFlowDtos.FlowResponse create(OrderFlowDtos.SaveFlowRequest request) {
        Long merchantId = ownerMerchantId();
        OrderFlow flow = OrderFlow.builder()
                .merchantId(merchantId)
                .name(request.name().trim())
                .templateCode(templateCode(request.templateCode()))
                .build();
        replaceSteps(flow, normalize(request.steps()));
        return toResponse(orderFlowRepository.save(flow), List.of());
    }

    @Transactional
    public OrderFlowDtos.FlowResponse update(Long flowId, OrderFlowDtos.SaveFlowRequest request) {
        OrderFlow flow = requireOwned(flowId);
        Map<OrderFlowChannel, List<OrderFlowStepType>> steps = normalize(request.steps());
        List<Branch> branches = branchRepository.findByOrderFlowIdAndDeletedFalse(flowId);
        // Önce bütün şubeler kontrol edilir; biri reddederse hiçbir şey değişmez.
        branches.forEach(branch -> guardTransition(branch, steps));

        flow.setName(request.name().trim());
        flow.setTemplateCode(templateCode(request.templateCode()));
        flow.setVersion(flow.getVersion() + 1);
        flow.getSteps().clear();
        orderFlowRepository.saveAndFlush(flow);
        replaceSteps(flow, steps);
        OrderFlow saved = orderFlowRepository.save(flow);
        branches.forEach(branch -> applyToBranch(branch, steps));
        return toResponse(saved, branches.stream().map(this::branchRef).toList());
    }

    @Transactional
    public void delete(Long flowId) {
        OrderFlow flow = requireOwned(flowId);
        if (!branchRepository.findByOrderFlowIdAndDeletedFalse(flowId).isEmpty()) {
            throw new BadRequestException("Bu akış şubelere atanmış; önce şubeleri başka bir akışa geçirin");
        }
        flow.setDeleted(true);
        orderFlowRepository.save(flow);
    }

    /** Akışı şubeye atar. Mutfak adımı kalkıyorsa şubede mutfakta bekleyen sipariş olmamalıdır. */
    @Transactional
    public OrderFlowDtos.BranchFlowResponse assign(Long flowId, Long branchId) {
        OrderFlow flow = requireOwned(flowId);
        Branch branch = branchService.requireOwned(branchId);
        Map<OrderFlowChannel, List<OrderFlowStepType>> steps = stepsOf(flow);
        guardTransition(branch, steps);
        branch.setOrderFlowId(flow.getId());
        applyToBranch(branch, steps);
        return toBranchResponse(branch, flow);
    }

    /** Şubeyi akıştan çıkarır; şube son bayraklarıyla eski davranışa döner. */
    @Transactional
    public OrderFlowDtos.BranchFlowResponse unassign(Long branchId) {
        ownerMerchantId();
        Branch branch = branchService.requireOwned(branchId);
        branch.setOrderFlowId(null);
        branchRepository.save(branch);
        return toBranchResponse(branch, null);
    }

    /** Şubenin oturumdaki işletmeye ait olduğunu doğrular. */
    @Transactional(readOnly = true)
    public Long ownedBranchId(Long branchId) {
        ownerMerchantId();
        return branchService.requireOwned(branchId).getId();
    }

    @Transactional(readOnly = true)
    public OrderFlowDtos.BranchFlowResponse branchFlow(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new NotFoundException("Şube bulunamadı"));
        OrderFlow flow = branch.getOrderFlowId() == null
                ? null
                : orderFlowRepository.findByIdAndDeletedFalse(branch.getOrderFlowId()).orElse(null);
        return toBranchResponse(branch, flow);
    }

    @Transactional
    public OrderFlowDtos.RequestResponse createRequest(OrderFlowDtos.CreateRequestRequest request) {
        Long merchantId = ownerMerchantId();
        if (request.flowId() != null) {
            requireOwned(request.flowId());
        }
        OrderFlowRequest saved = orderFlowRequestRepository.save(OrderFlowRequest.builder()
                .merchantId(merchantId)
                .flowId(request.flowId())
                .title(request.title().trim())
                .description(request.description() == null ? null : request.description().trim())
                .build());
        return toRequestResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderFlowDtos.RequestResponse> listRequests() {
        return orderFlowRequestRepository.findByMerchantIdAndDeletedFalseOrderByIdDesc(ownerMerchantId())
                .stream()
                .map(this::toRequestResponse)
                .toList();
    }

    /**
     * Kanal başına adımları doğrular ve katalog sırasına dizer: adım o kanalda kullanılabilmeli ve desteklenmeli;
     * dolu bir masa kanalı servisle, dolu bir paket kanalı kurye teslimatıyla bitmelidir.
     */
    Map<OrderFlowChannel, List<OrderFlowStepType>> normalize(Map<OrderFlowChannel, List<OrderFlowStepType>> requested) {
        Map<OrderFlowChannel, List<OrderFlowStepType>> result = new EnumMap<>(OrderFlowChannel.class);
        for (OrderFlowChannel channel : OrderFlowChannel.values()) {
            List<OrderFlowStepType> raw = requested == null ? null : requested.get(channel);
            List<OrderFlowStepType> steps = raw == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(raw.stream().filter(Objects::nonNull).toList()));
            for (OrderFlowStepType step : steps) {
                if (!step.getChannels().contains(channel)) {
                    throw new BadRequestException("\"" + step.getLabel() + "\" adımı " + channel.getLabel() + " kanalında kullanılamaz");
                }
                if (!step.isAvailable()) {
                    throw new BadRequestException("\"" + step.getLabel() + "\" adımı henüz desteklenmiyor; talep açabilirsiniz");
                }
            }
            steps.sort(Comparator.naturalOrder());
            if (channel == OrderFlowChannel.TABLE && !steps.isEmpty() && !steps.contains(OrderFlowStepType.TABLE_SERVICE)) {
                throw new BadRequestException("Masa akışı \"Masaya servis\" adımıyla bitmelidir");
            }
            if (channel == OrderFlowChannel.DELIVERY && !steps.isEmpty() && !steps.contains(OrderFlowStepType.DELIVERY)) {
                throw new BadRequestException("Paket akışı \"Kurye teslimatı\" adımıyla bitmelidir");
            }
            result.put(channel, List.copyOf(steps));
        }
        if (result.values().stream().allMatch(List::isEmpty)) {
            throw new BadRequestException("Akışta en az bir kanal için adım olmalıdır");
        }
        return result;
    }

    private void guardTransition(Branch branch, Map<OrderFlowChannel, List<OrderFlowStepType>> steps) {
        if (branch.isKitchenEnabled() && !usesKitchen(steps)) {
            kitchenCloseGuard.requireNoActiveOrders(branch);
        }
    }

    private void applyToBranch(Branch branch, Map<OrderFlowChannel, List<OrderFlowStepType>> steps) {
        branch.setKitchenEnabled(usesKitchen(steps));
        branch.setCourierEnabled(steps.getOrDefault(OrderFlowChannel.DELIVERY, List.of()).contains(OrderFlowStepType.DELIVERY));
        branchRepository.save(branch);
    }

    private static boolean usesKitchen(Map<OrderFlowChannel, List<OrderFlowStepType>> steps) {
        return steps.values().stream().anyMatch(list -> list.contains(OrderFlowStepType.KITCHEN_PREP));
    }

    private void replaceSteps(OrderFlow flow, Map<OrderFlowChannel, List<OrderFlowStepType>> steps) {
        steps.forEach((channel, list) -> {
            for (int index = 0; index < list.size(); index++) {
                flow.getSteps().add(OrderFlowStep.builder()
                        .flow(flow)
                        .channel(channel)
                        .step(list.get(index))
                        .sortOrder(index)
                        .build());
            }
        });
    }

    /**
     * Akışlar yalnızca işletme sahibi tarafından yönetilir. Personel token'ı da bir işletme kimliği taşıdığı için
     * ayrıca personel oturumu olmadığı doğrulanır.
     */
    private Long ownerMerchantId() {
        if (securityUtils.findCurrentStaffId().isPresent()) {
            throw new ForbiddenException("Sipariş akışlarını yalnızca işletme sahibi yönetebilir");
        }
        return securityUtils.getCurrentMerchantId();
    }

    private OrderFlow requireOwned(Long flowId) {
        return orderFlowRepository.findByIdAndMerchantIdAndDeletedFalse(flowId, ownerMerchantId())
                .orElseThrow(() -> new NotFoundException("Sipariş akışı bulunamadı"));
    }

    private static String templateCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return OrderFlowTemplate.valueOf(code.trim()).name();
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException("Bilinmeyen şablon: " + code);
        }
    }

    private static Map<OrderFlowChannel, List<OrderFlowStepType>> stepsOf(OrderFlow flow) {
        return stepsByChannel(channel -> flow.getSteps().stream()
                .filter(step -> step.getChannel() == channel)
                .sorted(Comparator.comparingInt(OrderFlowStep::getSortOrder))
                .map(OrderFlowStep::getStep)
                .toList());
    }

    private static Map<OrderFlowChannel, List<OrderFlowStepType>> stepsByChannel(
            java.util.function.Function<OrderFlowChannel, List<OrderFlowStepType>> source
    ) {
        Map<OrderFlowChannel, List<OrderFlowStepType>> map = new EnumMap<>(OrderFlowChannel.class);
        for (OrderFlowChannel channel : OrderFlowChannel.values()) {
            map.put(channel, source.apply(channel));
        }
        return map;
    }

    /** Akışı olmayan şube için bugünkü bayraklardan türetilen akış; personel uygulaması tek bir modelle çalışır. */
    private static Map<OrderFlowChannel, List<OrderFlowStepType>> legacySteps(Branch branch) {
        List<OrderFlowStepType> table = branch.isKitchenEnabled()
                ? List.of(OrderFlowStepType.KITCHEN_PREP, OrderFlowStepType.TABLE_SERVICE)
                : List.of(OrderFlowStepType.TABLE_SERVICE);
        List<OrderFlowStepType> delivery = !branch.isCourierEnabled()
                ? List.of()
                : branch.isKitchenEnabled()
                        ? List.of(OrderFlowStepType.KITCHEN_PREP, OrderFlowStepType.DELIVERY)
                        : List.of(OrderFlowStepType.DELIVERY);
        Map<OrderFlowChannel, List<OrderFlowStepType>> map = new EnumMap<>(OrderFlowChannel.class);
        map.put(OrderFlowChannel.TABLE, table);
        map.put(OrderFlowChannel.DELIVERY, delivery);
        return map;
    }

    private OrderFlowDtos.FlowResponse toResponse(OrderFlow flow, List<OrderFlowDtos.BranchRef> branches) {
        return new OrderFlowDtos.FlowResponse(
                flow.getId(),
                flow.getName(),
                flow.getTemplateCode(),
                flow.getVersion(),
                stepsOf(flow),
                branches,
                flow.getUpdatedAt()
        );
    }

    private OrderFlowDtos.BranchFlowResponse toBranchResponse(Branch branch, OrderFlow flow) {
        return flow == null
                ? new OrderFlowDtos.BranchFlowResponse(branch.getId(), null, null, 0, legacySteps(branch))
                : new OrderFlowDtos.BranchFlowResponse(branch.getId(), flow.getId(), flow.getName(), flow.getVersion(), stepsOf(flow));
    }

    private OrderFlowDtos.BranchRef branchRef(Branch branch) {
        return new OrderFlowDtos.BranchRef(branch.getId(), branch.getName());
    }

    private OrderFlowDtos.RequestResponse toRequestResponse(OrderFlowRequest request) {
        return new OrderFlowDtos.RequestResponse(
                request.getId(),
                request.getFlowId(),
                request.getTitle(),
                request.getDescription(),
                request.getStatus(),
                request.getCreatedAt()
        );
    }
}
