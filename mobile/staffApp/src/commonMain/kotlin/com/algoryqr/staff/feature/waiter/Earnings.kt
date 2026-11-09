package com.algoryqr.staff.feature.waiter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.format.formatTime
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.IconListRow
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SectionLabel
import com.algoryqr.shared.ui.StatTile
import com.algoryqr.shared.network.safeCall
import com.algoryqr.staff.data.Shift
import com.algoryqr.staff.data.StaffRepository
import com.algoryqr.staff.data.TodayCommission
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class Earnings(val shift: Shift?, val commission: TodayCommission?)

class EarningsViewModel(private val repository: StaffRepository) : LoadViewModel<Earnings>() {

    init {
        start()
    }

    // Either part may be switched off for this business, so a failure of one leaves the other visible.
    override suspend fun fetch(): Earnings = coroutineScope {
        val shift = async { safeCall { repository.currentShift() }.getOrNull() }
        val commission = async { safeCall { repository.todayCommission() }.getOrNull() }
        Earnings(shift.await(), commission.await())
    }
}

private fun recordLabel(type: String?): String = when (type) {
    "PERCENT_ORDER" -> "Sipariş yüzdesi"
    "FIXED_TABLE_CLOSE" -> "Masa kapatma"
    "FIXED_ITEM_ADD" -> "Ürün ekleme"
    else -> "Prim"
}

@Composable
fun EarningsScreen(viewModel: EarningsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScreen(title = "Kazanç", actions = { RefreshAction(viewModel::refresh) }) {
        LoadContent(state, onRetry = viewModel::refresh) { earnings ->
            val commission = earnings.commission
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            "Bugünkü prim",
                            formatMoney(commission?.totalAmount, commission?.currency),
                            AppIcons.Commission,
                            AppColors.Red,
                            Modifier.weight(1f),
                            caption = "${commission?.recordCount ?: 0} kayıt",
                        )
                        StatTile(
                            "Vardiya cirosu",
                            earnings.shift?.let { formatMoney(it.revenue) } ?: "—",
                            AppIcons.Shift,
                            AppColors.Amber,
                            Modifier.weight(1f),
                            caption = earnings.shift?.let { "${formatTime(it.openedAt)} açıldı" } ?: "Açık vardiya yok",
                        )
                    }
                }
                item { SectionLabel("Prim kayıtları") }
                val records = commission?.records.orEmpty()
                if (records.isEmpty()) {
                    item {
                        EmptyState(
                            "Bugün prim kaydı yok",
                            if (commission == null) "Bu işletmede prim sistemi kapalı olabilir." else "Siparişleriniz ödendikçe primleriniz burada listelenir.",
                            icon = AppIcons.Wallet,
                        )
                    }
                } else {
                    item {
                        AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                            records.forEach { record ->
                                IconListRow(
                                    icon = AppIcons.Revenue,
                                    tone = AppColors.Green,
                                    title = formatMoney(record.amount, commission?.currency),
                                    subtitle = listOfNotNull(recordLabel(record.recordType), record.tableName, formatTime(record.createdAt)).joinToString(" · "),
                                    onClick = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
