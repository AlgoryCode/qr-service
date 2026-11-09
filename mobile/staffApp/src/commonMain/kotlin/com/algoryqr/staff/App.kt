package com.algoryqr.staff

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.algoryqr.shared.session.Session
import com.algoryqr.shared.ui.AlgoryQrTheme
import com.algoryqr.shared.ui.AppBottomBar
import com.algoryqr.shared.ui.AuraBackground
import com.algoryqr.shared.ui.BottomTab
import com.algoryqr.staff.feature.account.StaffAccountScreen
import com.algoryqr.staff.feature.auth.StaffLoginScreen
import com.algoryqr.staff.feature.auth.StaffLoginViewModel
import com.algoryqr.staff.feature.courier.CourierScreen
import com.algoryqr.staff.feature.courier.CourierViewModel
import com.algoryqr.staff.feature.flow.BranchFlowViewModel
import com.algoryqr.staff.feature.kitchen.KitchenScreen
import com.algoryqr.staff.feature.kitchen.KitchenViewModel
import com.algoryqr.staff.feature.waiter.EarningsScreen
import com.algoryqr.staff.feature.waiter.EarningsViewModel
import com.algoryqr.staff.feature.waiter.OrderComposerScreen
import com.algoryqr.staff.feature.waiter.OrderComposerViewModel
import com.algoryqr.staff.feature.waiter.OrderDetailScreen
import com.algoryqr.staff.feature.waiter.OrderDetailViewModel
import com.algoryqr.staff.feature.waiter.PaymentScreen
import com.algoryqr.staff.feature.waiter.PaymentViewModel
import com.algoryqr.staff.feature.waiter.TableDetailScreen
import com.algoryqr.staff.feature.waiter.TableDetailViewModel
import com.algoryqr.staff.feature.waiter.TableHistoryScreen
import com.algoryqr.staff.feature.waiter.TableHistoryViewModel
import com.algoryqr.staff.feature.waiter.WaiterBoardViewModel
import com.algoryqr.staff.feature.waiter.WaiterOrdersScreen
import com.algoryqr.staff.feature.waiter.WaiterTablesScreen
import com.algoryqr.staff.feature.waiter.WaiterTablesViewModel
import com.algoryqr.staff.navigation.AccountRoute
import com.algoryqr.staff.navigation.DeliveriesRoute
import com.algoryqr.staff.navigation.EarningsRoute
import com.algoryqr.staff.navigation.KitchenRoute
import com.algoryqr.staff.navigation.NewOrderRoute
import com.algoryqr.staff.navigation.OrderDetailRoute
import com.algoryqr.staff.navigation.PaymentRoute
import com.algoryqr.staff.navigation.StaffTab
import com.algoryqr.staff.navigation.TableDetailRoute
import com.algoryqr.staff.navigation.TableHistoryRoute
import com.algoryqr.staff.navigation.TablesRoute
import com.algoryqr.staff.navigation.WaiterOrdersRoute
import com.algoryqr.staff.navigation.currentTab
import com.algoryqr.staff.navigation.navigateToTab
import com.algoryqr.staff.navigation.selectedTab
import kotlinx.coroutines.launch

private const val SENT_NOTICE_KEY = "sent_notice"
private const val BILL_CLOSED_KEY = "bill_closed"

@Composable
fun App(container: StaffContainer) {
    AlgoryQrTheme {
        AuraBackground {
            val session by container.sessionManager.session.collectAsStateWithLifecycle()
            val current = session
            if (current == null) {
                StaffLoginScreen(viewModel { StaffLoginViewModel(container.authRepository, container.localStore) })
            } else {
                MainShell(container, current)
            }
        }
    }
}

/** One navigation graph for every role; the role only decides the start tab and which tabs the bar offers. */
@Composable
private fun MainShell(container: StaffContainer, session: Session) {
    val profile = session.profile
    val flowViewModel = viewModel { BranchFlowViewModel(container.repository) }
    val flowState by flowViewModel.state.collectAsStateWithLifecycle()
    val flow = flowState.data
    val tabs = StaffTab.forRole(profile.role, flow)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination.currentTab(tabs)
    // The bar stays on every screen; on a detail screen it highlights the tab the screen came from.
    val selectedTab = backStackEntry?.destination.selectedTab(tabs)
    val repository = container.repository
    val scope = rememberCoroutineScope()
    val signOut: () -> Unit = { scope.launch { container.authRepository.logout() } }

    // Held by the shell so the Siparişler badge stays live while the waiter is on another tab.
    val waiterBoard = if (StaffTab.ORDERS in tabs) viewModel { WaiterBoardViewModel(repository) } else null
    val waiterBoardState = waiterBoard?.state?.collectAsStateWithLifecycle()
    val waitingCount = waiterBoardState?.value?.data?.let { board ->
        (if (flow?.tableApproval != false) board.pending.size else 0) + (if (flow?.tableKitchen != false) board.ready.size else 0)
    } ?: 0

    Column {
        // While the bar is visible it sits above the system navigation bar, so screens must not pad for it again.
        val contentModifier = if (selectedTab != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier
        Box(contentModifier.weight(1f).fillMaxWidth()) {
            NavHost(navController = navController, startDestination = tabs.first().route) {
                composable<TablesRoute> {
                    WaiterTablesScreen(
                        viewModel { WaiterTablesViewModel(repository, profile.merchantId, profile.branchId) },
                        // A table opens its own screen: bill, open orders and "Ürün ekle" in one place.
                        onOpenTable = { table -> navController.navigate(TableDetailRoute(table.tableId, table.title)) },
                    )
                }
                composable<WaiterOrdersRoute> {
                    waiterBoard?.let { board ->
                        WaiterOrdersScreen(board, flow, onOpenOrder = { order -> navController.navigate(OrderDetailRoute(order.id)) })
                    }
                }
                composable<OrderDetailRoute> { entry ->
                    val route = entry.toRoute<OrderDetailRoute>()
                    OrderDetailScreen(
                        viewModel(key = "order-detail-${route.orderId}") { OrderDetailViewModel(repository, route.orderId) },
                        onBack = navController::popBackStack,
                    )
                }
                composable<EarningsRoute> {
                    EarningsScreen(viewModel { EarningsViewModel(repository) })
                }
                composable<TableDetailRoute> { entry ->
                    val route = entry.toRoute<TableDetailRoute>()
                    val tableViewModel = viewModel(key = "table-${route.tableId}") { TableDetailViewModel(repository, container.cartDrafts, route.tableId) }
                    // The product picker reports what it sent; the table screen shows it once it is back on top.
                    val sent by entry.savedStateHandle.getStateFlow<String?>(SENT_NOTICE_KEY, null).collectAsStateWithLifecycle()
                    val closed by entry.savedStateHandle.getStateFlow(BILL_CLOSED_KEY, false).collectAsStateWithLifecycle()
                    LaunchedEffect(sent, closed) {
                        if (closed) {
                            tableViewModel.onBillClosed()
                            entry.savedStateHandle[BILL_CLOSED_KEY] = false
                        }
                        sent?.let {
                            tableViewModel.showNotice(it)
                            entry.savedStateHandle[SENT_NOTICE_KEY] = null
                        }
                    }
                    TableDetailScreen(
                        tableViewModel,
                        tableName = route.tableName,
                        // Without a kitchen step the waiter serves straight from "Onaylandı".
                        tableKitchen = flow?.tableKitchen ?: true,
                        onBack = navController::popBackStack,
                        onAddItems = { navController.navigate(NewOrderRoute(route.tableId, route.tableName)) },
                        onTakePayment = { navController.navigate(PaymentRoute(route.tableId, route.tableName)) },
                        onOpenHistory = { navController.navigate(TableHistoryRoute(route.tableId, route.tableName)) },
                    )
                }
                composable<TableHistoryRoute> { entry ->
                    val route = entry.toRoute<TableHistoryRoute>()
                    TableHistoryScreen(
                        viewModel(key = "table-history-${route.tableId}") { TableHistoryViewModel(repository, route.tableId) },
                        tableName = route.tableName,
                        onBack = navController::popBackStack,
                    )
                }
                composable<PaymentRoute> { entry ->
                    val route = entry.toRoute<PaymentRoute>()
                    PaymentScreen(
                        viewModel(key = "payment-${route.tableId}") { PaymentViewModel(repository, route.tableId) },
                        tableName = route.tableName,
                        onBack = navController::popBackStack,
                        onClosed = { message ->
                            navController.previousBackStackEntry?.savedStateHandle?.set(SENT_NOTICE_KEY, message)
                            navController.previousBackStackEntry?.savedStateHandle?.set(BILL_CLOSED_KEY, true)
                            navController.popBackStack()
                        },
                    )
                }
                composable<NewOrderRoute> { entry ->
                    val route = entry.toRoute<NewOrderRoute>()
                    // The product picker sits on top of the table screen; sending or Back lands on the updated bill.
                    OrderComposerScreen(
                        viewModel(key = "order-${route.tableId}") {
                            OrderComposerViewModel(repository, container.cartDrafts, container.appScope, route.tableId)
                        },
                        tableName = route.tableName,
                        kitchenActive = flow?.tableKitchen == true,
                        onBack = navController::popBackStack,
                        onSent = { toKitchen ->
                            navController.previousBackStackEntry?.savedStateHandle?.set(
                                SENT_NOTICE_KEY,
                                if (toKitchen) "Sipariş mutfağa gönderildi" else "Ürünler adisyona eklendi",
                            )
                            navController.popBackStack()
                        },
                    )
                }
                composable<KitchenRoute> {
                    KitchenScreen(viewModel { KitchenViewModel(repository) }, flow)
                }
                composable<DeliveriesRoute> {
                    CourierScreen(viewModel { CourierViewModel(repository) }, flow)
                }
                composable<AccountRoute> {
                    StaffAccountScreen(session, onSignOut = signOut, signOutOthers = container.authRepository::signOutOtherDevices)
                }
            }
        }
        if (selectedTab != null && tabs.size > 1) {
            AppBottomBar(
                tabs = tabs.map { BottomTab(it, it.label, it.icon, badge = if (it == StaffTab.ORDERS) waitingCount else 0) },
                current = selectedTab,
                onSelect = { tab ->
                    // Tapping the tab of the detail screen you are on goes back to that tab's start.
                    if (tab == selectedTab && currentTab == null) {
                        navController.popBackStack(tab.route, inclusive = false)
                    } else {
                        navController.navigateToTab(tab)
                    }
                },
            )
        }
    }
}
