package com.algoryqr.business.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.algoryqr.shared.ui.AppIcons
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data object DashboardRoute

@Serializable
data object OrdersRoute

@Serializable
data object TablesRoute

@Serializable
data object ReportsRoute

/** One report of the hub on its own page; [section] is a `ReportSection` name. */
@Serializable
data class ReportDetailRoute(val section: String)

@Serializable
data object StaffRoute

@Serializable
data object AccountRoute

@Serializable
data object StaffFormRoute

@Serializable
data object ReservationsRoute

@Serializable
data object FlowsRoute

@Serializable
data object KitchenRoutingRoute

@Serializable
data object ProductsRoute

@Serializable
data object ProductFormRoute

@Serializable
data object IntegrationsRoute

/** One delivery platform's connection and orders; [platform] is a `Platform` name. */
@Serializable
data class PlatformRoute(val platform: String)

/** New flow from [templateCode], or edit of [flowId]. */
@Serializable
data class FlowEditorRoute(val flowId: Long? = null, val templateCode: String? = null)

/**
 * Tabs of the bottom bar: the screens an owner checks every day. Everything else lives behind [MORE], which opens
 * a sheet instead of a screen ([route] is null), and those screens open on top of whatever tab was showing.
 */
enum class BusinessTab(val route: Any?, val routeClass: KClass<*>?, val label: String, val icon: ImageVector) {
    DASHBOARD(DashboardRoute, DashboardRoute::class, "Özet", AppIcons.Dashboard),
    ORDERS(OrdersRoute, OrdersRoute::class, "Siparişler", AppIcons.Orders),
    TABLES(TablesRoute, TablesRoute::class, "Masalar", AppIcons.Tables),
    REPORTS(ReportsRoute, ReportsRoute::class, "Raporlar", AppIcons.Report),
    MORE(null, null, "Daha fazla", AppIcons.More),
}

fun NavDestination?.currentTab(): BusinessTab? =
    this?.let { destination -> BusinessTab.entries.firstOrNull { tab -> tab.routeClass?.let { destination.hasRoute(it) } == true } }

/** Screens reached from the "Daha fazla" sheet, and the screens they open in turn. */
private val moreRoutes: List<KClass<*>> = listOf(
    StaffRoute::class, StaffFormRoute::class, AccountRoute::class, ReservationsRoute::class, FlowsRoute::class,
    FlowEditorRoute::class, KitchenRoutingRoute::class, ProductsRoute::class, ProductFormRoute::class, IntegrationsRoute::class, PlatformRoute::class,
)

/** The tab a screen belongs to, so the bar stays visible and highlighted on detail screens too. */
fun NavDestination?.selectedTab(): BusinessTab? = currentTab() ?: this?.let { destination ->
    when {
        destination.hasRoute(ReportDetailRoute::class) -> BusinessTab.REPORTS
        moreRoutes.any { destination.hasRoute(it) } -> BusinessTab.MORE
        else -> null
    }
}

/** Opens a "Daha fazla" screen on top of the current one, so Back returns to where the owner was. */
fun NavHostController.openFromMore(route: Any) {
    navigate(route) { launchSingleTop = true }
}

/** Switches tabs the usual way: one copy of each tab on the stack, each keeping its scroll and state. */
fun NavHostController.navigateToTab(tab: BusinessTab) {
    val route = tab.route ?: return
    // "Daha fazla" screens sit on top of whichever tab was showing; drop them so they don't come back inside it.
    while (currentBackStackEntry?.destination.selectedTab() == BusinessTab.MORE && previousBackStackEntry != null) {
        popBackStack()
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
