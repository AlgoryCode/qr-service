package com.ael.algoryqrservice.qrservice.orderflow.dto;

import com.ael.algoryqrservice.qrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowChannel;
import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowRequestStatus;
import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowStepType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class OrderFlowDtos {

    private OrderFlowDtos() {
    }

    /** Sabit sipariş statüsü; akışlar bunları değiştirmez, yalnızca kimin ilerlettiğini belirler. */
    public record StatusInfo(MenuOrderStatus code, String label) {
    }

    public record StepInfo(
            OrderFlowStepType code,
            String label,
            String role,
            String description,
            List<OrderFlowChannel> channels,
            boolean available
    ) {
    }

    public record ChannelInfo(OrderFlowChannel code, String label) {
    }

    public record TemplateInfo(String code, String label, String description, Map<OrderFlowChannel, List<OrderFlowStepType>> steps) {
    }

    public record CatalogResponse(
            List<StatusInfo> statuses,
            List<ChannelInfo> channels,
            List<StepInfo> steps,
            List<TemplateInfo> templates
    ) {
    }

    public record BranchRef(Long id, String name) {
    }

    public record FlowResponse(
            Long id,
            String name,
            String templateCode,
            int version,
            Map<OrderFlowChannel, List<OrderFlowStepType>> steps,
            List<BranchRef> branches,
            LocalDateTime updatedAt
    ) {
    }

    public record SaveFlowRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 40) String templateCode,
            @NotNull Map<OrderFlowChannel, List<OrderFlowStepType>> steps
    ) {
    }

    /** Personel uygulamasının ekranlarını şekillendirdiği, şubenin geçerli akışı. */
    public record BranchFlowResponse(
            Long branchId,
            Long flowId,
            String flowName,
            int version,
            Map<OrderFlowChannel, List<OrderFlowStepType>> steps
    ) {
    }

    public record CreateRequestRequest(
            Long flowId,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String description
    ) {
    }

    public record RequestResponse(
            Long id,
            Long flowId,
            String title,
            String description,
            OrderFlowRequestStatus status,
            LocalDateTime createdAt
    ) {
    }
}
