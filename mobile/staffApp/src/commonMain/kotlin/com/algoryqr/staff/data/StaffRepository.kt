package com.algoryqr.staff.data

import com.algoryqr.shared.model.AddTipRequest
import com.algoryqr.shared.model.Bill
import com.algoryqr.shared.model.BranchFlow
import com.algoryqr.shared.model.CancelOrderRequest
import com.algoryqr.shared.model.CatalogResponse
import com.algoryqr.shared.model.ChangePaymentMethodRequest
import com.algoryqr.shared.model.CloseBillRequest
import com.algoryqr.shared.model.DeliveryOrder
import com.algoryqr.shared.model.Order
import com.algoryqr.shared.model.PayAmountRequest
import com.algoryqr.shared.model.PayItemsRequest
import com.algoryqr.shared.model.PayShareRequest
import com.algoryqr.shared.model.SplitPreview
import com.algoryqr.shared.model.TableSummary
import com.algoryqr.shared.model.UpdateBillItemQuantityRequest
import com.algoryqr.shared.model.WaiterCreateOrderRequest
import com.algoryqr.shared.network.ApiException
import com.algoryqr.shared.network.ApiJson
import com.algoryqr.shared.network.jsonBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable

@Serializable
data class Shift(
    val id: Long,
    val openedAt: String? = null,
    val status: String? = null,
    val revenue: Double = 0.0,
    val orderCount: Long = 0,
)

@Serializable
data class CommissionRecord(
    val id: Long,
    val recordType: String? = null,
    val amount: Double = 0.0,
    val tableName: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class TodayCommission(
    val totalAmount: Double = 0.0,
    val currency: String? = null,
    val recordCount: Int = 0,
    val records: List<CommissionRecord> = emptyList(),
)

/** Staff endpoints of qr-service (`/staff/...`); the backend scopes every call to the signed-in staff's branch. */
class StaffRepository(private val client: HttpClient) {

    /** The order flow of the signed-in staff's branch (`/staff/order-flow`). */
    suspend fun orderFlow(): BranchFlow = client.get("api/v1/staff/order-flow").body()

    // Waiter
    suspend fun tables(merchantId: Long, branchId: Long): List<TableSummary> =
        client.get("api/v1/merchant/$merchantId/branches/$branchId/tables").body()

    suspend fun pendingOrders(): List<Order> = client.get("api/v1/staff/orders/pending").body()

    suspend fun readyOrders(): List<Order> = client.get("api/v1/staff/orders/ready").body()

    suspend fun tableOrders(tableId: Long): List<Order> = client.get("api/v1/staff/orders/tables/$tableId/today").body()

    /** Today's orders of the branch; the order screen derives "Sık satılanlar" from them. */
    suspend fun todayOrders(): List<Order> = client.get("api/v1/staff/orders/today").body()

    suspend fun confirm(orderId: Long): Order = client.post("api/v1/staff/orders/$orderId/confirm").body()

    suspend fun reject(orderId: Long, request: CancelOrderRequest): Order =
        client.post("api/v1/staff/orders/$orderId/reject") { jsonBody(request) }.body()

    /** Cancels an order that is still open (awaiting approval, in the kitchen or ready); its items leave the bill. */
    suspend fun cancel(orderId: Long, request: CancelOrderRequest): Order =
        client.post("api/v1/staff/orders/$orderId/cancel") { jsonBody(request) }.body()

    /** Sends an order that went straight onto the bill to the kitchen after all. */
    suspend fun sendToKitchen(orderId: Long): Order = client.post("api/v1/staff/orders/$orderId/send-to-kitchen").body()

    suspend fun served(orderId: Long): Order = client.post("api/v1/staff/orders/$orderId/served").body()

    suspend fun catalog(tableId: Long?): CatalogResponse =
        client.get("api/v1/staff/orders/catalog") { tableId?.let { parameter("tableId", it) } }.body()

    suspend fun createOrder(request: WaiterCreateOrderRequest): Order =
        client.post("api/v1/staff/orders") { jsonBody(request) }.body()

    /** `null` when the table has no bill yet. */
    suspend fun tableBill(tableId: Long): Bill? = try {
        client.get("api/v1/staff/tables/$tableId/bill").bodyAsText().takeIf { it.isNotBlank() }?.let { ApiJson.decodeFromString<Bill>(it) }
    } catch (error: ApiException) {
        if (error.status == 404) null else throw error
    }

    suspend fun bill(billId: Long): Bill = client.get("api/v1/staff/bills/$billId").body()

    suspend fun updateItemQuantity(billId: Long, itemId: Long, quantity: Int): Bill =
        client.patch("api/v1/staff/bills/$billId/items/$itemId") { jsonBody(UpdateBillItemQuantityRequest(quantity)) }.body()

    suspend fun payItems(billId: Long, request: PayItemsRequest): Bill =
        client.post("api/v1/staff/bills/$billId/payments/items") { jsonBody(request) }.body()

    suspend fun payAmount(billId: Long, request: PayAmountRequest): Bill =
        client.post("api/v1/staff/bills/$billId/payments/amount") { jsonBody(request) }.body()

    suspend fun changePaymentMethod(billId: Long, request: ChangePaymentMethodRequest): Bill =
        client.patch("api/v1/staff/bills/$billId/payments/method") { jsonBody(request) }.body()

    suspend fun addTip(billId: Long, request: AddTipRequest): Bill =
        client.post("api/v1/staff/bills/$billId/tip") { jsonBody(request) }.body()

    suspend fun payShare(billId: Long, request: PayShareRequest): Bill =
        client.post("api/v1/staff/bills/$billId/payments/share") { jsonBody(request) }.body()

    suspend fun splitPreview(billId: Long, personCount: Int): SplitPreview =
        client.get("api/v1/staff/bills/$billId/split") { parameter("personCount", personCount) }.body()

    suspend fun closeBill(billId: Long, request: CloseBillRequest): Bill =
        client.post("api/v1/staff/bills/$billId/close") { jsonBody(request) }.body()

    /** Cancels an unpaid bill together with all its orders; qr-service refuses once any payment was taken. */
    suspend fun cancelBill(billId: Long, request: CancelOrderRequest): Bill =
        client.post("api/v1/staff/bills/$billId/cancel") { jsonBody(request) }.body()

    /** qr-service answers 200 with an empty body when no shift is open. */
    suspend fun currentShift(): Shift? =
        client.get("api/v1/staff/ops/shifts/current").bodyAsText().takeIf { it.isNotBlank() && it != "null" }?.let { ApiJson.decodeFromString<Shift>(it) }

    suspend fun todayCommission(): TodayCommission = client.get("api/v1/staff/commissions/today").body()

    // Kitchen
    suspend fun kitchenOrders(): List<Order> = client.get("api/v1/staff/kitchen/orders").body()

    suspend fun kitchenPreparing(orderId: Long): Order = client.post("api/v1/staff/kitchen/orders/$orderId/preparing").body()

    suspend fun kitchenReady(orderId: Long): Order = client.post("api/v1/staff/kitchen/orders/$orderId/ready").body()

    // Courier
    suspend fun courierOrders(): List<DeliveryOrder> = client.get("api/v1/staff/courier/orders").body()

    suspend fun dispatch(orderId: Long): DeliveryOrder = client.post("api/v1/staff/courier/orders/$orderId/dispatch").body()

    suspend fun deliver(orderId: Long): DeliveryOrder = client.post("api/v1/staff/courier/orders/$orderId/deliver").body()
}
