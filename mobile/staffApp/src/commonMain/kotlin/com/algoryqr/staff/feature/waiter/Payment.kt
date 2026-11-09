package com.algoryqr.staff.feature.waiter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.algoryqr.shared.model.PayAmountRequest
import com.algoryqr.shared.format.formatTime
import com.algoryqr.shared.ui.StatusChip
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.model.Bill
import com.algoryqr.shared.model.BillItem
import com.algoryqr.shared.model.CloseBillRequest
import com.algoryqr.shared.model.PayItemLine
import com.algoryqr.shared.model.PayItemsRequest
import com.algoryqr.shared.model.PayShareRequest
import com.algoryqr.shared.model.PaymentMethod
import com.algoryqr.shared.model.SplitPreview
import com.algoryqr.shared.model.TableBillStatus
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.network.userMessage
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.AppTextField
import com.algoryqr.shared.ui.ChoiceChip
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.QuantityStepper
import com.algoryqr.shared.ui.SegmentedSwitch
import com.algoryqr.shared.ui.SwitchRow
import com.algoryqr.shared.ui.ToastHost
import com.algoryqr.staff.data.StaffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.round

/** What this payment takes; the switch at the top of the payment screen shows them in this order. */
enum class PayMode(val label: String) { FULL("Tamamı"), ITEMS("Ürün seç"), AMOUNT("Tutar"), SPLIT("Kişi başı") }

/** Same product at the same price, ordered in several rounds, shown and paid as one row. */
data class PayGroup(val key: String, val name: String, val unitPrice: Double, val items: List<BillItem>) {
    val unpaid: Int get() = items.sumOf { it.unpaidQuantity }
    val paid: Int get() = items.sumOf { it.paidQuantity }

    /** Spreads a quantity over the underlying bill lines, oldest first. */
    fun lines(quantity: Int): List<PayItemLine> {
        var left = quantity
        return items.mapNotNull { item ->
            val take = minOf(left, item.unpaidQuantity)
            left -= take
            take.takeIf { it > 0 }?.let { PayItemLine(item.id, it) }
        }
    }
}

fun Bill.payGroups(): List<PayGroup> =
    items.filter { it.unpaidQuantity > 0 }
        .groupBy { "${it.productName}|${it.unitPrice}|${it.note.orEmpty()}" }
        .map { (key, group) -> PayGroup(key, group.first().productName, group.first().unitPrice, group) }

/** What the waiter has picked on the payment screen; amounts follow from it and the bill. */
data class PaymentDraft(
    val mode: PayMode = PayMode.FULL,
    /** Method of the payment being taken, or of the last one. */
    val method: PaymentMethod = PaymentMethod.CASH,
    /** "Tutar": the amount typed by the waiter. */
    val amountText: String = "",
    val personCount: Int = 2,
    val split: SplitPreview? = null,
    val shareNumber: Int? = null,
    /** "Ürün ürün": [PayGroup.key] → quantity to pay now. */
    val itemQuantities: Map<String, Int> = emptyMap(),
    val tipOn: Boolean = false,
    val tipText: String = "",
    val paying: Boolean = false,
    /** Set when this screen's last payment closed the bill. */
    val closedNotice: String? = null,
    val notice: String? = null,
)

class PaymentViewModel(
    private val repository: StaffRepository,
    private val tableId: Long,
) : LoadViewModel<Bill>() {

    private val _draft = MutableStateFlow(PaymentDraft())
    val draft: StateFlow<PaymentDraft> = _draft.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): Bill {
        val bill = repository.tableBill(tableId)?.takeIf { it.status == TableBillStatus.OPEN }
            ?: error("Bu masada açık adisyon yok")
        // A split that already started fixes the mode and the person count.
        bill.splitPersonCount?.let { count ->
            _draft.update { it.copy(mode = PayMode.SPLIT, personCount = count) }
        }
        if (_draft.value.mode == PayMode.SPLIT) loadSplit(bill, _draft.value.personCount)
        return bill
    }

    private suspend fun loadSplit(bill: Bill, personCount: Int) {
        safeCall { repository.splitPreview(bill.id, personCount) }
            .onSuccess { preview ->
                _draft.update { draft ->
                    val keep = draft.shareNumber?.takeIf { number -> preview.shares.any { it.shareNumber == number && !it.paid } }
                    draft.copy(split = preview, shareNumber = keep ?: preview.shares.firstOrNull { !it.paid }?.shareNumber)
                }
            }
            .onFailure { error -> setActionError(error.userMessage()) }
    }

    fun selectMode(mode: PayMode) {
        val bill = state.value.data ?: return
        if (bill.splitPersonCount != null && mode != PayMode.SPLIT) return
        _draft.update { it.copy(mode = mode) }
        if (mode == PayMode.SPLIT && _draft.value.split == null) refreshSplit()
    }

    fun setAmountText(text: String) = _draft.update {
        it.copy(amountText = text.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(9))
    }

    fun setPersonCount(count: Int) {
        val bill = state.value.data ?: return
        if (bill.splitPersonCount != null) return
        _draft.update { it.copy(personCount = count.coerceIn(2, 20), shareNumber = null) }
        refreshSplit()
    }

    private fun refreshSplit() {
        val bill = state.value.data ?: return
        viewModelScope.launch { loadSplit(bill, _draft.value.personCount) }
    }

    fun selectShare(number: Int) = _draft.update { it.copy(shareNumber = number) }

    fun setItemQuantity(group: PayGroup, quantity: Int) = _draft.update { draft ->
        val next = quantity.coerceIn(0, group.unpaid)
        draft.copy(itemQuantities = if (next == 0) draft.itemQuantities - group.key else draft.itemQuantities + (group.key to next))
    }

    fun selectAllItems() {
        val bill = state.value.data ?: return
        _draft.update { draft ->
            draft.copy(itemQuantities = bill.payGroups().associate { it.key to it.unpaid })
        }
    }

    /** A tap on a product row: selects all of its unpaid pieces, or clears it when it was selected. */
    fun toggleItem(group: PayGroup) {
        val current = _draft.value.itemQuantities[group.key] ?: 0
        setItemQuantity(group, if (current > 0) 0 else group.unpaid)
    }

    fun setTipOn(on: Boolean) = _draft.update { it.copy(tipOn = on) }

    fun setTipText(text: String) = _draft.update { it.copy(tipText = text.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8)) }

    fun setTipPercent(base: Double, percent: Int) = _draft.update {
        it.copy(tipOn = true, tipText = formatPlain(round(base * percent) / 100.0))
    }

    fun dismissNotice() = _draft.update { it.copy(notice = null) }

    /** Amount this payment collects, before tip. */
    fun amountFor(bill: Bill, draft: PaymentDraft): Double = when (draft.mode) {
        PayMode.FULL -> bill.remainingTotal
        PayMode.AMOUNT -> draft.amountText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { round(it * 100) / 100 } ?: 0.0
        PayMode.SPLIT -> draft.split?.shares?.firstOrNull { it.shareNumber == draft.shareNumber }?.amount ?: 0.0
        PayMode.ITEMS -> bill.payGroups().sumOf { group -> (draft.itemQuantities[group.key] ?: 0) * group.unitPrice }
    }

    /** qr-service records a tip only with the payment that closes the bill. */
    fun closesBill(bill: Bill, draft: PaymentDraft): Boolean = when (draft.mode) {
        PayMode.FULL -> true
        PayMode.AMOUNT -> amountFor(bill, draft) >= bill.remainingTotal - 0.005
        PayMode.SPLIT -> draft.split?.shares?.count { !it.paid } == 1 && draft.shareNumber != null
        PayMode.ITEMS -> bill.payGroups().all { (draft.itemQuantities[it.key] ?: 0) >= it.unpaid }
    }

    fun tipAmount(draft: PaymentDraft): Double? =
        draft.tipText.replace(',', '.').toDoubleOrNull()?.takeIf { draft.tipOn && it > 0 }

    /** Takes the payment the screen shows with [method]: one tap on "Nakit" or "Kart". */
    fun pay(method: PaymentMethod) {
        val bill = state.value.data ?: return
        val draft = _draft.value.copy(method = method)
        if (draft.paying) return
        val amount = amountFor(bill, draft)
        if (amount <= 0 || amount > bill.remainingTotal + 0.005) return
        val closes = closesBill(bill, draft)
        val tip = tipAmount(draft).takeIf { closes }
        _draft.update { it.copy(paying = true, method = method) }
        viewModelScope.launch {
            safeCall {
                when (draft.mode) {
                    PayMode.FULL -> repository.closeBill(bill.id, CloseBillRequest(draft.method, tip != null, tip))
                    PayMode.AMOUNT -> repository.payAmount(bill.id, PayAmountRequest(amount, draft.method, tip != null, tip))
                    PayMode.SPLIT -> repository.payShare(
                        bill.id,
                        PayShareRequest(draft.personCount, draft.shareNumber ?: return@safeCall bill, draft.method, tip != null, tip),
                    )
                    PayMode.ITEMS -> repository.payItems(
                        bill.id,
                        PayItemsRequest(
                            draft.method,
                            bill.payGroups().flatMap { group -> group.lines(draft.itemQuantities[group.key] ?: 0) },
                            tip != null,
                            tip,
                        ),
                    )
                }
            }.onSuccess { result ->
                val paid = "${formatMoney(amount, bill.currency)} ${draft.method.label.lowercase()}"
                if (result.status != TableBillStatus.OPEN) {
                    val tipText = tip?.let { " + ${formatMoney(it, bill.currency)} bahşiş" }.orEmpty()
                    _draft.update { it.copy(paying = false, closedNotice = "Hesap kapatıldı · $paid$tipText") }
                } else {
                    _draft.update {
                        it.copy(
                            paying = false,
                            amountText = "",
                            itemQuantities = emptyMap(),
                            shareNumber = null,
                            notice = "$paid alındı · kalan ${formatMoney(result.remainingTotal, result.currency)}",
                        )
                    }
                    refresh()
                }
            }.onFailure { error ->
                _draft.update { it.copy(paying = false) }
                setActionError(error.userMessage())
            }
        }
    }
}

private fun formatPlain(value: Double): String {
    val cents = round(value * 100).toLong()
    return if (cents % 100 == 0L) (cents / 100).toString() else "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
}

/**
 * The bill to settle on one screen: what is left, a switch for what to take (all of it, chosen products, a typed
 * amount or one person's share), and at the bottom one tap on "Nakit" or "Kart" takes it.
 */
@Composable
fun PaymentScreen(viewModel: PaymentViewModel, tableName: String, onBack: () -> Unit, onClosed: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    LaunchedEffect(draft.closedNotice) { draft.closedNotice?.let(onClosed) }
    // "Nakit" / "Kart" only asks; the payment is taken once the waiter confirms it.
    var confirming by remember { mutableStateOf<PaymentMethod?>(null) }

    AppScreen(title = "$tableName · Ödeme", onBack = onBack) {
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        ToastHost(draft.notice, onDismiss = viewModel::dismissNotice, modifier = Modifier.fillMaxSize()) {
            LoadContent(state, onRetry = viewModel::refresh) { bill ->
                val amount = viewModel.amountFor(bill, draft)
                val closes = viewModel.closesBill(bill, draft)
                val splitting = bill.splitPersonCount != null
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                        verticalArrangement = ItemSpacing,
                        modifier = Modifier.weight(1f),
                    ) {
                        item { RemainingCard(bill) }
                        item {
                            // A split that already started goes on share by share; the other ways come back after it.
                            if (splitting) {
                                Text(
                                    "Kişi başı ödeme sürüyor · ${bill.splitPersonCount} kişi",
                                    color = AppColors.Muted,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                SegmentedSwitch(
                                    options = PayMode.entries.map { it to it.label },
                                    selected = draft.mode,
                                    onSelect = viewModel::selectMode,
                                )
                            }
                        }
                        when (draft.mode) {
                            PayMode.FULL -> item { BillLines(bill) }
                            PayMode.ITEMS -> {
                                val unpaid = bill.payGroups()
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Ödenecek ürünlere dokunun",
                                            color = AppColors.Muted,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = viewModel::selectAllItems) { Text("Tümünü seç", color = AppColors.AccentText) }
                                    }
                                }
                                if (unpaid.isEmpty()) {
                                    item { EmptyState("Ödenmemiş ürün yok", "Bu adisyonun tüm ürünleri ödendi.", icon = AppIcons.Approve) }
                                }
                                items(unpaid, key = { it.key }) { group ->
                                    ItemPayRow(
                                        group = group,
                                        quantity = draft.itemQuantities[group.key] ?: 0,
                                        currency = bill.currency,
                                        onToggle = { viewModel.toggleItem(group) },
                                        onQuantity = { viewModel.setItemQuantity(group, it) },
                                    )
                                }
                            }
                            PayMode.AMOUNT -> item { AmountEntry(bill, draft, amount > bill.remainingTotal + 0.005, viewModel) }
                            PayMode.SPLIT -> item { SplitPayment(bill, draft, viewModel) }
                        }
                        if (closes && amount > 0) {
                            item { TipSection(draft, amount, closes, viewModel) }
                        }
                    }
                    val tip = viewModel.tipAmount(draft).takeIf { closes }
                    PayBar(bill, draft, amount, closes, tip, onPay = { confirming = it })
                    confirming?.let { method ->
                        ConfirmPaymentDialog(
                            bill = bill,
                            method = method,
                            amount = amount,
                            tip = tip,
                            closes = closes,
                            onDismiss = { confirming = null },
                            onConfirm = {
                                confirming = null
                                viewModel.pay(method)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemainingCard(bill: Bill) {
    AppCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Kalan", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                Text(formatMoney(bill.remainingTotal, bill.currency), style = MaterialTheme.typography.headlineMedium, color = AppColors.AccentText)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Toplam ${formatMoney(bill.totalAmount, bill.currency)}", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                if (bill.paidTotal > 0) {
                    Text("Ödenen ${formatMoney(bill.paidTotal, bill.currency)}", color = AppColors.Green.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val payments = bill.paymentGroups().filter { !it.tip }
        if (payments.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = AppColors.Line)
            payments.forEach { payment ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        listOfNotNull(formatTime(payment.paidAt), payment.method?.label, payment.label).joinToString(" · "),
                        color = AppColors.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatMoney(payment.amount, bill.currency), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Every line of the bill; paid quantities are marked so the waiter sees what is still open. */
@Composable
private fun BillLines(bill: Bill) {
    val lines = bill.items.groupBy { "${it.productName}|${it.unitPrice}|${it.note.orEmpty()}" }.values
    AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        if (lines.isEmpty()) {
            Text("Adisyonda ürün yok.", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
        }
        lines.forEachIndexed { index, group ->
            if (index > 0) HorizontalDivider(color = AppColors.Line)
            val first = group.first()
            val quantity = group.sumOf { it.quantity }
            val paid = group.sumOf { it.paidQuantity }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("$quantity× ${first.productName}", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = listOfNotNull(
                        "${formatMoney(first.unitPrice, bill.currency)} / adet".takeIf { quantity > 1 },
                        first.note?.takeIf { it.isNotBlank() }?.let { "Not: $it" },
                    )
                    if (detail.isNotEmpty()) {
                        Text(detail.joinToString(" · "), color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (paid > 0) {
                    StatusChip(if (paid >= quantity) "Ödendi" else "$paid ödendi", AppColors.Green)
                    Spacer(Modifier.width(8.dp))
                }
                Text(formatMoney(group.sumOf { it.lineTotal }, bill.currency), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * Pinned footer: how much this takes and what it leaves, then one button per method. A tap on "Nakit" or "Kart"
 * takes the payment straight away; until there is something to take it says what is missing.
 */
@Composable
private fun PayBar(bill: Bill, draft: PaymentDraft, amount: Double, closes: Boolean, tip: Double?, onPay: (PaymentMethod) -> Unit) {
    val tooMuch = amount > bill.remainingTotal + 0.005
    val ready = amount > 0 && !tooMuch
    Column(Modifier.fillMaxWidth().background(AppColors.Surface).imePadding()) {
        HorizontalDivider(color = AppColors.Line)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            ready -> if (closes) "Hesap kapanır" else "Kalan ${formatMoney(bill.remainingTotal - amount, bill.currency)} olur"
                            tooMuch -> "Tutar kalandan fazla"
                            draft.mode == PayMode.AMOUNT -> "Tutar girin"
                            draft.mode == PayMode.ITEMS -> "Ürün seçin"
                            else -> "Pay seçin"
                        },
                        color = if (tooMuch) AppColors.Danger else AppColors.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    tip?.let {
                        Text("+ ${formatMoney(it, bill.currency)} bahşiş", color = AppColors.Amber.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    formatMoney(if (ready) amount else 0.0, bill.currency),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (ready) AppColors.Ink else AppColors.Muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaymentMethod.entries.forEach { method ->
                    PrimaryButton(
                        text = method.label,
                        onClick = { onPay(method) },
                        enabled = ready && !draft.paying,
                        loading = draft.paying && draft.method == method,
                        icon = if (method == PaymentMethod.CASH) AppIcons.Cash else AppIcons.Card,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** "Ödeme alınacak": amount, method and what it leaves, with "Vazgeç" bottom left and "Onayla" bottom right. */
@Composable
private fun ConfirmPaymentDialog(
    bill: Bill,
    method: PaymentMethod,
    amount: Double,
    tip: Double?,
    closes: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = AppColors.Surface) {
            Column(Modifier.padding(top = 24.dp, bottom = 12.dp)) {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ödeme alınacak", style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (method == PaymentMethod.CASH) AppIcons.Cash else AppIcons.Card,
                            contentDescription = null,
                            tint = AppColors.Ink,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(method.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(formatMoney(amount, bill.currency), style = MaterialTheme.typography.headlineSmall)
                    }
                    tip?.let {
                        Text("+ ${formatMoney(it, bill.currency)} bahşiş", color = AppColors.Amber.content, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        if (closes) "Hesap kapanır." else "Kalan ${formatMoney(bill.remainingTotal - amount, bill.currency)} olur.",
                        color = AppColors.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Onaylıyor musunuz?", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    TextButton(onClick = onDismiss) { Text("Vazgeç", color = AppColors.Muted) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onConfirm) { Text("Onayla", color = AppColors.AccentText) }
                }
            }
        }
    }
}

/** "Tutar": the amount typed, with the halves the waiter reaches for most. */
@Composable
private fun AmountEntry(bill: Bill, draft: PaymentDraft, tooMuch: Boolean, viewModel: PaymentViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column {
        AppTextField(
            draft.amountText,
            viewModel::setAmountText,
            "Alınacak tutar (₺)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.focusRequester(focus),
        )
        if (tooMuch) {
            Text(
                "Kalan tutar ${formatMoney(bill.remainingTotal, bill.currency)}",
                color = AppColors.Danger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
            ChoiceChip("Yarısı", selected = false, onClick = { viewModel.setAmountText(formatPlain(round(bill.remainingTotal * 50) / 100.0)) })
            ChoiceChip("Kalanın tamamı", selected = false, onClick = { viewModel.setAmountText(formatPlain(bill.remainingTotal)) })
        }
    }
}

@Composable
private fun SplitPayment(bill: Bill, draft: PaymentDraft, viewModel: PaymentViewModel) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kişi sayısı", style = MaterialTheme.typography.titleMedium)
                Text("Kalan tutar eşit paylara bölünür", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (bill.splitPersonCount == null) {
                QuantityStepper(draft.personCount, onDecrease = { viewModel.setPersonCount(draft.personCount - 1) }, onIncrease = { viewModel.setPersonCount(draft.personCount + 1) })
            } else {
                Text("${bill.splitPersonCount} kişi", style = MaterialTheme.typography.titleMedium)
            }
        }
        val shares = draft.split?.shares.orEmpty()
        if (shares.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                shares.forEach { share ->
                    ShareChip(
                        number = share.shareNumber,
                        amount = formatMoney(share.amount, bill.currency),
                        paid = share.paid,
                        selected = share.shareNumber == draft.shareNumber,
                        onClick = { viewModel.selectShare(share.shareNumber) },
                    )
                }
            }
        }
    }
}

/** One person's share: number, amount, and "Ödendi" once taken. */
@Composable
private fun ShareChip(number: Int, amount: String, paid: Boolean, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .clip(shape)
            .background(
                when {
                    paid -> AppColors.Green.container
                    selected -> AppColors.Red.container
                    else -> AppColors.Surface
                },
            )
            .border(if (selected) 2.dp else 1.dp, if (selected) AppColors.Accent else AppColors.Line, shape)
            .clickable(enabled = !paid, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(if (paid) AppColors.Green.content else if (selected) AppColors.Accent else AppColors.Subtle),
        ) {
            if (paid) {
                Icon(AppIcons.Check, contentDescription = "Ödendi", tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                Text(number.toString(), color = if (selected) Color.White else AppColors.Ink, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(if (paid) "Ödendi" else amount, style = MaterialTheme.typography.labelLarge, color = if (paid) AppColors.Green.content else AppColors.Ink)
    }
}

/** One product to pay: a tap selects all its unpaid pieces (or clears it), the stepper picks fewer. */
@Composable
private fun ItemPayRow(group: PayGroup, quantity: Int, currency: String?, onToggle: () -> Unit, onQuantity: (Int) -> Unit) {
    val selected = quantity > 0
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) AppColors.Red.container else AppColors.Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) AppColors.Accent else AppColors.Line, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) AppColors.Accent else AppColors.Surface)
                .border(1.5.dp, if (selected) AppColors.Accent else AppColors.Line, CircleShape),
        ) {
            if (selected) Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatMoney(group.unitPrice, currency)} · ${group.unpaid} ödenmedi" +
                    if (group.paid > 0) " · ${group.paid} ödendi" else "",
                color = AppColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Only worth a stepper when there is more than one piece to choose from.
        if (selected && group.unpaid > 1) {
            QuantityStepper(quantity, onDecrease = { onQuantity(quantity - 1) }, onIncrease = { onQuantity(quantity + 1) })
        } else if (selected) {
            Text(formatMoney(group.unitPrice, currency), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TipSection(draft: PaymentDraft, amount: Double, closes: Boolean, viewModel: PaymentViewModel) {
    AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        SwitchRow(
            title = "Bahşiş",
            checked = draft.tipOn && closes,
            onCheckedChange = viewModel::setTipOn,
            description = if (closes) "Hesabı kapatan ödemeyle birlikte kaydedilir" else "Bahşiş, hesabı kapatan son ödemede girilir",
            icon = AppIcons.Commission,
            tone = AppColors.Amber,
            enabled = closes,
        )
        if (draft.tipOn && closes) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(5, 10, 15).forEach { percent ->
                    ChoiceChip("%$percent", selected = false, onClick = { viewModel.setTipPercent(amount, percent) })
                }
            }
            AppTextField(
                draft.tipText,
                viewModel::setTipText,
                "Bahşiş tutarı (₺)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}
