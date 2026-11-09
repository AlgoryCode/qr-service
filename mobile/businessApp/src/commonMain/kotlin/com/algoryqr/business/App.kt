package com.algoryqr.business

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.algoryqr.business.data.Branch
import com.algoryqr.business.data.BusinessRepository
import com.algoryqr.business.feature.account.AccountScreen
import com.algoryqr.business.feature.auth.LoginScreen
import com.algoryqr.business.feature.auth.LoginViewModel
import com.algoryqr.business.feature.dashboard.DashboardScreen
import com.algoryqr.business.feature.dashboard.DashboardViewModel
import com.algoryqr.business.feature.flow.FlowEditorScreen
import com.algoryqr.business.feature.flow.FlowEditorViewModel
import com.algoryqr.business.feature.flow.FlowsScreen
import com.algoryqr.business.feature.flow.FlowsViewModel
import com.algoryqr.business.feature.kitchen.KitchenRoutingScreen
import com.algoryqr.business.feature.kitchen.KitchenRoutingViewModel
import com.algoryqr.business.feature.orders.OrdersScreen
import com.algoryqr.business.feature.orders.OrdersViewModel
import com.algoryqr.business.data.Platform
import com.algoryqr.business.feature.integrations.IntegrationsScreen
import com.algoryqr.business.feature.integrations.IntegrationsViewModel
import com.algoryqr.business.feature.integrations.PlatformScreen
import com.algoryqr.business.feature.integrations.PlatformViewModel
import com.algoryqr.business.feature.more.MoreDestination
import com.algoryqr.business.feature.more.MoreSheet
import com.algoryqr.business.feature.reports.ReportDetailScreen
import com.algoryqr.business.feature.reports.ReportSection
import com.algoryqr.business.feature.reports.ReportsScreen
import com.algoryqr.business.feature.reports.ReportsViewModel
import com.algoryqr.business.feature.reservations.ReservationsScreen
import com.algoryqr.business.feature.reservations.ReservationsViewModel
import com.algoryqr.business.feature.staff.StaffFormScreen
import com.algoryqr.business.feature.staff.StaffFormViewModel
import com.algoryqr.business.feature.staff.StaffScreen
import com.algoryqr.business.feature.staff.StaffViewModel
import com.algoryqr.business.feature.tables.TablesScreen
import com.algoryqr.business.feature.tables.TablesViewModel
import com.algoryqr.business.navigation.AccountRoute
import com.algoryqr.business.navigation.BusinessTab
import com.algoryqr.business.navigation.DashboardRoute
import com.algoryqr.business.navigation.FlowEditorRoute
import com.algoryqr.business.navigation.FlowsRoute
import com.algoryqr.business.navigation.KitchenRoutingRoute
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.business.navigation.ProductsRoute
import com.algoryqr.business.navigation.ProductFormRoute
import com.algoryqr.business.feature.products.ProductsViewModel
import com.algoryqr.business.feature.products.ProductsScreen
import com.algoryqr.business.feature.products.ProductFormViewModel
import com.algoryqr.business.feature.products.ProductFormScreen
import com.algoryqr.business.navigation.OrdersRoute
import com.algoryqr.business.navigation.ReportDetailRoute
import com.algoryqr.business.navigation.ReportsRoute
import com.algoryqr.business.navigation.currentTab
import com.algoryqr.business.navigation.IntegrationsRoute
import com.algoryqr.business.navigation.PlatformRoute
import com.algoryqr.business.navigation.ReservationsRoute
import com.algoryqr.business.navigation.StaffFormRoute
import com.algoryqr.business.navigation.StaffRoute
import com.algoryqr.business.navigation.TablesRoute
import com.algoryqr.business.navigation.selectedTab
import com.algoryqr.business.navigation.navigateToTab
import com.algoryqr.business.navigation.openFromMore
import com.algoryqr.shared.session.Session
import com.algoryqr.shared.ui.AlgoryQrTheme
import com.algoryqr.shared.ui.AppBottomBar
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AuraBackground
import com.algoryqr.shared.ui.BottomTab
import com.algoryqr.shared.ui.ChoiceChip
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.ScreenPadding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun App(container: BusinessContainer) {
    AlgoryQrTheme {
        AuraBackground {
            val session by container.sessionManager.session.collectAsStateWithLifecycle()
            val current = session
            if (current == null) {
                LoginScreen(viewModel { LoginViewModel(container.authRepository) })
            } else {
                MainShell(container, current)
            }
        }
    }
}

/** Branches of the signed-in owner and which one the screens are showing. */
class BranchesViewModel(private val repository: BusinessRepository) : LoadViewModel<List<Branch>>() {
    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = _selectedId.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): List<Branch> = repository.branches().let { all -> all.filter { it.active }.ifEmpty { all } }

    fun select(branchId: Long) {
        _selectedId.value = branchId
    }
}

@Composable
private fun MainShell(container: BusinessContainer, session: Session) {
    val branchesViewModel = viewModel { BranchesViewModel(container.repository) }
    val state by branchesViewModel.state.collectAsStateWithLifecycle()
    val selectedId by branchesViewModel.selectedId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val signOut: () -> Unit = { scope.launch { container.authRepository.logout() } }

    LoadContent(state, onRetry = branchesViewModel::refresh) { branches ->
        val branch = branches.firstOrNull { it.id == selectedId } ?: branches.firstOrNull()
        if (branch == null) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(ScreenPadding)) {
                EmptyState(
                    title = "Henüz şubeniz yok",
                    description = "Şube ve menünüzü AlgoryQR web panelinden oluşturduktan sonra burada görünecek.",
                    icon = AppIcons.Branch,
                    modifier = Modifier.padding(top = 80.dp),
                )
            }
        } else {
            ShellContent(container, session, branches, branch, branchesViewModel::select, signOut)
        }
    }
}

@Composable
private fun ShellContent(
    container: BusinessContainer,
    session: Session,
    branches: List<Branch>,
    branch: Branch,
    onSelectBranch: (Long) -> Unit,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // The bar stays on every screen; on a detail screen it highlights the tab the screen came from.
    val selectedTab = backStackEntry?.destination.selectedTab()
    val currentTab = backStackEntry?.destination.currentTab()
    val repository = container.repository
    val branchPicker: @Composable () -> Unit = { BranchPicker(branches, branch, onSelectBranch) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }

    Column {
        // While the bar is visible it sits above the system navigation bar, so screens must not pad for it again.
        val contentModifier = if (selectedTab != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier
        Box(contentModifier.weight(1f).fillMaxWidth()) {
            NavHost(navController = navController, startDestination = DashboardRoute) {
                composable<DashboardRoute> {
                    DashboardScreen(viewModel(key = "dashboard-${branch.id}") { DashboardViewModel(repository, branch) }, branchPicker)
                }
                composable<OrdersRoute> {
                    OrdersScreen(viewModel(key = "orders-${branch.id}") { OrdersViewModel(repository, container.integrationRepository, branch) }, branchPicker)
                }
                composable<TablesRoute> {
                    TablesScreen(
                        viewModel(key = "tables-${branch.id}") { TablesViewModel(repository, session.profile.merchantId, branch) },
                        branchPicker,
                    )
                }
                composable<ReportsRoute> {
                    ReportsScreen(
                        viewModel(key = "reports") { ReportsViewModel(repository, branch) },
                        branches,
                        branch,
                        onOpen = { section -> navController.navigate(ReportDetailRoute(section.name)) },
                    )
                }
                composable<ReportDetailRoute> { entry ->
                    // Detail pages share the hub's view model, so loaded ranges, the chosen branch and filters carry over.
                    val hub = remember(entry) { navController.getBackStackEntry(ReportsRoute) }
                    ReportDetailScreen(
                        viewModel(viewModelStoreOwner = hub, key = "reports") { ReportsViewModel(repository, branch) },
                        branches,
                        ReportSection.valueOf(entry.toRoute<ReportDetailRoute>().section),
                        onBack = navController::popBackStack,
                    )
                }
                composable<StaffRoute> {
                    StaffScreen(
                        viewModel { StaffViewModel(repository) },
                        branches = branches,
                        onBack = navController::popBackStack,
                        onAdd = { navController.navigate(StaffFormRoute) },
                    )
                }
                composable<AccountRoute> {
                    AccountScreen(
                        session = session,
                        branch = branch,
                        onBack = navController::popBackStack,
                        onSignOut = onSignOut,
                        signOutOthers = container.authRepository::signOutOtherDevices,
                    )
                }
                composable<StaffFormRoute> {
                    StaffFormScreen(
                        viewModel { StaffFormViewModel(repository, branch.id) },
                        onBack = navController::popBackStack,
                    )
                }
                composable<KitchenRoutingRoute> {
                    KitchenRoutingScreen(
                        viewModel(key = "kitchen-routing-${branch.id}") { KitchenRoutingViewModel(repository, branch.primaryMenuId) },
                        kitchenEnabled = branch.kitchenEnabled,
                        onBack = navController::popBackStack,
                    )
                }
                composable<ProductsRoute> {
                    val menuId = branch.primaryMenuId
                    if (menuId == null) {
                        NoMenuScreen(onBack = navController::popBackStack)
                    } else {
                        ProductsScreen(
                            viewModel(key = "products-$menuId") { ProductsViewModel(repository, menuId) },
                            onBack = navController::popBackStack,
                            onAdd = { navController.navigate(ProductFormRoute) },
                        )
                    }
                }
                composable<ProductFormRoute> {
                    val menuId = branch.primaryMenuId
                    if (menuId == null) {
                        NoMenuScreen(onBack = navController::popBackStack)
                    } else {
                        ProductFormScreen(
                            viewModel { ProductFormViewModel(repository, menuId) },
                            onBack = navController::popBackStack,
                        )
                    }
                }
                composable<FlowsRoute> {
                    FlowsScreen(
                        viewModel(key = "flows-${branch.id}") { FlowsViewModel(container.orderFlowRepository, branch) },
                        branch = branch,
                        onBack = navController::popBackStack,
                        onCreate = { template -> navController.navigate(FlowEditorRoute(templateCode = template)) },
                        onEdit = { flow -> navController.navigate(FlowEditorRoute(flowId = flow.id)) },
                    )
                }
                composable<FlowEditorRoute> { entry ->
                    val route = entry.toRoute<FlowEditorRoute>()
                    FlowEditorScreen(
                        viewModel(key = "flow-editor-${route.flowId}-${route.templateCode}") {
                            FlowEditorViewModel(container.orderFlowRepository, branch.id, route.flowId, route.templateCode)
                        },
                        isNew = route.flowId == null,
                        branchName = branch.name,
                        onBack = navController::popBackStack,
                    )
                }
                composable<IntegrationsRoute> {
                    IntegrationsScreen(
                        viewModel { IntegrationsViewModel(container.integrationRepository) },
                        branches = branches,
                        onOpenPlatform = { platform -> navController.navigate(PlatformRoute(platform.name)) },
                        onBack = navController::popBackStack,
                    )
                }
                composable<PlatformRoute> { entry ->
                    val platform = Platform.valueOf(entry.toRoute<PlatformRoute>().platform)
                    PlatformScreen(
                        viewModel(key = "platform-${platform.name}") { PlatformViewModel(container.integrationRepository, platform) },
                        branches = branches,
                        onBack = navController::popBackStack,
                    )
                }
                composable<ReservationsRoute> {
                    ReservationsScreen(
                        viewModel(key = "reservations-${branch.id}") { ReservationsViewModel(repository, branch.primaryMenuId) },
                        onBack = navController::popBackStack,
                    )
                }
            }
        }
        if (selectedTab != null) {
            AppBottomBar(
                tabs = BusinessTab.entries.map { BottomTab(it, it.label, it.icon) },
                // While the sheet is open its tab is the one lit, so it's clear where the sheet came from.
                current = if (moreOpen) BusinessTab.MORE else selectedTab,
                onSelect = { tab ->
                    when {
                        tab == BusinessTab.MORE -> moreOpen = true
                        // Tapping the tab of the report page you are on goes back to the reports hub.
                        tab == selectedTab && currentTab == null -> navController.popBackStack(tab.route!!, inclusive = false)
                        else -> navController.navigateToTab(tab)
                    }
                },
            )
        }
    }

    if (moreOpen) {
        MoreSheet(
            session = session,
            onOpen = { destination ->
                moreOpen = false
                navController.openFromMore(
                    when (destination) {
                        MoreDestination.STAFF -> StaffRoute
                        MoreDestination.PRODUCTS -> ProductsRoute
                        MoreDestination.RESERVATIONS -> ReservationsRoute
                        MoreDestination.INTEGRATIONS -> IntegrationsRoute
                        MoreDestination.FLOWS -> FlowsRoute
                        MoreDestination.KITCHEN_ROUTING -> KitchenRoutingRoute
                        MoreDestination.ACCOUNT -> AccountRoute
                    },
                )
            },
            onDismiss = { moreOpen = false },
        )
    }
}

/** Branch chips under the title; hidden for single-branch businesses, which are the common case. */
@Composable
private fun BranchPicker(branches: List<Branch>, selected: Branch, onSelect: (Long) -> Unit) {
    if (branches.size < 2) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        branches.forEach { branch ->
            ChoiceChip(branch.name, selected = branch.id == selected.id, onClick = { onSelect(branch.id) })
        }
    }
}

/** The branch has no QR menu yet, so there is nowhere to list or add products. */
@Composable
private fun NoMenuScreen(onBack: () -> Unit) {
    AppScreen(title = "Ürünler", onBack = onBack) {
        EmptyState(
            "Menü yok",
            "Bu şubenin menüsünü AlgoryQR web panelinden oluşturduktan sonra ürün ekleyebilirsiniz.",
            icon = AppIcons.Menu,
            modifier = Modifier.padding(ScreenPadding),
        )
    }
}
