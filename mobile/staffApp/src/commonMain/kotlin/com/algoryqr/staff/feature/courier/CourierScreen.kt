package com.algoryqr.staff.feature.courier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.shared.format.formatElapsed
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.model.DeliveryOrder
import com.algoryqr.shared.model.StoreOrderStatus
import com.algoryqr.shared.model.BranchFlow
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.IconTile
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.KeyValueRow
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SecondaryButton
import com.algoryqr.shared.ui.StatusChip
import com.algoryqr.shared.ui.tone
import com.algoryqr.staff.data.StaffRepository

/** Ready for pick-up: the kitchen has finished, or handed the bag to the courier. */
private val PICKUP_STATUSES = setOf(
    StoreOrderStatus.READY,
    StoreOrderStatus.KITCHEN_PREPARED,
    StoreOrderStatus.KITCHEN_DELIVERED_TO_COURIER,
)

class CourierViewModel(private val repository: StaffRepository) : LoadViewModel<List<DeliveryOrder>>(pollMillis = 15_000) {

    init {
        start()
    }

    // On-the-road orders first, then the ones waiting longest.
    override suspend fun fetch(): List<DeliveryOrder> =
        repository.courierOrders().sortedWith(compareByDescending<DeliveryOrder> { it.status == StoreOrderStatus.ON_THE_WAY }.thenBy { it.createdAt })

    fun dispatch(order: DeliveryOrder) = runAction { repository.dispatch(order.id) }

    fun deliver(order: DeliveryOrder) = runAction { repository.deliver(order.id) }
}

@Composable
fun CourierScreen(viewModel: CourierViewModel, flow: BranchFlow?) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScreen(title = "Teslimatlar", actions = { RefreshAction(viewModel::refresh) }) {
        if (flow != null && !flow.usesCourier) {
            AppCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("Bu şubenin sipariş akışında kurye teslimatı yok; paket siparişleri burada görünmez. Akışı işletme sahibi değiştirebilir.", color = AppColors.Amber.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) { orders ->
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                if (orders.isEmpty()) {
                    item {
                        EmptyState(
                            "Teslimat yok",
                            "Mutfak paketi hazırladığında sipariş burada görünür.",
                            icon = AppIcons.Courier,
                        )
                    }
                }
                items(orders, key = { it.id }) { order ->
                    DeliveryCard(
                        order,
                        busy = state.busy,
                        onDispatch = { viewModel.dispatch(order) },
                        onDeliver = { viewModel.deliver(order) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryCard(order: DeliveryOrder, busy: Boolean, onDispatch: () -> Unit, onDeliver: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(AppIcons.Delivery, order.status.tone())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(order.customerName ?: "Müşteri", style = MaterialTheme.typography.titleMedium)
                Text(
                    "#${order.orderNo ?: order.id} · ${formatElapsed(order.createdAt)}",
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            StatusChip(order.status.label, order.status.tone())
        }
        Spacer(Modifier.height(12.dp))
        val address = order.address
        address?.oneLine?.takeIf { it.isNotBlank() }?.let { line ->
            Row(verticalAlignment = Alignment.Top) {
                IconTile(AppIcons.Address, AppColors.Red, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(line, style = MaterialTheme.typography.bodyLarge)
                    address.directions?.takeIf { it.isNotBlank() }?.let {
                        Text("Tarif: $it", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        order.customerPhone?.let { KeyValueRow("Telefon", it) }
        KeyValueRow("Ödeme", paymentLabel(order.paymentMethod))
        KeyValueRow("Tutar", formatMoney(order.totalAmount, order.currency), valueColor = AppColors.AccentText)
        order.note?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            StatusChip("Not: $it", AppColors.Amber)
        }
        if (order.items.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = AppColors.Line)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                order.items.forEach { item ->
                    Text("${item.quantity}× ${item.productName}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        when {
            order.status in PICKUP_STATUSES -> {
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Yola çık", onClick = onDispatch, enabled = !busy, icon = AppIcons.Courier)
            }
            order.status == StoreOrderStatus.ON_THE_WAY -> {
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Teslim edildi", onClick = onDeliver, enabled = !busy, icon = AppIcons.Check)
            }
        }
    }
}

private fun paymentLabel(method: String?): String = when (method) {
    "CASH_ON_DELIVERY" -> "Kapıda nakit"
    "CARD_ON_DELIVERY" -> "Kapıda kart"
    null -> "—"
    else -> method
}
