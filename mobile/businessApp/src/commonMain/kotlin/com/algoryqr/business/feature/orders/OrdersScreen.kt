package com.algoryqr.business.feature.orders

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.business.data.Branch
import com.algoryqr.business.data.BusinessRepository
import com.algoryqr.business.data.IntegrationRepository
import com.algoryqr.business.data.Platform
import com.algoryqr.business.data.PlatformOrderAction
import com.algoryqr.business.feature.integrations.FeedOrder
import com.algoryqr.business.feature.integrations.OrderRow
import com.algoryqr.business.feature.integrations.OrderSheet
import com.algoryqr.business.feature.integrations.OrderStage
import com.algoryqr.business.feature.integrations.isActive
import com.algoryqr.shared.format.today
import com.algoryqr.shared.model.CancelOrderRequest
import com.algoryqr.shared.model.CancelReason
import com.algoryqr.shared.model.Order
import com.algoryqr.shared.model.OrderStatus
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.ChoiceChip
import com.algoryqr.shared.ui.CancelReasonDialog
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.OrderCard
import com.algoryqr.shared.ui.OutlineButton
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

enum class OrderFilter(val label: String, val matches: (OrderStatus) -> Boolean, val matchesStage: (OrderStage) -> Boolean) {
    ACTIVE("Aktif", { it.isOpen }, { it.isActive || it == OrderStage.UNKNOWN }),
    DONE("Tamamlanan", { it == OrderStatus.SERVED || it == OrderStatus.PAID }, { it == OrderStage.ON_THE_WAY || it == OrderStage.DELIVERED }),
    CANCELLED("İptal", { it == OrderStatus.CANCELLED || it == OrderStatus.REJECTED }, { it == OrderStage.CANCELLED }),
    ALL("Tümü", { true }, { true }),
}

/** Where an order came from; the delivery platforms get their own chip once the branch has any of their orders. */
enum class OrderChannel(val label: String, val platform: Platform?) {
    ALL("Tüm kanallar", null),
    IN_HOUSE("Masa / QR", null),
    UBER_EATS("Uber Eats", Platform.UBER_EATS),
    YEMEKSEPETI("Yemeksepeti", Platform.YEMEKSEPETI),
}

/** One row of the list: a table/QR order of the menu or an order a delivery platform sent. */
sealed interface OrderEntry {
    val key: String
    val placedAt: String?

    data class InHouse(val order: Order) : OrderEntry {
        override val key get() = "menu-${order.id}"
        override val placedAt get() = order.placedAt
    }

    data class Delivery(val feed: FeedOrder) : OrderEntry {
        override val key get() = "${feed.platform}-${feed.order.id}"
        override val placedAt get() = feed.order.packageCreatedAt ?: feed.order.updatedAt
    }

    fun matches(filter: OrderFilter): Boolean = when (this) {
        is InHouse -> filter.matches(order.status)
        is Delivery -> filter.matchesStage(feed.stage)
    }

    fun matches(channel: OrderChannel): Boolean = when (channel) {
        OrderChannel.ALL -> true
        OrderChannel.IN_HOUSE -> this is InHouse
        else -> this is Delivery && feed.platform == channel.platform
    }
}

class OrdersViewModel(
    private val repository: BusinessRepository,
    private val integrations: IntegrationRepository,
    private val branch: Branch,
) : LoadViewModel<List<OrderEntry>>(pollMillis = 20_000) {

    private val _filter = MutableStateFlow(OrderFilter.ACTIVE)
    val filter: StateFlow<OrderFilter> = _filter.asStateFlow()

    private val _channel = MutableStateFlow(OrderChannel.ALL)
    val channel: StateFlow<OrderChannel> = _channel.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): List<OrderEntry> = coroutineScope {
        val menuOrders = async { branch.primaryMenuId?.let { repository.orders(it) }.orEmpty() }
        val delivery = Platform.entries.map { platform -> async { deliveryOrders(platform) } }
        // qr-service also mixes platform tickets into the menu list, merchant-wide and without their actions;
        // they are taken from the platforms themselves instead, for this branch only.
        val inHouse = menuOrders.await()
            .filter { it.orderSource != "UBER_EATS" && it.orderSource != "YEMEKSEPETI" }
            .map { OrderEntry.InHouse(it) }
        (inHouse + delivery.awaitAll().flatten()).sortedByDescending { it.placedAt ?: "" }
    }

    /** A platform's recent orders when it is connected to this branch (or to every branch); a failing platform adds none. */
    private suspend fun deliveryOrders(platform: Platform): List<OrderEntry> = runCatching {
        val connection = integrations.connection(platform)
        if (connection?.isLive != true || (connection.branchId != null && connection.branchId != branch.id)) return emptyList()
        val since = today().minus(DatePeriod(days = 1)).toString()
        integrations.orders(platform)
            .filter { (it.packageCreatedAt ?: it.updatedAt ?: "") >= since }
            .map { OrderEntry.Delivery(FeedOrder(platform, it)) }
    }.getOrDefault(emptyList())

    fun selectFilter(filter: OrderFilter) {
        _filter.value = filter
    }

    fun selectChannel(channel: OrderChannel) {
        _channel.value = channel
    }

    fun cancel(order: Order, reason: CancelReason) {
        val menuId = order.menuId ?: branch.primaryMenuId ?: return
        runAction { repository.cancelOrder(menuId, order.id, CancelOrderRequest(reason = reason)) }
    }

    fun act(order: FeedOrder, action: PlatformOrderAction) {
        runAction { integrations.orderAction(order.platform, order.order.id, action) }
    }
}

@Composable
fun OrdersScreen(viewModel: OrdersViewModel, branchPicker: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    var cancelling by remember { mutableStateOf<Order?>(null) }
    var opened by remember { mutableStateOf<FeedOrder?>(null) }

    AppScreen(title = "Siparişler", actions = { RefreshAction(viewModel::refresh) }) {
        branchPicker()
        val entries = state.data.orEmpty()
        val inChannel = entries.filter { it.matches(channel) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            OrderFilter.entries.forEach { option ->
                val count = inChannel.count { it.matches(option) }
                ChoiceChip("${option.label} ($count)", selected = option == filter, onClick = { viewModel.selectFilter(option) })
            }
        }
        // Only worth a row when a delivery platform actually sends orders to this branch.
        val channels = OrderChannel.entries.filter { option ->
            option.platform == null || entries.any { it is OrderEntry.Delivery && it.feed.platform == option.platform }
        }
        if (channels.size > 2) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                channels.forEach { option ->
                    val count = entries.count { it.matches(option) && it.matches(filter) }
                    ChoiceChip("${option.label} ($count)", selected = option == channel, onClick = { viewModel.selectChannel(option) })
                }
            }
        }
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) {
            val visible = inChannel.filter { it.matches(filter) }
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Sipariş yok",
                            description = "Bugün ve dün bu filtrede gösterilecek sipariş bulunmuyor. Liste her 20 saniyede yenilenir.",
                            icon = AppIcons.Orders,
                        )
                    }
                }
                items(visible, key = { it.key }) { entry ->
                    when (entry) {
                        is OrderEntry.InHouse -> OrderCard(entry.order) {
                            if (entry.order.status.isOpen) {
                                OutlineButton("Siparişi iptal et", onClick = { cancelling = entry.order }, icon = AppIcons.Reject, enabled = !state.busy)
                            }
                        }
                        is OrderEntry.Delivery -> OrderRow(
                            entry.feed,
                            busy = state.busy,
                            onOpen = { opened = entry.feed },
                            onAction = { viewModel.act(entry.feed, it) },
                        )
                    }
                }
            }
        }
    }

    cancelling?.let { order ->
        CancelReasonDialog(
            title = "İptal nedeni",
            onSelect = { reason ->
                viewModel.cancel(order, reason)
                cancelling = null
            },
            onDismiss = { cancelling = null },
        )
    }
    opened?.let { order ->
        // Show the latest copy after an action or a poll, not the one that was tapped.
        val fresh = state.data?.firstNotNullOfOrNull { entry ->
            (entry as? OrderEntry.Delivery)?.feed?.takeIf { it.platform == order.platform && it.order.id == order.order.id }
        } ?: order
        OrderSheet(fresh, busy = state.busy, onAction = { viewModel.act(fresh, it) }, onDismiss = { opened = null })
    }
}
