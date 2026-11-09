package com.algoryqr.staff.feature.waiter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import com.algoryqr.shared.model.AddTipRequest
import com.algoryqr.shared.ui.AppTextField
import com.algoryqr.shared.ui.ChoiceChip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.format.formatTime
import com.algoryqr.shared.model.Bill
import com.algoryqr.shared.model.BillPayment
import com.algoryqr.shared.model.CancelOrderRequest
import com.algoryqr.shared.model.CancelReason
import com.algoryqr.shared.model.ChangePaymentMethodRequest
import com.algoryqr.shared.model.Order
import com.algoryqr.shared.model.PaymentMethod
import com.algoryqr.shared.model.TableBillStatus
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.CancelReasonDialog
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.KeyValueRow
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.OrderCard
import com.algoryqr.shared.ui.OutlineButton
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SectionLabel
import com.algoryqr.shared.ui.SegmentedSwitch
import com.algoryqr.shared.ui.StatusChip
import com.algoryqr.shared.ui.tone
import com.algoryqr.staff.data.StaffRepository

/** An order of today with the bill it went onto, when there is one. */
data class OrderDetail(val order: Order, val bill: Bill?)

/**
 * One payment as the waiter took it. qr-service stores a payment per bill line ("Ayran x2", "Su x1"), so the rows
 * taken together at the same moment with the same method are shown, and corrected, as one.
 */
data class PaymentGroup(val payments: List<BillPayment>) {
    val method: PaymentMethod? get() = payments.first().paymentMethod
    val tip: Boolean get() = payments.first().tip
    val amount: Double get() = payments.sumOf { it.amount }
    val paidAt: String? get() = payments.first().paidAt
    val label: String
        get() = if (tip) "Bahşiş" else payments.mapNotNull { it.itemSummary }.distinct().joinToString(", ").ifBlank { "Ödeme" }
}

fun Bill.paymentGroups(): List<PaymentGroup> =
    payments.groupBy { Triple(it.paidAt, it.paymentMethod, it.tip) }.values.map(::PaymentGroup)

class OrderDetailViewModel(
    private val repository: StaffRepository,
    private val orderId: Long,
) : LoadViewModel<OrderDetail>() {

    init {
        start()
    }

    override suspend fun fetch(): OrderDetail {
        // Staff have no single-order endpoint; the order is one of today's.
        val order = repository.todayOrders().firstOrNull { it.id == orderId } ?: error("Sipariş bulunamadı")
        val bill = order.billId?.let { id -> safeCall { repository.bill(id) }.getOrNull() }
        return OrderDetail(order, bill)
    }

    fun changeMethod(bill: Bill, group: PaymentGroup, method: PaymentMethod) = runAction {
        repository.changePaymentMethod(bill.id, ChangePaymentMethodRequest(group.payments.map { it.id }, method))
    }

    fun addTip(bill: Bill, amount: Double, method: PaymentMethod) = runAction {
        repository.addTip(bill.id, AddTipRequest(amount, method))
    }

    fun cancel(order: Order, reason: CancelReason) = runAction { repository.cancel(order.id, CancelOrderRequest(reason = reason)) }
}

@Composable
fun OrderDetailScreen(viewModel: OrderDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var changing by remember { mutableStateOf<PaymentGroup?>(null) }
    var cancelling by remember { mutableStateOf<Order?>(null) }
    var tipping by remember { mutableStateOf(false) }

    AppScreen(title = state.data?.order?.let { "Sipariş #${it.id}" } ?: "Sipariş", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) { detail ->
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                item {
                    OrderCard(detail.order) {
                        // Open orders only: once served or paid, qr-service no longer lets staff cancel them.
                        if (detail.order.status.isOpen) {
                            OutlineButton("Siparişi iptal et", onClick = { cancelling = detail.order }, icon = AppIcons.Reject, enabled = !state.busy)
                        }
                    }
                }
                detail.bill?.let { bill ->
                    item { SectionLabel("Adisyon") }
                    item { BillSummaryCard(bill, enabled = !state.busy, onChange = { changing = it }, onAddTip = { tipping = true }) }
                }
            }
            if (tipping) {
                detail.bill?.let { bill ->
                    AddTipDialog(
                        currency = bill.currency,
                        defaultMethod = bill.paymentMethod ?: PaymentMethod.CASH,
                        onDismiss = { tipping = false },
                        onAdd = { amount, method ->
                            viewModel.addTip(bill, amount, method)
                            tipping = false
                        },
                    )
                }
            }
            changing?.let { group ->
                val bill = detail.bill ?: return@let
                PaymentMethodDialog(
                    group = group,
                    currency = bill.currency,
                    onDismiss = { changing = null },
                    onSelect = { method ->
                        if (method != group.method) viewModel.changeMethod(bill, group, method)
                        changing = null
                    },
                )
            }
        }
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

/** The bill the order went onto: status, totals and each payment with "Değiştir" for its method. */
@Composable
private fun BillSummaryCard(bill: Bill, enabled: Boolean, onChange: (PaymentGroup) -> Unit, onAddTip: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bill.tableName ?: "Adisyon", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(formatTime(bill.openedAt), bill.closedAt?.let(::formatTime)).joinToString(" – "),
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            StatusChip(
                when (bill.status) {
                    TableBillStatus.OPEN -> "Açık"
                    TableBillStatus.CLOSED -> "Kapandı"
                    TableBillStatus.CANCELLED -> "İptal"
                    TableBillStatus.EMPTY -> "Boş"
                },
                bill.status.tone(),
            )
        }
        Spacer(Modifier.height(10.dp))
        KeyValueRow("Toplam", formatMoney(bill.totalAmount, bill.currency))
        if (bill.paidTotal > 0) KeyValueRow("Ödenen", formatMoney(bill.paidTotal, bill.currency), valueColor = AppColors.Green.content)
        if (bill.status == TableBillStatus.OPEN) KeyValueRow("Kalan", formatMoney(bill.remainingTotal, bill.currency), valueColor = AppColors.AccentText)
        val groups = bill.paymentGroups()
        if (groups.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Henüz ödeme alınmadı.", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        groups.forEach { group ->
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.Line)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${formatMoney(group.amount, bill.currency)} · ${group.method?.label ?: "—"}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${formatTime(group.paidAt)} · ${group.label}",
                        color = AppColors.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { onChange(group) }, enabled = enabled) {
                    Text("Değiştir", color = AppColors.AccentText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        // Guests sometimes leave the tip after paying; a closed bill takes it here.
        if (bill.status == TableBillStatus.CLOSED) {
            Spacer(Modifier.height(10.dp))
            OutlineButton("Bahşiş ekle", onClick = onAddTip, enabled = enabled, icon = AppIcons.Commission)
        }
    }
}

/** Tip amount with the usual round figures, the method, and "Vazgeç" bottom left, "Ekle" bottom right. */
@Composable
private fun AddTipDialog(currency: String?, defaultMethod: PaymentMethod, onDismiss: () -> Unit, onAdd: (Double, PaymentMethod) -> Unit) {
    var text by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(defaultMethod) }
    val amount = text.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.01 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = AppColors.Surface) {
            Column(Modifier.padding(top = 24.dp, bottom = 12.dp)) {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Bahşiş ekle", style = MaterialTheme.typography.titleLarge)
                    AppTextField(
                        value = text,
                        onValueChange = { value -> text = value.filter { it.isDigit() || it == ',' || it == '.' }.take(8) },
                        label = "Bahşiş tutarı (₺)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.focusRequester(focus),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(10, 20, 50, 100).forEach { value ->
                            ChoiceChip("₺$value", selected = amount == value.toDouble(), onClick = { text = value.toString() })
                        }
                    }
                    SegmentedSwitch(options = PaymentMethod.entries.map { it to it.label }, selected = method, onSelect = { method = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    TextButton(onClick = onDismiss) { Text("Vazgeç", color = AppColors.Muted) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { amount?.let { onAdd(it, method) } }, enabled = amount != null) {
                        Text(
                            amount?.let { "Ekle · ${formatMoney(it, currency)}" } ?: "Ekle",
                            color = if (amount != null) AppColors.AccentText else AppColors.Muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodDialog(group: PaymentGroup, currency: String?, onDismiss: () -> Unit, onSelect: (PaymentMethod) -> Unit) {
    var method by remember { mutableStateOf(group.method ?: PaymentMethod.CASH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text("Ödeme yöntemi", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    "${formatMoney(group.amount, currency)} · ${group.label}",
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                SegmentedSwitch(options = PaymentMethod.entries.map { it to it.label }, selected = method, onSelect = { method = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(method) }) { Text("Kaydet", color = AppColors.AccentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = AppColors.Muted) } },
    )
}
