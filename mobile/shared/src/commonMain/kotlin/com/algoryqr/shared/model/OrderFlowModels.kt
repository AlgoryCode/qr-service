package com.algoryqr.shared.model

import kotlinx.serialization.Serializable

/** Mirrors qr-service `OrderFlowChannel`. */
@Serializable
enum class FlowChannel(val label: String) { TABLE("Masa"), DELIVERY("Paket") }

/** Mirrors qr-service `OrderFlowStepType`. */
@Serializable
enum class FlowStep { ORDER_APPROVAL, KITCHEN_PREP, PASS_CHECK, PACKING, TABLE_SERVICE, DELIVERY }

/** `OrderFlowDtos.BranchFlowResponse`: the flow a branch runs, also derived for branches without one. */
@Serializable
data class BranchFlow(
    val branchId: Long? = null,
    val flowId: Long? = null,
    val flowName: String? = null,
    val version: Int = 0,
    val steps: Map<FlowChannel, List<FlowStep>> = emptyMap(),
) {
    fun has(channel: FlowChannel, step: FlowStep): Boolean = steps[channel].orEmpty().contains(step)

    /** QR orders wait for a waiter's approval before reaching the kitchen and the bill. */
    val tableApproval: Boolean get() = has(FlowChannel.TABLE, FlowStep.ORDER_APPROVAL)

    /** Table orders go through the kitchen screen; otherwise the waiter serves them straight away. */
    val tableKitchen: Boolean get() = has(FlowChannel.TABLE, FlowStep.KITCHEN_PREP)

    val usesKitchen: Boolean get() = steps.values.any { FlowStep.KITCHEN_PREP in it }

    val usesCourier: Boolean get() = has(FlowChannel.DELIVERY, FlowStep.DELIVERY)
}
