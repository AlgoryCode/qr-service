package com.algoryqr.business.data

import kotlinx.serialization.Serializable

@Serializable
data class BranchMenu(
    val menuId: Long,
    val publicId: String? = null,
    val businessName: String? = null,
    val active: Boolean = true,
)

/** `BranchDtos.Response`. */
@Serializable
data class Branch(
    val id: Long,
    val merchantId: Long? = null,
    val name: String = "",
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val active: Boolean = true,
    val kitchenEnabled: Boolean = false,
    val courierEnabled: Boolean = false,
    val menus: List<BranchMenu> = emptyList(),
) {
    /** Orders, reservations and catalog belong to a menu; owners almost always have one per branch. */
    val primaryMenuId: Long? get() = (menus.firstOrNull { it.active } ?: menus.firstOrNull())?.menuId
}

@Serializable
data class BranchListResponse(val content: List<Branch> = emptyList())

@Serializable
data class SummaryOverview(
    val grossRevenue: Double = 0.0,
    val netRevenue: Double = 0.0,
    val tipTotal: Double = 0.0,
    val orderCount: Long = 0,
    val completedOrderCount: Long = 0,
    val cancelledOrderCount: Long = 0,
    val avgBasket: Double = 0.0,
    val activeOpenTables: Long = 0,
    val occupancyRatio: Double? = null,
    val cancelRate: Double = 0.0,
    val revenueVsPreviousPct: Double? = null,
    val currency: String? = null,
)

@Serializable
data class HourlyRevenuePoint(val hour: Int, val revenue: Double = 0.0, val orderCount: Long = 0)

@Serializable
data class RevenueProduct(val productId: Long? = null, val name: String = "", val quantity: Long = 0, val revenue: Double = 0.0)

@Serializable
data class SalesTeaser(
    val hourlyRevenue: List<HourlyRevenuePoint> = emptyList(),
    val topProducts: List<RevenueProduct> = emptyList(),
)

@Serializable
data class WaiterPerformanceRow(
    val staffId: Long? = null,
    val displayName: String? = null,
    val orderCount: Long = 0,
    val revenue: Double = 0.0,
    val tipAmount: Double = 0.0,
    val revenueSharePercent: Double = 0.0,
)

@Serializable
data class StaffTeaser(val topWaitersByRevenue: List<WaiterPerformanceRow> = emptyList())

@Serializable
data class OperationsTeaser(val openBills: Long = 0, val confirmedOrders: Long = 0)

/** Menu browsing steps of QR guests, from opening the menu to sending an order. */
@Serializable
data class BrowseFunnel(
    val menuOpens: Long = 0,
    val categoryViews: Long = 0,
    val productViews: Long = 0,
    val addToCart: Long = 0,
    val checkoutStarts: Long = 0,
    val orderSubmitted: Long = 0,
)

@Serializable
data class QrTeaser(
    val sessions: Long = 0,
    val menuOpens: Long = 0,
    val productViews: Long = 0,
    val browseFunnel: BrowseFunnel = BrowseFunnel(),
)

@Serializable
data class TableRevenueRow(
    val tableId: Long? = null,
    val tableName: String = "",
    val revenue: Double = 0.0,
    val orderCount: Long = 0,
    val billCount: Long = 0,
    val avgCheck: Double = 0.0,
    val avgDwellMinutes: Double? = null,
)

@Serializable
data class TablesTeaser(val topTablesByRevenue: List<TableRevenueRow> = emptyList(), val avgTurnMinutes: Double? = null)

/** `AnalyticsDtos.SummaryAnalyticsResponse`, trimmed to what the dashboard shows. */
@Serializable
data class SummaryAnalytics(
    val branchName: String? = null,
    val overview: SummaryOverview = SummaryOverview(),
    val salesTeaser: SalesTeaser = SalesTeaser(),
    val staffTeaser: StaffTeaser = StaffTeaser(),
    val operationsTeaser: OperationsTeaser = OperationsTeaser(),
    val tablesTeaser: TablesTeaser = TablesTeaser(),
    val qrTeaser: QrTeaser = QrTeaser(),
)

/** Mirrors qr-service `StaffRole`. */
@Serializable
enum class StaffRole(val label: String) { WAITER("Garson"), KITCHEN("Mutfak"), COURIER("Kurye") }

/** `MenuWaiterDtos.WaiterResponse`. */
@Serializable
data class StaffMember(
    val id: Long,
    val branchId: Long? = null,
    val username: String = "",
    val displayName: String = "",
    val staffRole: StaffRole? = null,
    val active: Boolean = true,
    val commissionEnabled: Boolean = false,
)

@Serializable
data class StaffListResponse(val staff: List<StaffMember> = emptyList())

@Serializable
data class CreateStaffRequest(
    val username: String,
    val password: String,
    val displayName: String,
    val staffRole: StaffRole,
)

@Serializable
data class UpdateStaffRequest(val displayName: String? = null, val active: Boolean? = null, val password: String? = null)

@Serializable
data class AssignBranchRequest(val branchId: Long?)

/** Mirrors qr-service `MenuReservationStatus`. */
@Serializable
enum class ReservationStatus(val label: String) { PENDING("Bekliyor"), ACTIVE("Onaylı"), CANCELED("İptal") }

@Serializable
data class Reservation(
    val id: Long,
    val customerName: String = "",
    val phone: String? = null,
    val email: String? = null,
    val partySize: Int = 0,
    val reservationAt: String? = null,
    val status: ReservationStatus = ReservationStatus.PENDING,
    val note: String? = null,
)

@Serializable
data class ReservationPage(val content: List<Reservation> = emptyList())

@Serializable
data class ReservationUpdateRequest(val status: ReservationStatus)

/** `TaxonomyDtos.MainCategoryResponse`, trimmed to what the kitchen routing screen needs. */
@Serializable
data class MenuCategory(
    val id: Long,
    val name: String = "",
    val sortOrder: Int = 0,
    val skipKitchen: Boolean = false,
    /** Products always sit in a sub-category ("Ana Yemek" under "Yemekler"). */
    val subs: List<MenuSubCategory> = emptyList(),
)

@Serializable
data class MenuSubCategory(val id: Long, val name: String = "", val sortOrder: Int = 0)

/** One product of the menu as qr-service lists it. */
@Serializable
data class MenuProduct(
    val productId: Long,
    val name: String = "",
    val description: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val subCategoryId: Long? = null,
    val subCategoryName: String? = null,
    val mainCategoryId: Long? = null,
    val mainCategoryName: String? = null,
    val imageUrl: String? = null,
    val available: Boolean = true,
    val sortOrder: Int = 0,
)

@Serializable
data class MenuProductPage(val content: List<MenuProduct> = emptyList(), val hasNext: Boolean = false)

/** Nutrition label basis: values are per 100 g of food or per 100 ml of drink. */
enum class NutritionBasis(val label: String) { PER_100G("100 g başına"), PER_100ML("100 ml başına") }

/** The nutrition label of a new product: qr-service requires the calories per [basis]; every other line is optional. */
@Serializable
data class NutritionFacts(
    val basis: NutritionBasis,
    val energyKj: Double,
    val energyKcal: Double,
    val fat: Double? = null,
    val saturatedFat: Double? = null,
    val carbohydrate: Double? = null,
    val sugars: Double? = null,
    val fibre: Double? = null,
    val protein: Double? = null,
    val salt: Double? = null,
)

/** `POST /api/v1/menu/{menuId}/products`: name, sub-category and nutrition are required, the rest optional. */
@Serializable
data class CreateProductRequest(
    val name: String,
    val subCategoryId: Long,
    val nutrition: NutritionFacts,
    val price: Double? = null,
    val description: String? = null,
    val available: Boolean = true,
    val currency: String = "TRY",
)

@Serializable
data class KitchenRoutingRequest(val skipKitchen: Boolean)

@Serializable
data class RevenueKpis(
    val totalRevenue: Double = 0.0,
    val orderCount: Long = 0,
    val itemCount: Long = 0,
    val avgOrderValue: Double = 0.0,
    val currency: String? = null,
)

@Serializable
data class DailyRevenuePoint(val date: String = "", val revenue: Double = 0.0, val orderCount: Long = 0)

@Serializable
data class RevenueCategory(val categoryId: Long? = null, val name: String = "", val quantity: Long = 0, val revenue: Double = 0.0)

@Serializable
data class UnsoldProduct(val productId: Long? = null, val name: String = "")

@Serializable
data class UnsoldCatalog(val count: Long = 0, val products: List<UnsoldProduct> = emptyList())

@Serializable
data class RevenueSpotlight(
    val byQuantity: RevenueProduct? = null,
    val byRevenue: RevenueProduct? = null,
    val leastSoldByQuantity: RevenueProduct? = null,
)

@Serializable
data class PaymentBreakdown(
    val cashRevenue: Double = 0.0,
    val cardRevenue: Double = 0.0,
    val tipRevenue: Double = 0.0,
    val uberEatsRevenue: Double = 0.0,
    val yemeksepetiRevenue: Double = 0.0,
    val grossRevenue: Double = 0.0,
    val fixedExpenseTotal: Double = 0.0,
    val netRevenue: Double = 0.0,
)

@Serializable
data class ChannelShare(
    val code: String = "",
    val label: String = "",
    val revenue: Double = 0.0,
    val orderCount: Long = 0,
    val sharePercent: Double? = null,
    val connected: Boolean = true,
)

/** `AnalyticsDtos.MenuRevenueReportResponse`, trimmed to what the reports screen shows. */
@Serializable
data class RevenueReport(
    val kpis: RevenueKpis = RevenueKpis(),
    val daily: List<DailyRevenuePoint> = emptyList(),
    val products: List<RevenueProduct> = emptyList(),
    val categories: List<RevenueCategory> = emptyList(),
    val spotlight: RevenueSpotlight = RevenueSpotlight(),
    val hourly: List<HourlyRevenuePoint> = emptyList(),
    val unsold: UnsoldCatalog = UnsoldCatalog(),
    val paymentBreakdown: PaymentBreakdown? = null,
    val channels: List<ChannelShare> = emptyList(),
    val channelDaily: List<ChannelDailyPoint> = emptyList(),
) {
    /** Delivery platforms' channels; table and QR orders are `IN_HOUSE`. */
    val platformChannels: List<ChannelShare> get() = channels.filter { it.code != IN_HOUSE_CHANNEL }

    /** [kpis] counts closed bills only; platform orders are counted in their channels. */
    val totalOrders: Long get() = kpis.orderCount + platformChannels.sumOf { it.orderCount }

    /** [daily] holds bill payments only; platform revenue and orders are added per day from [channelDaily]. */
    val dailyWithPlatforms: List<DailyRevenuePoint>
        get() {
            val platform = channelDaily.filter { it.channelCode != IN_HOUSE_CHANNEL }.groupBy { it.date }
            return daily.map { day ->
                val extra = platform[day.date].orEmpty()
                day.copy(revenue = day.revenue + extra.sumOf { it.revenue }, orderCount = day.orderCount + extra.sumOf { it.orderCount })
            }
        }
}

const val IN_HOUSE_CHANNEL = "IN_HOUSE"

@Serializable
data class ChannelDailyPoint(val date: String = "", val channelCode: String = "", val revenue: Double = 0.0, val orderCount: Long = 0)

@Serializable
data class StaffPerformanceKpis(
    val activeWaiterCount: Long = 0,
    val assignedOrderCount: Long = 0,
    val unassignedOrderCount: Long = 0,
    val totalRevenue: Double = 0.0,
    val itemCount: Long = 0,
    val totalCommission: Double = 0.0,
    val totalTip: Double = 0.0,
    val billsClosedCount: Long = 0,
    val currency: String? = null,
)

@Serializable
data class StaffPerformanceRow(
    val staffId: Long? = null,
    val displayName: String? = null,
    val orderCount: Long = 0,
    val itemCount: Long = 0,
    val revenue: Double = 0.0,
    val tipAmount: Double = 0.0,
    val commissionAmount: Double = 0.0,
    val billsClosedCount: Long = 0,
    val avgOrderValue: Double = 0.0,
    val revenueSharePercent: Double = 0.0,
    val orderSharePercent: Double = 0.0,
    val itemSharePercent: Double = 0.0,
    val active: Boolean = true,
    val topProducts: List<RevenueProduct> = emptyList(),
)

/** `AnalyticsDtos.MerchantStaffPerformanceReportResponse`. */
@Serializable
data class StaffPerformanceReport(
    val kpis: StaffPerformanceKpis = StaffPerformanceKpis(),
    val waiters: List<StaffPerformanceRow> = emptyList(),
    val daily: List<DailyRevenuePoint> = emptyList(),
    val hourly: List<HourlyRevenuePoint> = emptyList(),
    val products: List<RevenueProduct> = emptyList(),
)
