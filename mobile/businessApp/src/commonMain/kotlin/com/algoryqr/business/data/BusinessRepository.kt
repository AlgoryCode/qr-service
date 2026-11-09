package com.algoryqr.business.data

import com.algoryqr.shared.model.CancelOrderRequest
import com.algoryqr.shared.model.Order
import com.algoryqr.shared.model.TableSummary
import com.algoryqr.shared.network.jsonBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import kotlinx.datetime.LocalDate

/** Owner endpoints of qr-service; every call runs with the merchant's access token. */
private const val PRODUCT_PAGE_SIZE = 100
private const val MAX_PRODUCT_PAGES = 20

class BusinessRepository(private val client: HttpClient) {

    suspend fun branches(): List<Branch> = client.get("api/v1/branches").body<BranchListResponse>().content

    suspend fun summary(branchId: Long, menuId: Long?, from: LocalDate, to: LocalDate): SummaryAnalytics =
        client.get("api/v1/analytics/branch/$branchId/summary") {
            menuId?.let { parameter("menuId", it) }
            parameter("from", from.toString())
            parameter("to", to.toString())
        }.body()

    suspend fun revenueReport(branchId: Long, menuId: Long?, from: LocalDate, to: LocalDate): RevenueReport =
        client.get("api/v1/analytics/branch/$branchId/revenue") {
            menuId?.let { parameter("menuId", it) }
            parameter("from", from.toString())
            parameter("to", to.toString())
        }.body()

    suspend fun staffReport(branchId: Long, menuId: Long?, from: LocalDate, to: LocalDate): StaffPerformanceReport =
        client.get("api/v1/analytics/branch/$branchId/waiter-performance") {
            menuId?.let { parameter("menuId", it) }
            parameter("from", from.toString())
            parameter("to", to.toString())
        }.body()

    /**
     * Today's and yesterday's orders of a menu, newest first; filtering happens on the device. Without [days]
     * qr-service returns the menu's whole history, which a busy branch can't load on a phone.
     */
    suspend fun orders(menuId: Long, days: Int = 2): List<Order> =
        client.get("api/v1/staff/panel/menu/$menuId/orders") {
            parameter("status", "ALL")
            parameter("days", days)
        }.body()

    suspend fun cancelOrder(menuId: Long, orderId: Long, request: CancelOrderRequest): Order =
        client.post("api/v1/staff/panel/menu/$menuId/orders/$orderId/cancel") { jsonBody(request) }.body()

    suspend fun tables(merchantId: Long, branchId: Long): List<TableSummary> =
        client.get("api/v1/merchant/$merchantId/branches/$branchId/tables").body()

    suspend fun staff(): List<StaffMember> = client.get("api/v1/staff/panel/staff").body<StaffListResponse>().staff

    suspend fun createStaff(request: CreateStaffRequest): StaffMember =
        client.post("api/v1/staff/panel/staff") { jsonBody(request) }.body()

    suspend fun updateStaff(staffId: Long, request: UpdateStaffRequest): StaffMember =
        client.patch("api/v1/staff/panel/staff/$staffId") { jsonBody(request) }.body()

    suspend fun assignBranch(staffId: Long, branchId: Long?): StaffMember =
        client.patch("api/v1/staff/panel/staff/$staffId/branch") { jsonBody(AssignBranchRequest(branchId)) }.body()

    suspend fun categories(menuId: Long): List<MenuCategory> =
        client.get("api/v1/menu/$menuId/categories").body<List<MenuCategory>>().sortedBy { it.sortOrder }

    /** Marks a category "Mutfağa gitmez": its products skip the kitchen ticket. */
    suspend fun setSkipKitchen(menuId: Long, categoryId: Long, skip: Boolean): MenuCategory =
        client.patch("api/v1/menu/$menuId/categories/$categoryId/kitchen") { jsonBody(KitchenRoutingRequest(skip)) }.body()

    /** Every product of the menu; qr-service pages them, so pages are fetched until the last one. */
    suspend fun products(menuId: Long): List<MenuProduct> {
        val all = mutableListOf<MenuProduct>()
        var page = 0
        do {
            val result = client.get("api/v1/menu/$menuId/products") {
                parameter("page", page)
                parameter("size", PRODUCT_PAGE_SIZE)
            }.body<MenuProductPage>()
            all += result.content
            page++
        } while (result.hasNext && page < MAX_PRODUCT_PAGES)
        return all
    }

    suspend fun createProduct(menuId: Long, request: CreateProductRequest): MenuProduct =
        client.post("api/v1/menu/$menuId/products") { jsonBody(request) }.body()

    suspend fun reservations(menuId: Long): List<Reservation> =
        client.get("api/v1/menu/$menuId/reservations") {
            parameter("status", "all")
            parameter("size", 100)
        }.body<ReservationPage>().content

    suspend fun updateReservation(menuId: Long, reservationId: Long, status: ReservationStatus): Reservation =
        client.patch("api/v1/menu/$menuId/reservations/$reservationId") { jsonBody(ReservationUpdateRequest(status)) }.body()
}
