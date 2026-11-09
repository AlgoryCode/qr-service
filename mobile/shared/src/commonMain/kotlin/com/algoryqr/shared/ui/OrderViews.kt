package com.algoryqr.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.algoryqr.shared.format.elapsedMinutes
import com.algoryqr.shared.format.formatElapsed
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.format.formatTime
import com.algoryqr.shared.model.CancelReason
import com.algoryqr.shared.model.Order
import com.algoryqr.shared.model.OrderStatus
import com.algoryqr.shared.model.StoreOrderStatus
import com.algoryqr.shared.model.TableBillStatus

@Composable
@ReadOnlyComposable
fun OrderStatus.tone(): Tone = when (this) {
    OrderStatus.SUBMITTED -> AppColors.Red
    OrderStatus.CONFIRMED -> AppColors.Blue
    OrderStatus.PREPARING -> AppColors.Amber
    OrderStatus.READY -> AppColors.Green
    OrderStatus.SERVED, OrderStatus.PAID -> AppColors.Slate
    OrderStatus.REJECTED, OrderStatus.CANCELLED -> AppColors.Rose
    OrderStatus.DRAFT -> AppColors.Slate
}

@Composable
@ReadOnlyComposable
fun StoreOrderStatus.tone(): Tone = when (this) {
    StoreOrderStatus.PENDING -> AppColors.Red
    StoreOrderStatus.CONFIRMED -> AppColors.Blue
    StoreOrderStatus.PREPARING, StoreOrderStatus.KITCHEN_PREPARING -> AppColors.Amber
    StoreOrderStatus.READY, StoreOrderStatus.KITCHEN_PREPARED,
    StoreOrderStatus.KITCHEN_DELIVERED_TO_COURIER, StoreOrderStatus.KITCHEN_DELIVERED_TO_WAITER -> AppColors.Green
    StoreOrderStatus.ON_THE_WAY -> AppColors.Blue
    StoreOrderStatus.DELIVERED, StoreOrderStatus.UNKNOWN -> AppColors.Slate
    StoreOrderStatus.REJECTED, StoreOrderStatus.CANCELLED -> AppColors.Rose
}

@Composable
@ReadOnlyComposable
fun TableBillStatus?.tone(): Tone = when (this) {
    TableBillStatus.OPEN -> AppColors.Red
    TableBillStatus.CLOSED -> AppColors.Green
    else -> AppColors.Slate
}

/**
 * Order card shared by the boards: table, time, status, the item lines and a slot for the actions the current
 * screen allows. Orders waiting over 15 minutes show their age in red so they stand out on a busy board.
 */
@Composable
fun OrderCard(
    order: Order,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showPrices: Boolean = true,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (order.tableId != null) AppIcons.Table else AppIcons.Orders, order.status.tone())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(order.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val late = order.status.isOpen && elapsedMinutes(order.placedAt) >= 15
                Text(
                    "#${order.id} · ${formatTime(order.placedAt)} · ${formatElapsed(order.placedAt)}",
                    color = if (late) AppColors.Danger else AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            StatusChip(order.status.label, order.status.tone())
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = AppColors.Line)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            order.items.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${item.quantity}×", fontWeight = FontWeight.SemiBold, color = AppColors.AccentText, modifier = Modifier.width(34.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.productName, style = MaterialTheme.typography.bodyLarge)
                        val options = item.selectedOptions.mapNotNull { it.optionName }
                        if (options.isNotEmpty()) {
                            Text(options.joinToString(", "), color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        item.note?.takeIf { it.isNotBlank() }?.let {
                            Text("Not: $it", color = AppColors.Amber.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (showPrices) {
                        Text(formatMoney(item.lineTotal, order.currency), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        listOfNotNull(
            order.note?.takeIf { it.isNotBlank() }?.let { "Sipariş notu: $it" },
            order.waiterNote?.takeIf { it.isNotBlank() }?.let { "Garson notu: $it" },
            order.kitchenNote?.takeIf { it.isNotBlank() }?.let { "Mutfak notu: $it" },
        ).forEach { note ->
            Spacer(Modifier.height(6.dp))
            StatusChip(note, AppColors.Amber)
        }
        if (showPrices) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.waiterName?.let { "Garson: $it" } ?: "", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(formatMoney(order.totalAmount, order.currency), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions()
        }
    }
}

/**
 * Reason list shown before cancelling or rejecting an order or a bill; qr-service keeps the reason for the reports.
 * Picking a reason only selects it: nothing is cancelled until [confirmLabel] is pressed, so a stray tap is harmless.
 */
@Composable
fun CancelReasonDialog(
    title: String,
    onSelect: (CancelReason) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "İptal et",
) {
    var selected by remember { mutableStateOf<CancelReason?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = AppColors.Surface) {
            Column(Modifier.padding(top = 24.dp, bottom = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(12.dp))
                Column(Modifier.selectableGroup()) {
                    CancelReason.entries.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selected == reason, role = Role.RadioButton, onClick = { selected = reason })
                                .padding(horizontal = 12.dp),
                        ) {
                            RadioButton(
                                selected = selected == reason,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = AppColors.Danger),
                                modifier = Modifier.padding(12.dp),
                            )
                            Text(reason.label, color = AppColors.Ink, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    TextButton(onClick = onDismiss) { Text("Vazgeç", color = AppColors.Muted) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { selected?.let(onSelect) }, enabled = selected != null) {
                        Text(confirmLabel, color = if (selected != null) AppColors.Danger else AppColors.Muted)
                    }
                }
            }
        }
    }
}
