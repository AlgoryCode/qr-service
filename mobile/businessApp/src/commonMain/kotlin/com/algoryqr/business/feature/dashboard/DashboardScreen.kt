package com.algoryqr.business.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.business.data.Branch
import com.algoryqr.business.data.BusinessRepository
import com.algoryqr.business.data.HourlyRevenuePoint
import com.algoryqr.business.data.SummaryAnalytics
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.format.formatPercent
import com.algoryqr.shared.format.today
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SectionLabel
import com.algoryqr.shared.ui.SegmentedSwitch
import com.algoryqr.shared.ui.StatTile
import com.algoryqr.shared.ui.StatusChip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

enum class Period(val label: String, val days: Int) { TODAY("Bugün", 0), WEEK("7 gün", 6), MONTH("30 gün", 29) }

class DashboardViewModel(
    private val repository: BusinessRepository,
    private val branch: Branch,
) : LoadViewModel<SummaryAnalytics>(pollMillis = 60_000) {

    private val _period = MutableStateFlow(Period.TODAY)
    val period: StateFlow<Period> = _period.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): SummaryAnalytics {
        val to = today()
        val from = to.minus(DatePeriod(days = _period.value.days))
        return repository.summary(branch.id, branch.primaryMenuId, from, to)
    }

    fun selectPeriod(period: Period) {
        _period.value = period
        refresh()
    }
}

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, branchPicker: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()

    AppScreen(title = "Özet", actions = { RefreshAction(viewModel::refresh) }) {
        branchPicker()
        SegmentedSwitch(
            options = Period.entries.map { it to it.label },
            selected = period,
            onSelect = viewModel::selectPeriod,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LoadContent(state, onRetry = viewModel::refresh) { summary ->
            DashboardContent(summary)
        }
    }
}

@Composable
private fun DashboardContent(summary: SummaryAnalytics) {
    val overview = summary.overview
    val currency = overview.currency
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
        item {
            AppCard {
                Text("Brüt ciro", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                Text(formatMoney(overview.grossRevenue, currency), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    overview.revenueVsPreviousPct?.let { change ->
                        StatusChip(
                            (if (change >= 0) "▲ " else "▼ ") + formatPercent(kotlin.math.abs(change)) + " önceki döneme göre",
                            if (change >= 0) AppColors.Green else AppColors.Red,
                        )
                    }
                    if (overview.tipTotal > 0) StatusChip("Bahşiş ${formatMoney(overview.tipTotal, currency)}", AppColors.Amber)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Sipariş", overview.orderCount.toString(), AppIcons.Orders, AppColors.Red, Modifier.weight(1f),
                    caption = "${overview.completedOrderCount} tamamlandı")
                StatTile("Ort. sepet", formatMoney(overview.avgBasket, currency), AppIcons.Revenue, AppColors.Amber, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Açık masa", overview.activeOpenTables.toString(), AppIcons.Tables, AppColors.Blue, Modifier.weight(1f),
                    caption = "${summary.operationsTeaser.openBills} açık hesap")
                StatTile("İptal oranı", formatPercent(overview.cancelRate), AppIcons.Reject, AppColors.Rose, Modifier.weight(1f),
                    caption = "${overview.cancelledOrderCount} iptal")
            }
        }
        if (summary.salesTeaser.hourlyRevenue.any { it.revenue > 0 }) {
            item { SectionLabel("Saatlik ciro") }
            item { AppCard { HourlyBars(summary.salesTeaser.hourlyRevenue) } }
        }
        if (summary.salesTeaser.topProducts.isNotEmpty()) {
            item { SectionLabel("En çok satanlar") }
            item {
                AppCard {
                    summary.salesTeaser.topProducts.take(5).forEachIndexed { index, product ->
                        RankRow(index + 1, product.name, "${product.quantity} adet", formatMoney(product.revenue, currency))
                    }
                }
            }
        }
        if (summary.staffTeaser.topWaitersByRevenue.isNotEmpty()) {
            item { SectionLabel("Personel performansı") }
            item {
                AppCard {
                    summary.staffTeaser.topWaitersByRevenue.take(5).forEachIndexed { index, waiter ->
                        RankRow(index + 1, waiter.displayName ?: "Personel", "${waiter.orderCount} sipariş", formatMoney(waiter.revenue, currency))
                    }
                }
            }
        }
        item { SectionLabel("QR menü") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Ziyaret", summary.qrTeaser.sessions.toString(), AppIcons.Menu, AppColors.Red, Modifier.weight(1f))
                StatTile("Ürün görüntüleme", summary.qrTeaser.productViews.toString(), AppIcons.Product, AppColors.Slate, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, title: String, subtitle: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            rank.toString(),
            color = if (rank == 1) AppColors.AccentText else AppColors.Muted,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Simple bar per opening hour; the busiest hour is drawn in full brand red, the rest in its pale tone. */
@Composable
private fun HourlyBars(points: List<HourlyRevenuePoint>) {
    val visible = points.filter { it.revenue > 0 || it.orderCount > 0 }.ifEmpty { points }
    val first = visible.minOf { it.hour }
    val last = visible.maxOf { it.hour }
    val byHour = points.associateBy { it.hour }
    val max = visible.maxOf { it.revenue }.takeIf { it > 0 } ?: 1.0
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().height(120.dp),
    ) {
        (first..last).forEach { hour ->
            val revenue = byHour[hour]?.revenue ?: 0.0
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((revenue / max).toFloat().coerceIn(0.02f, 1f) * 0.85f)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (revenue == max) AppColors.Accent else AppColors.Red.container),
                )
                Text(
                    hour.toString().padStart(2, '0'),
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
