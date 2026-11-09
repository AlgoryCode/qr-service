package com.algoryqr.business.data

import com.algoryqr.shared.model.BranchFlow
import com.algoryqr.shared.model.FlowChannel
import com.algoryqr.shared.model.FlowStep
import com.algoryqr.shared.network.jsonBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import kotlinx.serialization.Serializable

@Serializable
data class FlowStatusInfo(val code: String, val label: String)

@Serializable
data class FlowStepInfo(
    val code: FlowStep,
    val label: String,
    val role: String,
    val description: String,
    val channels: List<FlowChannel> = emptyList(),
    val available: Boolean = true,
)

@Serializable
data class FlowTemplateInfo(
    val code: String,
    val label: String,
    val description: String,
    val steps: Map<FlowChannel, List<FlowStep>> = emptyMap(),
)

/** `OrderFlowDtos.CatalogResponse`: fixed statuses, the step catalogue and the ready-made templates. */
@Serializable
data class FlowCatalog(
    val statuses: List<FlowStatusInfo> = emptyList(),
    val steps: List<FlowStepInfo> = emptyList(),
    val templates: List<FlowTemplateInfo> = emptyList(),
) {
    fun step(code: FlowStep): FlowStepInfo? = steps.firstOrNull { it.code == code }
}

@Serializable
data class FlowBranchRef(val id: Long, val name: String = "")

@Serializable
data class OrderFlow(
    val id: Long,
    val name: String,
    val templateCode: String? = null,
    val version: Int = 1,
    val steps: Map<FlowChannel, List<FlowStep>> = emptyMap(),
    val branches: List<FlowBranchRef> = emptyList(),
)

@Serializable
data class SaveFlowRequest(val name: String, val templateCode: String?, val steps: Map<FlowChannel, List<FlowStep>>)

@Serializable
data class FlowRequest(
    val id: Long,
    val flowId: Long? = null,
    val title: String = "",
    val description: String? = null,
    val status: String = "OPEN",
    val createdAt: String? = null,
) {
    val statusLabel: String
        get() = when (status) {
            "IN_REVIEW" -> "İnceleniyor"
            "DONE" -> "Eklendi"
            "REJECTED" -> "Reddedildi"
            else -> "Alındı"
        }
}

@Serializable
data class CreateFlowRequest(val flowId: Long?, val title: String, val description: String?)

/** Owner endpoints for building order flows and assigning them to branches (`/order-flows`). */
class OrderFlowRepository(private val client: HttpClient) {

    suspend fun catalog(): FlowCatalog = client.get("api/v1/order-flows/catalog").body()

    suspend fun flows(): List<OrderFlow> = client.get("api/v1/order-flows").body()

    suspend fun branchFlow(branchId: Long): BranchFlow = client.get("api/v1/order-flows/branches/$branchId").body()

    suspend fun create(request: SaveFlowRequest): OrderFlow = client.post("api/v1/order-flows") { jsonBody(request) }.body()

    suspend fun update(flowId: Long, request: SaveFlowRequest): OrderFlow = client.put("api/v1/order-flows/$flowId") { jsonBody(request) }.body()

    suspend fun delete(flowId: Long) {
        client.delete("api/v1/order-flows/$flowId")
    }

    suspend fun assign(flowId: Long, branchId: Long): BranchFlow = client.put("api/v1/order-flows/$flowId/branches/$branchId").body()

    suspend fun requests(): List<FlowRequest> = client.get("api/v1/order-flows/requests").body()

    suspend fun createRequest(request: CreateFlowRequest): FlowRequest = client.post("api/v1/order-flows/requests") { jsonBody(request) }.body()
}
