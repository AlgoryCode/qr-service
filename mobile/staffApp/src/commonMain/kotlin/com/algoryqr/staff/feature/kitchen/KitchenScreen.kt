package com.algoryqr.staff.feature.kitchen

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.shared.model.Order
import com.algoryqr.shared.model.OrderStatus
import com.algoryqr.shared.model.BranchFlow
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppCard
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.OrderCard
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SecondaryButton
import com.algoryqr.shared.ui.SegmentedSwitch
import com.algoryqr.staff.data.StaffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class KitchenLane(val label: String, val statuses: Set<OrderStatus>) {
    NEW("Yeni", setOf(OrderStatus.SUBMITTED, OrderStatus.CONFIRMED)),
    PREPARING("Hazırlanıyor", setOf(OrderStatus.PREPARING)),
    READY("Hazır", setOf(OrderStatus.READY)),
}

class KitchenViewModel(private val repository: StaffRepository) : LoadViewModel<List<Order>>(pollMillis = 10_000) {

    private val _lane = MutableStateFlow(KitchenLane.NEW)
    val lane: StateFlow<KitchenLane> = _lane.asStateFlow()

    init {
        start()
    }

    // Oldest first: the kitchen works the queue in arrival order.
    override suspend fun fetch(): List<Order> = repository.kitchenOrders().sortedBy { it.placedAt }

    fun selectLane(lane: KitchenLane) {
        _lane.value = lane
    }

    fun startPreparing(order: Order) = runAction { repository.kitchenPreparing(order.id) }

    fun markReady(order: Order) = runAction { repository.kitchenReady(order.id) }
}

@Composable
fun KitchenScreen(viewModel: KitchenViewModel, flow: BranchFlow?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lane by viewModel.lane.collectAsStateWithLifecycle()

    AppScreen(title = "Mutfak", actions = { RefreshAction(viewModel::refresh) }) {
        val orders = state.data.orEmpty()
        SegmentedSwitch(
            options = KitchenLane.entries.map { option -> option to "${option.label} (${orders.count { it.status in option.statuses }})" },
            selected = lane,
            onSelect = viewModel::selectLane,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (flow != null && !flow.usesKitchen) {
            AppCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("Bu şubenin sipariş akışında mutfak adımı yok; siparişler mutfak ekranına düşmez. Akışı işletme sahibi değiştirebilir.", color = AppColors.Amber.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) { all ->
            val visible = all.filter { it.status in lane.statuses }
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            title = "${lane.label} sipariş yok",
                            description = "Mutfak kuyruğu her 10 saniyede yenilenir.",
                            icon = AppIcons.Kitchen,
                        )
                    }
                }
                items(visible, key = { it.id }) { order ->
                    OrderCard(order, showPrices = false) {
                        when (order.status) {
                            OrderStatus.SUBMITTED, OrderStatus.CONFIRMED ->
                                SecondaryButton("Hazırlamaya başla", onClick = { viewModel.startPreparing(order) }, enabled = !state.busy, icon = AppIcons.Preparing)
                            OrderStatus.PREPARING ->
                                PrimaryButton("Hazır", onClick = { viewModel.markReady(order) }, enabled = !state.busy, icon = AppIcons.Check)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
