package com.algoryqr.staff.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.algoryqr.shared.auth.Roles
import com.algoryqr.shared.model.BranchFlow
import com.algoryqr.shared.ui.AppIcons
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data object TablesRoute

@Serializable
data object WaiterOrdersRoute

@Serializable
data object EarningsRoute

@Serializable
data object KitchenRoute

@Serializable
data object DeliveriesRoute

@Serializable
data object AccountRoute

@Serializable
data class TableDetailRoute(val tableId: Long, val tableName: String)

@Serializable
data class TableHistoryRoute(val tableId: Long, val tableName: String)

@Serializable
data class NewOrderRoute(val tableId: Long, val tableName: String)

@Serializable
data class PaymentRoute(val tableId: Long, val tableName: String)

@Serializable
data class OrderDetailRoute(val orderId: Long)

/** Tabs of the bottom bar; which ones show depends on the staff role. */
enum class StaffTab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector) {
    TABLES(TablesRoute, TablesRoute::class, "Masalar", AppIcons.Tables),
    ORDERS(WaiterOrdersRoute, WaiterOrdersRoute::class, "Siparişler", AppIcons.Notification),
    EARNINGS(EarningsRoute, EarningsRoute::class, "Kazanç", AppIcons.Wallet),
    KITCHEN(KitchenRoute, KitchenRoute::class, "Mutfak", AppIcons.Kitchen),
    DELIVERIES(DeliveriesRoute, DeliveriesRoute::class, "Teslimat", AppIcons.Courier),
    ACCOUNT(AccountRoute, AccountRoute::class, "Hesap", AppIcons.Account);

    companion object {
        /** Waiters always get "Siparişler": the day's orders are there even when the flow has nothing to act on. */
        @Suppress("UNUSED_PARAMETER")
        fun forRole(role: String, flow: BranchFlow?): List<StaffTab> = when (role) {
            Roles.KITCHEN -> listOf(KITCHEN, ACCOUNT)
            Roles.COURIER -> listOf(DELIVERIES, ACCOUNT)
            else -> listOf(TABLES, ORDERS, EARNINGS, ACCOUNT)
        }
    }
}

fun NavDestination?.currentTab(tabs: List<StaffTab>): StaffTab? =
    this?.let { destination -> tabs.firstOrNull { destination.hasRoute(it.routeClass) } }

/** The tab a screen belongs to, so the bar stays visible and highlighted on detail screens too. */
fun NavDestination?.selectedTab(tabs: List<StaffTab>): StaffTab? = currentTab(tabs) ?: this?.let { destination ->
    when {
        destination.hasRoute(TableDetailRoute::class) || destination.hasRoute(NewOrderRoute::class) ||
            destination.hasRoute(PaymentRoute::class) || destination.hasRoute(TableHistoryRoute::class) -> StaffTab.TABLES
        destination.hasRoute(OrderDetailRoute::class) -> StaffTab.ORDERS
        else -> null
    }?.takeIf { it in tabs }
}

/** Switches tabs the usual way: one copy of each tab on the stack, each keeping its scroll and state. */
fun NavHostController.navigateToTab(tab: StaffTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
