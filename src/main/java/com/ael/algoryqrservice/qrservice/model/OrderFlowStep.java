package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowChannel;
import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowStepType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "tbl_order_flow_step")
@Data
@EqualsAndHashCode(exclude = "flow")
@ToString(exclude = "flow")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderFlowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_id", nullable = false)
    private OrderFlow flow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderFlowChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OrderFlowStepType step;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
