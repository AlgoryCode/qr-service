package com.algoryqr.staff.feature.waiter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.format.formatTime
import com.algoryqr.shared.format.minuteOfDay
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.StatusChip
import com.algoryqr.shared.ui.tone
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.shared.model.BranchFlow
import com.algoryqr.shared.model.CancelOrderRequest
import com.algoryqr.shared.model.CancelReason
import com.algoryqr.shared.model.Order
import com.algoryqr.shared.model.OrderStatus
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.CancelReasonDialog
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.OrderCard
import com.algoryqr.shared.ui.OutlineButton
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SegmentedSwitch
import com.algoryqr.staff.data.StaffRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The day's orders of the branch, plus those a waiter has to act on: QR orders to approve and food the kitchen marked ready. */
data class WaiterBoard(val today: List<Order>, val pending: List<Order>, val ready: List<Order>)

enum class BoardTab { TODAY, PENDING, READY }

class WaiterBoardViewModel(private val repository: StaffRepository) : LoadViewModel<WaiterBoard>(pollMillis = 10_000) {

    private val _tab = MutableStateFlow(BoardTab.TODAY)
    val tab: StateFlow<BoardTab> = _tab.asStateFlow()

    /** Hour and amount ranges on "Bugün"; the action tabs are short enough to read whole. */
    private val _todayFilter = MutableStateFlow(TimeAmountFilter())
    val todayFilter: StateFlow<TimeAmountFilter> = _todayFilter.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): WaiterBoard = coroutineScope {
        val today = async { repository.todayOrders() }
        val pending = async { repository.pendingOrders() }
        val ready = async { repository.readyOrders() }
        WaiterBoard(today.await(), pending.await(), ready.await())
    }

    fun selectTab(tab: BoardTab) {
        _tab.value = tab
    }

    fun confirm(order: Order) = runAction { repository.confirm(order.id) }

    fun reject(order: Order, reason: CancelReason) = runAction { repository.reject(order.id, CancelOrderRequest(reason = reason)) }

    fun served(order: Order) = runAction { repository.served(order.id) }

    fun setFilter(filter: TimeAmountFilter) {
        _todayFilter.value = filter
    }


    fun cancel(order: Order, reason: CancelReason) = runAction { repository.cancel(order.id, CancelOrderRequest(reason = reason)) }
}

@Composable
fun WaiterOrdersScreen(viewModel: WaiterBoardViewModel, flow: BranchFlow?, onOpenOrder: (Order) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.tab.collectAsStateWithLifecycle()
    val todayFilter by viewModel.todayFilter.collectAsStateWithLifecycle()
    var rejecting by remember { mutableStateOf<Order?>(null) }
    var cancelling by remember { mutableStateOf<Order?>(null) }
    // "Bugün" is always there; the action tabs exist only if the branch's flow has that step.
    val tabs = listOfNotNull(
        BoardTab.TODAY,
        BoardTab.PENDING.takeIf { flow?.tableApproval != false },
        BoardTab.READY.takeIf { flow?.tableKitchen != false },
    )
    val tab = selectedTab.takeIf { it in tabs } ?: tabs.first()

    AppScreen(title = "Siparişler", actions = { RefreshAction(viewModel::refresh) }) {
        val board = state.data
        if (tabs.size > 1) {
            SegmentedSwitch(
                options = tabs.map { option ->
                    option to when (option) {
                        BoardTab.TODAY -> "Bugün (${board?.today?.size ?: 0})"
                        BoardTab.PENDING -> "Onay (${board?.pending?.size ?: 0})"
                        BoardTab.READY -> "Hazır (${board?.ready?.size ?: 0})"
                    }
                },
                selected = tab,
                onSelect = viewModel::selectTab,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) { data ->
            if (tab == BoardTab.TODAY) {
                TodayOrders(data.today, todayFilter, viewModel, onOpenOrder)
                return@LoadContent
            }
            val orders = if (tab == BoardTab.PENDING) data.pending else data.ready
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                if (orders.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (tab == BoardTab.PENDING) "Onay bekleyen sipariş yok" else "Servise hazır sipariş yok",
                            description = "Yeni siparişler her 10 saniyede kontrol edilir.",
                            icon = if (tab == BoardTab.PENDING) AppIcons.Notification else AppIcons.Kitchen,
                        )
                    }
                }
                items(orders, key = { it.id }) { order ->
                    OrderCard(order, onClick = { onOpenOrder(order) }) {
                        if (tab == BoardTab.PENDING) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlineButton("Reddet", onClick = { rejecting = order }, enabled = !state.busy, modifier = Modifier.weight(1f))
                                PrimaryButton("Onayla", onClick = { viewModel.confirm(order) }, enabled = !state.busy, icon = AppIcons.Check, modifier = Modifier.weight(1f))
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlineButton("İptal et", onClick = { cancelling = order }, enabled = !state.busy, modifier = Modifier.weight(1f))
                                PrimaryButton("Servis edildi", onClick = { viewModel.served(order) }, enabled = !state.busy, icon = AppIcons.Check, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    rejecting?.let { order ->
        CancelReasonDialog(
            title = "Neden reddediliyor?",
            confirmLabel = "Reddet",
            onSelect = { reason ->
                viewModel.reject(order, reason)
                rejecting = null
            },
            onDismiss = { rejecting = null },
        )
    }

    cancelling?.let { order ->
        CancelReasonDialog(
            title = "Neden iptal ediliyor?",
            onSelect = { reason ->
                viewModel.cancel(order, reason)
                cancelling = null
            },
            onDismiss = { cancelling = null },
        )
    }
}

/**
 * Every order of the day, newest first, one line each, narrowed by the hour and amount filter; a tap opens the
 * order with its bill and payments.
 */
private val Order.listStatus: ListStatus
    get() = when (status) {
        OrderStatus.CANCELLED, OrderStatus.REJECTED -> ListStatus.CANCELLED
        OrderStatus.SERVED, OrderStatus.PAID -> ListStatus.DONE
        else -> ListStatus.OPEN
    }

@Composable
private fun TodayOrders(orders: List<Order>, filter: TimeAmountFilter, viewModel: WaiterBoardViewModel, onOpenOrder: (Order) -> Unit) {
    val bounds = TimeAmountBounds.of(orders.mapNotNull { minuteOfDay(it.placedAt) }, orders.map { it.totalAmount })
    val visible = orders.filteredBy(filter, minuteOf = { minuteOfDay(it.placedAt) }, amountOf = { it.totalAmount }, statusOf = { it.listStatus })
    // Cancelled and rejected orders stay in the list but bring in no money.
    val revenue = visible.filter { it.status != OrderStatus.CANCELLED && it.status != OrderStatus.REJECTED }.sumOf { it.totalAmount }
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (orders.isEmpty()) {
            item { EmptyState("Bugün sipariş yok", "Günün siparişleri burada listelenir.", icon = AppIcons.Orders) }
            return@LazyColumn
        }
        item {
            TimeAmountFilterBar(
                filter = filter,
                bounds = bounds,
                summary = "${visible.size} sipariş · ${formatMoney(revenue)}",
                showLabel = "${visible.size} siparişi göster",
                onChange = viewModel::setFilter,
                withStatus = true,
            )
        }
        if (visible.isEmpty()) {
            item { EmptyState("Filtreye uyan sipariş yok", "Filtreleri gevşetin ya da sıfırlayın.", icon = AppIcons.Orders) }
        }
        items(visible, key = { it.id }) { order ->
            AppCard(onClick = { onOpenOrder(order) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(order.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "#${order.id} · ${formatTime(order.placedAt)} · " +
                                order.items.joinToString(", ") { if (it.quantity > 1) "${it.quantity}× ${it.productName}" else it.productName },
                            color = AppColors.Muted,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatMoney(order.totalAmount, order.currency), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        StatusChip(order.status.label, order.status.tone())
                    }
                }
            }
        }
    }
}
