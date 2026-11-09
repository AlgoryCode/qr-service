package com.algoryqr.staff.feature.waiter

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.format.minuteOfDay
import com.algoryqr.shared.model.TableBillStatus
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.staff.data.StaffRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Bill details are fetched one by one; a long day stops here and the rest show from their orders alone. */
private const val MAX_HISTORY_BILLS = 30

/** Today's earlier guests of one table, newest first; qr-service only lists a table's orders of the current day. */
class TableHistoryViewModel(
    private val repository: StaffRepository,
    private val tableId: Long,
) : LoadViewModel<List<PastBill>>() {

    private val _filter = MutableStateFlow(TimeAmountFilter())
    val filter: StateFlow<TimeAmountFilter> = _filter.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): List<PastBill> = coroutineScope {
        val ordersCall = async { repository.tableOrders(tableId) }
        val billCall = async { safeCall { repository.tableBill(tableId) }.getOrNull() }
        val currentBillId = billCall.await()?.takeIf { it.status == TableBillStatus.OPEN }?.id
        val past = ordersCall.await()
            .filter { !it.status.isOpen && it.billId != currentBillId }
            .groupBy { it.billId }
        // Bill details only decorate the row, so a failure there still shows the orders.
        val bills = past.keys.filterNotNull().take(MAX_HISTORY_BILLS)
            .map { id -> async { id to safeCall { repository.bill(id) }.getOrNull() } }
            .awaitAll()
            .toMap()
        past.map { (billId, orders) -> PastBill(bills[billId], billId, orders) }
            .sortedByDescending { it.bill?.closedAt ?: it.orders.maxOf { order -> order.placedAt.orEmpty() } }
    }

    fun setFilter(filter: TimeAmountFilter) {
        _filter.value = filter
    }

}

@Composable
fun TableHistoryScreen(viewModel: TableHistoryViewModel, tableName: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    AppScreen(title = "$tableName · Geçmiş", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        LoadContent(state, onRetry = viewModel::refresh) { bills ->
            val bounds = TimeAmountBounds.of(bills.mapNotNull { minuteOfDay(it.openedAt) }, bills.map { it.total })
            val visible = bills.filteredBy(filter, minuteOf = { minuteOfDay(it.openedAt) }, amountOf = { it.total })
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                if (bills.isEmpty()) {
                    item { EmptyState("Bugün geçmiş yok", "Bu masada bugün kapanan adisyon olmadı.", icon = AppIcons.Time) }
                    return@LazyColumn
                }
                item {
                    TimeAmountFilterBar(
                        filter = filter,
                        bounds = bounds,
                        summary = "${visible.size} adisyon · ${formatMoney(visible.sumOf { it.total })}",
                        showLabel = "${visible.size} adisyonu göster",
                        onChange = viewModel::setFilter,
                    )
                }
                if (visible.isEmpty()) {
                    item { EmptyState("Filtreye uyan adisyon yok", "Filtreleri gevşetin ya da sıfırlayın.", icon = AppIcons.Time) }
                }
                items(visible, key = { "past-${it.billId}" }) { past -> PastBillRow(past) }
            }
        }
    }
}
