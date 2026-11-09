package com.algoryqr.staff.feature.waiter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.shared.format.formatElapsed
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.format.formatTime
import com.algoryqr.shared.model.Bill
import com.algoryqr.shared.model.BillItem
import com.algoryqr.shared.model.Order
import com.algoryqr.shared.model.OrderStatus
import com.algoryqr.shared.model.TableBillStatus
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.CancelReasonDialog
import com.algoryqr.shared.model.CancelReason
import com.algoryqr.shared.model.CancelOrderRequest
import com.algoryqr.shared.model.PaymentMethod
import com.algoryqr.shared.model.CloseBillRequest
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.KeyValueRow
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.OrderCard
import com.algoryqr.shared.ui.OutlineButton
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SectionLabel
import com.algoryqr.shared.ui.ToastHost
import com.algoryqr.shared.ui.Tone
import com.algoryqr.shared.ui.StatusChip
import com.algoryqr.shared.ui.tone
import com.algoryqr.staff.data.CartDrafts
import com.algoryqr.staff.data.StaffRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [pastOrders]: today's earlier orders of the table grouped by their closed bill; the bill details behind them
 * are only fetched when the waiter opens the history.
 * [unsentOrders]: orders on the open bill that never went through the kitchen although they contain kitchen
 * products ("Adisyona ekle" while the kitchen runs); their bill lines get the kitchen icon.
 */
data class TableDetail(
    val orders: List<Order>,
    val bill: Bill?,
    val pastOrders: Map<Long?, List<Order>> = emptyMap(),
    val unsentOrders: List<Order> = emptyList(),
    val skipKitchenProducts: Set<Long> = emptySet(),
) {
    val unsentOrderIds: Set<Long> get() = unsentOrders.map { it.id }.toSet()
}

/** One earlier guest of the day. Without the bill details (they failed to load) it falls back to its orders. */
data class PastBill(val bill: Bill?, val billId: Long?, val orders: List<Order>) {
    val total: Double
        get() = bill?.totalAmount ?: orders.filter { it.status == OrderStatus.SERVED || it.status == OrderStatus.PAID }.sumOf { it.totalAmount }
    val openedAt: String? get() = bill?.openedAt ?: orders.minOfOrNull { it.placedAt.orEmpty() }
}

class TableDetailViewModel(
    private val repository: StaffRepository,
    private val drafts: CartDrafts,
    private val tableId: Long,
) : LoadViewModel<TableDetail>(pollMillis = 15_000) {

    /** Products of "Mutfağa gitmez" categories; loaded once, they never need the kitchen. */
    private var skipKitchenProducts: Set<Long>? = null

    init {
        start()
    }

    override suspend fun fetch(): TableDetail = coroutineScope {
        val catalogCall = async {
            skipKitchenProducts ?: safeCall { repository.catalog(tableId) }.getOrNull()
                ?.products?.filter { it.skipKitchen }?.map { it.productId }?.toSet()
                ?.also { skipKitchenProducts = it }
        }
        val ordersCall = async { repository.tableOrders(tableId) }
        val billCall = async { repository.tableBill(tableId) }
        val orders = ordersCall.await().sortedByDescending { it.placedAt }
        val bill = billCall.await()
        val currentBillId = bill?.takeIf { it.status == TableBillStatus.OPEN }?.id
        // Earlier guests today: one entry per closed bill.
        val past = orders
            .filter { !it.status.isOpen && it.billId != currentBillId }
            .groupBy { it.billId }
        val skip = catalogCall.await().orEmpty()
        val unsent = orders.filter { order ->
            currentBillId != null && order.billId == currentBillId &&
                order.status == OrderStatus.SERVED && order.preparedAt == null &&
                order.items.any { it.productId !in skip }
        }
        TableDetail(orders, bill, past, unsent, skip)
    }

    /** "Mutfağa gönder" on the bill: forwards every order that skipped the kitchen. */
    fun sendUnsent(orders: List<Order>) = runAction(
        onSuccess = { showNotice("Mutfağa gönderildi") },
    ) {
        orders.forEach { repository.sendToKitchen(it.id) }
    }

    fun confirm(order: Order) = runAction { repository.confirm(order.id) }

    fun served(order: Order) = runAction { repository.served(order.id) }

    fun cancel(order: Order, reason: CancelReason) = runAction(
        onSuccess = { showNotice("Sipariş iptal edildi") },
    ) {
        repository.cancel(order.id, CancelOrderRequest(reason = reason))
    }

    /**
     * Takes [count] pieces of a merged bill line off the bill. The line may span several bill items (one per round);
     * the latest round goes first and paid pieces always stay.
     */
    fun removeFromBill(bill: Bill, items: List<BillItem>, count: Int) = runAction(
        onSuccess = { showNotice("$count ürün adisyondan çıkarıldı") },
    ) {
        var left = count
        for (item in items.sortedByDescending { it.id }) {
            if (left == 0) break
            val take = minOf(left, item.quantity - item.paidQuantity)
            if (take <= 0) continue
            repository.updateItemQuantity(bill.id, item.id, item.quantity - take)
            left -= take
        }
    }

    /** Short confirmation shown on the table screen, e.g. after the bill was closed. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun showNotice(message: String) {
        _notice.value = message
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /** Cancels the whole bill with its orders; only offered while nothing has been paid. */
    fun cancelBill(bill: Bill, reason: CancelReason) = runAction(
        onSuccess = {
            onBillClosed()
            showNotice("Adisyon iptal edildi")
        },
    ) {
        repository.cancelBill(bill.id, CancelOrderRequest(reason = reason))
    }

    /** Closes a bill with nothing left to pay (all paid, or emptied by cancellations) and frees the table. */
    fun closeTable(bill: Bill) = runAction(
        onSuccess = {
            onBillClosed()
            showNotice("Masa kapatıldı")
        },
    ) {
        // qr-service wants a method even when nothing is left to collect; no payment is recorded for it.
        repository.closeBill(bill.id, CloseBillRequest(bill.paymentMethod ?: PaymentMethod.CASH))
    }

    /** Called when the payment screen closed the bill: the next guests start with an empty cart. */
    fun onBillClosed() {
        drafts.clear(tableId)
    }

    private var seen = false

    /** Coming back from the product picker shows what was just added instead of waiting for the next poll. */
    fun onResume() {
        if (seen) refresh() else seen = true
    }
}

/**
 * The table in one place: the open bill on top, what the waiter has to do below, and one footer that either opens a
 * bill on an empty table or adds products and closes the bill on a busy one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDetailScreen(
    viewModel: TableDetailViewModel,
    tableName: String,
    tableKitchen: Boolean,
    onBack: () -> Unit,
    onAddItems: () -> Unit,
    onTakePayment: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val pastCount = state.data?.pastOrders?.size ?: 0
    var cancelling by remember { mutableStateOf<Order?>(null) }
    var cancellingBill by remember { mutableStateOf<Bill?>(null) }
    var removing by remember { mutableStateOf<Pair<Bill, BillLine>?>(null) }
    var editing by remember { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }

    AppScreen(title = tableName, onBack = onBack, actions = {
        // Always there, also on a busy table; the count only shows once earlier guests left today.
        ActionPill("Geçmiş", AppIcons.Time, AppColors.Amber, onClick = onOpenHistory, badge = pastCount.takeIf { it > 0 })
        Spacer(Modifier.width(12.dp))
    }) {
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        // The confirmation floats over the content instead of pushing it down.
        ToastHost(notice, onDismiss = viewModel::dismissNotice, modifier = Modifier.fillMaxSize()) {
        LoadContent(state, onRetry = viewModel::refresh) { detail ->
            val bill = detail.bill?.takeIf { it.status == TableBillStatus.OPEN }
            // A closed bill belongs to the previous guests; only the current visit's orders are listed.
            val visit = detail.orders.filter { it.status.isOpen || (bill != null && it.billId == bill.id) }
            val board = TableBoard.of(visit, tableKitchen)
            val free = bill == null && detail.orders.none { it.status.isOpen }
            Column(Modifier.fillMaxSize()) {
                // The screen polls on its own; pulling down refreshes right away.
                PullToRefreshBox(
                    isRefreshing = state.loading && state.data != null,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                ) {
                LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing, modifier = Modifier.fillMaxSize()) {
                    when {
                        bill != null -> item {
                            BillCard(
                                bill = bill,
                                // The kitchen marks only matter while the branch runs the kitchen.
                                unsentOrderIds = if (tableKitchen) detail.unsentOrderIds else emptySet(),
                                skipKitchenProducts = detail.skipKitchenProducts,
                                busy = state.busy,
                                editing = editing,
                                onToggleEditing = { editing = !editing },
                                onCancelBill = { cancellingBill = bill },
                                onSendUnsent = { viewModel.sendUnsent(detail.unsentOrders) },
                                onRemoveLine = { removing = bill to it },
                            )
                        }
                        free -> item {
                            EmptyState(
                                "Masa boş",
                                "Adisyon açıp ürünleri seçin; ilk siparişle adisyon açılır.",
                                icon = AppIcons.Table,
                                action = { ActionPill("Adisyon aç", AppIcons.Add, AppColors.Green, onClick = onAddItems) },
                            )
                        }
                    }
                    // What the waiter has to do now, as full cards with their button.
                    if (board.actionable.isNotEmpty()) {
                        item { SectionLabel("Sizi bekleyen") }
                        items(board.actionable, key = { it.id }) { order ->
                            OrderCard(order) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlineButton("İptal et", onClick = { cancelling = order }, enabled = !state.busy, modifier = Modifier.weight(1f))
                                    if (order.status == OrderStatus.SUBMITTED) {
                                        PrimaryButton("Onayla", onClick = { viewModel.confirm(order) }, enabled = !state.busy, icon = AppIcons.Check, modifier = Modifier.weight(1f))
                                    } else {
                                        PrimaryButton("Servis edildi", onClick = { viewModel.served(order) }, enabled = !state.busy, icon = AppIcons.Check, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    if (board.inKitchen.isNotEmpty()) {
                        item { SectionLabel("Mutfakta") }
                        item {
                            AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                                board.inKitchen.forEach { order ->
                                    CompactOrderRow(order, showElapsed = true, onCancel = { cancelling = order }.takeIf { !state.busy })
                                }
                            }
                        }
                    }
                    if (board.done.isNotEmpty()) {
                        item { DoneOrders(board.done) }
                    }
                }
                }
                // A bill keeps its two everyday actions at the bottom and a free table has "Adisyon aç" on its card;
                // orders still open without a bill show neither, so the footer keeps a way to start one.
                if (bill != null) {
                    BillActionBar(bill, state.busy, onAddItems = onAddItems, onPay = onTakePayment, onCloseTable = { viewModel.closeTable(bill) })
                } else if (!free) {
                    TableFooter(onAddItems = onAddItems)
                }
            }
        }
        }
    }

    removing?.let { (bill, line) ->
        RemoveFromBillDialog(
            line = line,
            currency = bill.currency,
            // Pieces that went through the kitchen are being cooked; taking them off the bill doesn't stop that.
            wentToKitchen = tableKitchen && !line.unsent,
            onDismiss = { removing = null },
            onConfirm = { count ->
                viewModel.removeFromBill(bill, line.items, count)
                removing = null
            },
        )
    }

    cancellingBill?.let { bill ->
        CancelReasonDialog(
            title = "Adisyon neden iptal ediliyor?",
            onSelect = { reason ->
                viewModel.cancelBill(bill, reason)
                cancellingBill = null
            },
            onDismiss = { cancellingBill = null },
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

/** Always within thumb reach: "Adisyon aç" on an empty table, "Ödeme al" and "Ürün ekle" on an open bill. */
@Composable
private fun TableFooter(onAddItems: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        ActionPill("Adisyon aç", AppIcons.Add, AppColors.Green, onClick = onAddItems)
    }
}

/** Small rounded action in a soft [tone]: pale fill, coloured text, the same at rest as when pressed. */
@Composable
internal fun ActionPill(
    text: String,
    icon: ImageVector,
    tone: Tone,
    onClick: () -> Unit,
    enabled: Boolean = true,
    /** Count shown in a small solid bubble after the text, e.g. earlier bills behind "Geçmiş". */
    badge: Int? = null,
    /** Small icon after the text, e.g. a caret on a pill that opens a menu. */
    trailingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 14.dp),
        elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = tone.container, contentColor = tone.content),
        modifier = Modifier.height(36.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        badge?.let {
            Spacer(Modifier.width(6.dp))
            Text(
                it.toString(),
                color = tone.container,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tone.content).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        trailingIcon?.let {
            Spacer(Modifier.width(4.dp))
            Icon(it, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun BillCard(
    bill: Bill,
    unsentOrderIds: Set<Long>,
    skipKitchenProducts: Set<Long>,
    busy: Boolean,
    /** "Düzenle" is on: each unpaid line shows its "✕". */
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onCancelBill: () -> Unit,
    onSendUnsent: () -> Unit,
    onRemoveLine: (BillLine) -> Unit,
) {
    val lines = groupBillItems(bill.items) { item ->
        item.sourceOrderId in unsentOrderIds && item.productId !in skipKitchenProducts
    }
    val unsentCount = lines.filter { it.unsent }.sumOf { it.quantity }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Adisyon", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        "${bill.items.sumOf { it.quantity }} ürün",
                        formatElapsed(bill.openedAt).takeIf { it.isNotEmpty() }?.let { if (it == "şimdi") "az önce açıldı" else "$it önce açıldı" },
                    ).joinToString(" · "),
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (editing) {
                ActionPill("Bitti", AppIcons.Check, AppColors.Green, onClick = onToggleEditing)
            } else {
                BillActionsMenu(
                    // "Kişi başı" shares were worked out from the current total, so it stays fixed once one is paid.
                    canRemove = lines.any { it.removable > 0 } && bill.remainingTotal > 0.005 && bill.payments.none { it.splitShareNumber != null },
                    // Cancelling throws the orders away, so it is offered only before any money changed hands.
                    canCancel = bill.payments.isEmpty() && bill.paidTotal <= 0.0,
                    busy = busy,
                    onRemove = onToggleEditing,
                    onCancel = onCancelBill,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = AppColors.Line)
        // The same product ordered in several rounds reads as one line: "2× Ayran" instead of two "1× Ayran".
        // In "Düzenle" each unpaid line gets its "✕" (after a confirmation it comes off the bill); paid lines say so.
        // Nothing left to collect (shares or typed amounts covered it all): every line counts as paid.
        val billSettled = bill.items.isNotEmpty() && bill.remainingTotal <= 0.005
        lines.forEachIndexed { index, line ->
            if (index > 0) HorizontalDivider(color = AppColors.Line)
            val fullyPaid = billSettled || line.removable == 0
            val ink = if (fullyPaid) AppColors.Muted else AppColors.Ink
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                if (line.unsent) {
                    Icon(
                        AppIcons.Kitchen,
                        contentDescription = "Mutfağa gönderilmedi",
                        tint = AppColors.Amber.content,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("${line.quantity}× ${line.name}", color = ink, style = MaterialTheme.typography.bodyLarge)
                    line.note?.let { Text("Not: $it", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium) }
                    // Paid pieces in green under the name, so they never compete with the "✕" on the right.
                    when {
                        fullyPaid -> PaidMark("Ödendi")
                        line.paid > 0 -> PaidMark("${line.paid} / ${line.quantity} ödendi")
                    }
                }
                Text(formatMoney(line.total, bill.currency), color = ink, style = MaterialTheme.typography.bodyLarge)
                if (editing && !fullyPaid) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppColors.Red.container)
                            .clickable(enabled = !busy) { onRemoveLine(line) },
                    ) {
                        Icon(AppIcons.Close, contentDescription = "${line.name} adisyondan çıkar", tint = AppColors.Danger, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (bill.items.isEmpty()) {
            Text("Henüz adisyona yazılan ürün yok.", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (unsentCount > 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Amber.container)
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Icon(AppIcons.Kitchen, contentDescription = null, tint = AppColors.Amber.content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "$unsentCount ürün mutfağa gönderilmedi",
                    color = AppColors.Amber.content,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSendUnsent, enabled = !busy) {
                    Text("Mutfağa gönder", color = AppColors.AccentText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = AppColors.Line)
        Spacer(Modifier.height(6.dp))
        KeyValueRow("Toplam", formatMoney(bill.totalAmount, bill.currency))
        if (bill.paidTotal > 0) KeyValueRow("Ödenen", formatMoney(bill.paidTotal, bill.currency), valueColor = AppColors.Green.content)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Kalan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatMoney(bill.remainingTotal, bill.currency), style = MaterialTheme.typography.titleLarge, color = AppColors.AccentText)
        }
    }
}

@Composable
private fun PaidMark(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(AppIcons.Check, contentDescription = null, tint = AppColors.Green.content, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = AppColors.Green.content, style = MaterialTheme.typography.labelMedium)
    }
}

/** "İşlemler": the bill's rarer actions in one labelled dropdown instead of buttons on every line. */
@Composable
private fun BillActionsMenu(canRemove: Boolean, canCancel: Boolean, busy: Boolean, onRemove: () -> Unit, onCancel: () -> Unit) {
    if (!canRemove && !canCancel) return
    var open by remember { mutableStateOf(false) }
    Box {
        ActionPill("İşlemler", AppIcons.Actions, AppColors.Blue, onClick = { open = true }, trailingIcon = AppIcons.Expand)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = AppColors.Surface) {
            if (canRemove) {
                DropdownMenuItem(
                    text = { Text("Ürün çıkar") },
                    leadingIcon = { Icon(AppIcons.Edit, contentDescription = null, tint = AppColors.Ink) },
                    enabled = !busy,
                    onClick = {
                        open = false
                        onRemove()
                    },
                )
            }
            if (canCancel) {
                DropdownMenuItem(
                    text = { Text("Adisyonu iptal et", color = AppColors.Danger) },
                    leadingIcon = { Icon(AppIcons.Reject, contentDescription = null, tint = AppColors.Danger) },
                    enabled = !busy,
                    onClick = {
                        open = false
                        onCancel()
                    },
                )
            }
        }
    }
}

/** The two everyday actions of an open bill, pinned within thumb reach: add products, and take the payment. */
@Composable
private fun BillActionBar(bill: Bill, busy: Boolean, onAddItems: () -> Unit, onPay: () -> Unit, onCloseTable: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(AppColors.Surface)) {
        HorizontalDivider(color = AppColors.Line)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            OutlineButton("Ürün ekle", onClick = onAddItems, enabled = !busy, icon = AppIcons.Add, modifier = Modifier.weight(1f))
            if (bill.remainingTotal > 0.005) {
                PrimaryButton("Ödeme al", onClick = onPay, enabled = !busy, icon = AppIcons.Wallet, modifier = Modifier.weight(1f))
            } else {
                PrimaryButton("Masayı kapat", onClick = onCloseTable, enabled = !busy, icon = AppIcons.Check, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Today's orders of a table split by what the waiter has to do with them. */
private data class TableBoard(val actionable: List<Order>, val inKitchen: List<Order>, val done: List<Order>) {
    companion object {
        fun of(orders: List<Order>, tableKitchen: Boolean): TableBoard {
            val waiting = if (tableKitchen) {
                setOf(OrderStatus.SUBMITTED, OrderStatus.READY)
            } else {
                // Without a kitchen the waiter serves straight from "Onaylandı".
                setOf(OrderStatus.SUBMITTED, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY)
            }
            val kitchen = if (tableKitchen) setOf(OrderStatus.CONFIRMED, OrderStatus.PREPARING) else emptySet()
            return TableBoard(
                actionable = orders.filter { it.status in waiting },
                inKitchen = orders.filter { it.status in kitchen }.sortedBy { it.placedAt },
                done = orders.filter { it.status !in waiting && it.status !in kitchen },
            )
        }
    }
}

private data class BillLine(
    val name: String,
    val quantity: Int,
    val total: Double,
    val note: String?,
    val unsent: Boolean,
    val items: List<BillItem>,
) {
    val paid: Int get() = items.sumOf { it.paidQuantity }

    /** Unpaid pieces; only these can come off the bill. */
    val removable: Int get() = quantity - paid
}

private data class BillLineKey(val name: String, val unitPrice: Double, val note: String?, val unsent: Boolean)

/** Merges identical lines; sent and unsent portions of a product stay apart so the icon stays truthful. */
private fun groupBillItems(items: List<BillItem>, isUnsent: (BillItem) -> Boolean = { false }): List<BillLine> =
    items.groupBy { BillLineKey(it.productName, it.unitPrice, it.note?.takeIf(String::isNotBlank), isUnsent(it)) }
        .map { (key, group) -> BillLine(key.name, group.sumOf { it.quantity }, group.sumOf { it.lineTotal }, key.note, key.unsent, group) }

/** How many pieces of a bill line to take off; paid pieces are not offered. */
@Composable
private fun RemoveFromBillDialog(line: BillLine, currency: String?, wentToKitchen: Boolean, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var count by remember { mutableStateOf(1) }
    val unitPrice = if (line.quantity > 0) line.total / line.quantity else 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text("Adisyondan çıkar", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${line.quantity}× ${line.name}", style = MaterialTheme.typography.titleMedium)
                if (line.removable > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Çıkarılacak adet", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { count-- }, enabled = count > 1) { Icon(AppIcons.Remove, contentDescription = "Bir azalt") }
                        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { count++ }, enabled = count < line.removable) { Icon(AppIcons.Add, contentDescription = "Bir artır") }
                    }
                }
                if (line.paid > 0) {
                    Text("${line.paid} adet ödendi, onlar adisyonda kalır.", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                if (wentToKitchen) {
                    Text(
                        "Bu ürün mutfağa gitti. Hazırlanmasın diye mutfağa ayrıca haber verin.",
                        color = AppColors.Amber.content,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(count) }) {
                Text("Çıkar · ${formatMoney(unitPrice * count, currency)}", color = AppColors.Danger)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = AppColors.Muted) } },
    )
}

private fun Order.itemSummary(): String =
    items.joinToString(", ") { if (it.quantity > 1) "${it.quantity}× ${it.productName}" else it.productName }

/** One order on a single line: number, time or waiting time, items and amount or status. */
@Composable
private fun CompactOrderRow(order: Order, showElapsed: Boolean, onCancel: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(order.itemSummary(), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "#${order.id} · " + if (showElapsed) formatElapsed(order.placedAt) else formatTime(order.placedAt),
                color = AppColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (showElapsed || order.status != OrderStatus.SERVED && order.status != OrderStatus.PAID) {
            StatusChip(if (order.status == OrderStatus.PREPARING) "Hazırlanıyor" else if (showElapsed) "Sırada" else order.status.label, order.status.tone())
        } else {
            Text(formatMoney(order.totalAmount, order.currency), style = MaterialTheme.typography.labelLarge)
        }
        onCancel?.let {
            TextButton(onClick = it) { Text("İptal", color = AppColors.Rose.content, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Served and cancelled orders, folded into one line until the waiter needs them. */
@Composable
private fun DoneOrders(orders: List<Order>) {
    var expanded by remember { mutableStateOf(false) }
    val served = orders.filter { it.status == OrderStatus.SERVED || it.status == OrderStatus.PAID }
    AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(vertical = 10.dp),
        ) {
            val title = when (served.size) {
                orders.size -> "Servis edilenler"
                0 -> "İptal edilenler"
                else -> "Servis edilen ve iptaller"
            }
            Text("$title (${orders.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatMoney(served.sumOf { it.totalAmount }), color = AppColors.Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            Icon(
                AppIcons.Next,
                contentDescription = if (expanded) "Gizle" else "Göster",
                tint = AppColors.Muted,
                modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            orders.forEach { order ->
                HorizontalDivider(color = AppColors.Line)
                CompactOrderRow(order, showElapsed = false)
            }
        }
    }
}

/** One earlier guest of the day: time range, total and payment; the orders fold out on tap. */
@Composable
internal fun PastBillRow(past: PastBill) {
    var expanded by remember { mutableStateOf(false) }
    val bill = past.bill
    val total = past.total
    val opened = past.openedAt
    val closed = bill?.closedAt
    val status = when (bill?.status) {
        // A bill paid partly in cash and partly by card shows both.
        TableBillStatus.CLOSED -> bill.payments.filter { !it.tip }.mapNotNull { it.paymentMethod }.distinct()
            .takeIf { it.isNotEmpty() }?.sortedBy { it.ordinal }?.joinToString(" + ") { it.label }
            ?: bill.paymentMethod?.label ?: "Kapandı"
        TableBillStatus.CANCELLED -> "İptal"
        else -> null
    }
    AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(formatTime(opened), closed?.let { formatTime(it) }).joinToString(" – "),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${past.orders.sumOf { order -> order.items.sumOf { it.quantity } }} ürün · ${past.orders.size} sipariş",
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(total, bill?.currency), style = MaterialTheme.typography.labelLarge)
                status?.let { StatusChip(it, if (bill?.status == TableBillStatus.CANCELLED) AppColors.Rose else AppColors.Green) }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                AppIcons.Next,
                contentDescription = if (expanded) "Gizle" else "Göster",
                tint = AppColors.Muted,
                modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            past.orders.forEach { order ->
                HorizontalDivider(color = AppColors.Line)
                CompactOrderRow(order, showElapsed = false)
            }
        }
    }
}
