package com.algoryqr.business.feature.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.algoryqr.shared.ui.Tone
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.business.data.Branch
import com.algoryqr.business.data.BrowseFunnel
import com.algoryqr.business.data.RevenueProduct
import com.algoryqr.business.data.RevenueReport
import com.algoryqr.business.data.StaffPerformanceReport
import com.algoryqr.business.data.SummaryAnalytics
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.format.formatPercent
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.IconTile
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.StatusChip
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt


// ---------------------------------------------------------------------------------------------------------------
// Card frame

/**
 * Every report card: title with the period in words, its own range pills, then a placeholder, an error with retry
 * or [content]. A thin bar shows while a newer range loads over the last one.
 */
@Composable
internal fun <T> ReportFrame(
    card: ReportCard,
    range: ReportRange,
    onRange: (ReportRange) -> Unit,
    load: CardLoad<T>,
    onRetry: () -> Unit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    AppCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(range.description, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
            RangePills(card.ranges, range, onRange)
        }
        Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) {
            if (load.loading && load.data != null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(50)),
                    color = AppColors.Accent,
                    trackColor = AppColors.Subtle,
                )
            }
        }
        val data = load.data
        when {
            data != null -> content(data)
            load.error != null -> CardMessage(load.error, action = "Tekrar dene", onAction = onRetry)
            else -> Placeholder()
        }
    }
}

@Composable
internal fun Placeholder() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(120.dp).height(26.dp).clip(RoundedCornerShape(8.dp)).background(AppColors.Subtle))
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.Subtle))
    }
}

@Composable
internal fun CardMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
        if (action != null) TextButton(onClick = onAction) { Text(action, color = AppColors.AccentText) }
    }
}

@Composable
internal fun BigValue(value: String, caption: String? = null) {
    Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    caption?.let { Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium) }
}

/** Small label-over-value cell; three of them sit in a row under a chart. */
@Composable
internal fun MiniStat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun MiniStats(vararg stats: Pair<String, String>) {
    HorizontalDivider(color = AppColors.Line, modifier = Modifier.padding(vertical = 14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEach { (label, value) -> MiniStat(label, value, Modifier.weight(1f)) }
    }
}

@Composable
internal fun AxisLabels(vararg labels: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, color = AppColors.Muted, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
internal fun LegendRow(color: Color, label: String, value: String, share: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        share?.let { Text(formatPercent(it), color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun chartPalette(): List<Color> = listOf(
    AppColors.Accent, AppColors.Blue.content, AppColors.Amber.content, AppColors.Green.content, AppColors.Rose.content, AppColors.Slate.content,
)

// ---------------------------------------------------------------------------------------------------------------
// Cards

private class TrendData(val current: RevenueReport, val previous: RevenueReport?, val summary: SummaryAnalytics?)

@Composable
internal fun TrendCard(
    range: ReportRange,
    onRange: (ReportRange) -> Unit,
    current: CardLoad<RevenueReport>,
    previous: CardLoad<RevenueReport>,
    summary: CardLoad<SummaryAnalytics>,
    onRetry: () -> Unit,
) {
    val load = CardLoad(current.data?.let { TrendData(it, previous.data, summary.data) }, current.loading, current.error)
    ReportFrame(ReportCard.TREND, range, onRange, load, onRetry) { data ->
        val days = data.current.dailyWithPlatforms.sortedBy { it.date }
        val currency = data.current.kpis.currency
        var selected by remember(days) { mutableStateOf<Int?>(null) }
        val picked = selected?.let(days::getOrNull)
        val total = data.current.kpis.totalRevenue
        val previousTotal = data.previous?.kpis?.totalRevenue

        if (picked != null) {
            BigValue(formatMoney(picked.revenue, currency), "${longDate(picked.date)} · ${picked.orderCount} sipariş")
        } else {
            BigValue(formatMoney(total, currency))
            Row(Modifier.padding(top = 6.dp)) { ChangeChip(total, previousTotal) }
        }
        Spacer(Modifier.height(16.dp))
        if (days.size > 1) {
            AreaChart(
                values = days.map { it.revenue },
                previous = data.previous?.dailyWithPlatforms?.sortedBy { it.date }?.map { it.revenue },
                selected = selected,
                onSelect = { selected = it },
                description = "Günlük ciro grafiği, toplam ${formatMoney(total, currency)}",
                modifier = Modifier.fillMaxWidth().height(170.dp),
            )
            AxisLabels(shortDate(days.first().date), shortDate(days[days.size / 2].date), shortDate(days.last().date))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(AppColors.Accent, "Bu dönem")
                if (data.previous != null) LegendDot(AppColors.Muted, "Önceki dönem", dashed = true)
            }
        }
        val overview = data.summary?.overview
        MiniStats(
            "Sipariş" to data.current.totalOrders.toString(),
            "Ort. sepet" to formatMoney(data.current.kpis.avgOrderValue, currency),
            "İptal oranı" to (overview?.let { formatPercent(it.cancelRate) } ?: "—"),
        )
    }
}

@Composable
internal fun ChangeChip(current: Double, previous: Double?) {
    when {
        previous == null -> Unit
        previous <= 0.0 && current > 0 -> StatusChip("Önceki dönemde satış yoktu", AppColors.Slate)
        previous > 0.0 -> {
            val change = (current - previous) / previous * 100
            StatusChip(
                (if (change >= 0) "▲ " else "▼ ") + formatPercent(abs(change)) + " önceki döneme göre",
                if (change >= 0) AppColors.Green else AppColors.Red,
            )
        }
    }
}

@Composable
internal fun LegendDot(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) { Box(Modifier.width(4.dp).height(2.dp).background(color)) }
            }
        } else {
            Box(Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(50)).background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun HoursCard(range: ReportRange, onRange: (ReportRange) -> Unit, load: CardLoad<RevenueReport>, onRetry: () -> Unit) {
    ReportFrame(ReportCard.HOURS, range, onRange, load, onRetry) { report ->
        val byHour = report.hourly.associateBy { it.hour }
        val busy = report.hourly.filter { it.revenue > 0 || it.orderCount > 0 }
        if (busy.isEmpty()) {
            CardMessage("Bu dönemde satış yok")
            return@ReportFrame
        }
        // Only the opening hours, with one quiet hour on each side for context.
        val first = (busy.minOf { it.hour } - 1).coerceAtLeast(0)
        val last = (busy.maxOf { it.hour } + 1).coerceAtMost(23)
        val hours = (first..last).toList()
        val values = hours.map { byHour[it]?.revenue ?: 0.0 }
        val peak = values.indices.maxBy { values[it] }
        var selected by remember(report) { mutableStateOf<Int?>(null) }
        val shown = selected ?: peak
        val point = byHour[hours[shown]]
        val currency = report.kpis.currency

        Text(if (selected == null) "En yoğun saat" else "Seçili saat", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
        BigValue(hourSpan(hours[shown]), "${formatMoney(point?.revenue ?: 0.0, currency)} · ${point?.orderCount ?: 0} sipariş")
        Spacer(Modifier.height(16.dp))
        ColumnChart(
            values = values,
            highlight = peak,
            selected = selected,
            onSelect = { selected = it },
            description = "Saatlik ciro, en yoğun saat ${hourSpan(hours[peak])}",
            modifier = Modifier.fillMaxWidth().height(130.dp),
        )
        val step = if (hours.size > 12) 3 else 2
        AxisLabels(*hours.filterIndexed { i, _ -> i % step == 0 }.map { "${it.toString().padStart(2, '0')}:00" }.toTypedArray())
    }
}

@Composable
internal fun PaymentsCard(range: ReportRange, onRange: (ReportRange) -> Unit, load: CardLoad<RevenueReport>, onRetry: () -> Unit) {
    ReportFrame(ReportCard.PAYMENTS, range, onRange, load, onRetry) { report ->
        val payments = report.paymentBreakdown
        val currency = report.kpis.currency
        if (payments == null || payments.grossRevenue <= 0) {
            CardMessage("Bu dönemde tahsilat yok")
            return@ReportFrame
        }
        val slices = listOf(
            Slice("Nakit", payments.cashRevenue, AppColors.Green.content),
            Slice("Kart", payments.cardRevenue, AppColors.Blue.content),
            Slice("Bahşiş", payments.tipRevenue, AppColors.Amber.content),
            Slice("Uber Eats", payments.uberEatsRevenue, Color(0xFF06C167)),
            Slice("Yemeksepeti", payments.yemeksepetiRevenue, Color(0xFFEA004B)),
        ).filter { it.value > 0 }
        val total = slices.sumOf { it.value }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices,
                description = slices.joinToString { "${it.label} ${formatMoney(it.value, currency)}" },
                modifier = Modifier.size(132.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Toplam", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
                    Text(formatMoney(payments.grossRevenue, currency), style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                slices.forEach { slice ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(slice.color))
                            Spacer(Modifier.width(6.dp))
                            Text(slice.label, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(formatPercent(slice.value / total * 100), color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatMoney(slice.value, currency), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        if (payments.fixedExpenseTotal > 0) {
            MiniStats(
                "Sabit gider" to "−" + formatMoney(payments.fixedExpenseTotal, currency),
                "Net" to formatMoney(payments.netRevenue, currency),
            )
        }
        val channels = report.channels.filter { it.connected && it.revenue > 0 }
        if (channels.size > 1) {
            HorizontalDivider(color = AppColors.Line, modifier = Modifier.padding(vertical = 14.dp))
            Text("Satış kanalları", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
            val channelTotal = channels.sumOf { it.revenue }
            channels.forEachIndexed { i, channel ->
                Column(Modifier.padding(top = 10.dp)) {
                    Row {
                        Text(channel.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatMoney(channel.revenue, currency), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    ShareBar((channel.revenue / channelTotal).toFloat(), chartPalette()[i % 6], height = 6.dp)
                }
            }
        }
    }
}

private enum class ProductMetric(val label: String) { REVENUE("Ciro"), QUANTITY("Adet") }

@Composable
internal fun ProductsCard(range: ReportRange, onRange: (ReportRange) -> Unit, load: CardLoad<RevenueReport>, onRetry: () -> Unit) {
    var metric by rememberSaveable { mutableStateOf(ProductMetric.REVENUE) }
    ReportFrame(ReportCard.PRODUCTS, range, onRange, load, onRetry) { report ->
        val currency = report.kpis.currency
        if (report.products.isEmpty()) {
            CardMessage("Bu dönemde satılan ürün yok")
            return@ReportFrame
        }
        val spotlight = report.spotlight
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spotlight("En çok satan", spotlight.byQuantity, "adet", AppColors.Green.content, Modifier.weight(1f))
            Spotlight("En az satan", spotlight.leastSoldByQuantity, "adet", AppColors.Amber.content, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
            Text("Sıralama", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted, modifier = Modifier.weight(1f))
            OptionPills(ProductMetric.entries, metric, { metric = it }, label = { it.label })
        }
        val value: (RevenueProduct) -> Double = { if (metric == ProductMetric.REVENUE) it.revenue else it.quantity.toDouble() }
        val ranked = report.products.sortedByDescending(value).take(8)
        val max = ranked.maxOf(value).takeIf { it > 0 } ?: 1.0
        ranked.forEachIndexed { i, product ->
            RankedBar(
                rank = i + 1,
                title = product.name,
                value = if (metric == ProductMetric.REVENUE) formatMoney(product.revenue, currency) else "${product.quantity} adet",
                caption = if (metric == ProductMetric.REVENUE) "${product.quantity} adet" else formatMoney(product.revenue, currency),
                fraction = (value(product) / max).toFloat(),
            )
        }
        if (report.unsold.count > 0) {
            HorizontalDivider(color = AppColors.Line, modifier = Modifier.padding(vertical = 14.dp))
            Text("Hiç satılmayanlar (${report.unsold.count})", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                report.unsold.products.take(12).forEach { StatusChip(it.name, AppColors.Slate) }
            }
        }
    }
}

@Composable
internal fun Spotlight(label: String, product: RevenueProduct?, unit: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(12.dp),
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        Text(product?.name ?: "—", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(product?.let { "${it.quantity} $unit" } ?: " ", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Rank number, name and value on one line, a bar for its share of the leader underneath. */
@Composable
internal fun RankedBar(rank: Int, title: String, value: String, caption: String?, fraction: Float, color: Color = AppColors.Accent) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                rank.toString(),
                color = if (rank == 1) AppColors.AccentText else AppColors.Muted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                caption?.let { Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall) }
            }
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(6.dp))
        ShareBar(fraction, color, Modifier.padding(start = 22.dp), height = 6.dp)
    }
}

@Composable
internal fun CategoriesCard(range: ReportRange, onRange: (ReportRange) -> Unit, load: CardLoad<RevenueReport>, onRetry: () -> Unit) {
    ReportFrame(ReportCard.CATEGORIES, range, onRange, load, onRetry) { report ->
        val currency = report.kpis.currency
        val sorted = report.categories.filter { it.revenue > 0 }.sortedByDescending { it.revenue }
        if (sorted.isEmpty()) {
            CardMessage("Bu dönemde satış yok")
            return@ReportFrame
        }
        // Past five slices a donut stops being readable, so the tail is folded into "Diğer".
        val head = sorted.take(5)
        val other = sorted.drop(5).sumOf { it.revenue }
        val palette = chartPalette()
        val slices = head.mapIndexed { i, c -> Slice(c.name, c.revenue, palette[i]) } +
            listOfNotNull(other.takeIf { it > 0 }?.let { Slice("Diğer", it, AppColors.Line) })
        val total = slices.sumOf { it.value }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                slices,
                description = slices.joinToString { "${it.label} ${formatPercent(it.value / total * 100)}" },
                modifier = Modifier.size(150.dp),
                thickness = 18.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${sorted.size}", style = MaterialTheme.typography.titleLarge)
                    Text("kategori", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        slices.forEach { slice -> LegendRow(slice.color, slice.label, formatMoney(slice.value, currency), slice.value / total * 100) }
    }
}

@Composable
internal fun TablesCard(range: ReportRange, onRange: (ReportRange) -> Unit, load: CardLoad<SummaryAnalytics>, onRetry: () -> Unit) {
    ReportFrame(ReportCard.TABLES, range, onRange, load, onRetry) { summary ->
        val overview = summary.overview
        val tables = summary.tablesTeaser.topTablesByRevenue.filter { it.revenue > 0 }
        val currency = overview.currency
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat("Doluluk", overview.occupancyRatio?.let { formatPercent(it) } ?: "—", Modifier.weight(1f))
            MiniStat("Açık masa", overview.activeOpenTables.toString(), Modifier.weight(1f))
            MiniStat("Ort. oturma", summary.tablesTeaser.avgTurnMinutes?.let(::duration) ?: "—", Modifier.weight(1f))
        }
        if (tables.isEmpty()) {
            CardMessage("Bu dönemde masa satışı yok")
            return@ReportFrame
        }
        HorizontalDivider(color = AppColors.Line, modifier = Modifier.padding(vertical = 14.dp))
        Text("En çok kazandıran masalar", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
        val max = tables.maxOf { it.revenue }
        tables.take(6).forEachIndexed { i, table ->
            RankedBar(
                rank = i + 1,
                title = table.tableName,
                value = formatMoney(table.revenue, currency),
                caption = listOfNotNull(
                    "${table.billCount} hesap",
                    "ort. ${formatMoney(table.avgCheck, currency)}",
                    table.avgDwellMinutes?.let(::duration),
                ).joinToString(" · "),
                fraction = (table.revenue / max).toFloat(),
                color = AppColors.Blue.content,
            )
        }
    }
}

@Composable
internal fun Initials(name: String, size: Dp = 38.dp, tone: Tone? = null) {
    val letters = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    val colors = tone ?: AppColors.Red
    Box(Modifier.size(size).clip(CircleShape).background(colors.container), contentAlignment = Alignment.Center) {
        Text(letters, color = if (tone == null) AppColors.AccentText else tone.content, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun QrCard(range: ReportRange, onRange: (ReportRange) -> Unit, load: CardLoad<SummaryAnalytics>, onRetry: () -> Unit) {
    ReportFrame(ReportCard.QR, range, onRange, load, onRetry) { summary ->
        val qr = summary.qrTeaser
        val steps = qr.browseFunnel.steps()
        val start = steps.first().second
        if (qr.sessions == 0L && start == 0L) {
            CardMessage("Bu dönemde QR menü ziyareti yok")
            return@ReportFrame
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat("Ziyaret", qr.sessions.toString(), Modifier.weight(1f))
            MiniStat("Menü açılışı", qr.menuOpens.toString(), Modifier.weight(1f))
            MiniStat("Ürün görüntüleme", qr.productViews.toString(), Modifier.weight(1f))
        }
        HorizontalDivider(color = AppColors.Line, modifier = Modifier.padding(vertical = 14.dp))
        Text("Menüden siparişe", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
        steps.forEach { (label, count) ->
            val fraction = if (start > 0) count.toFloat() / start else 0f
            Column(Modifier.padding(top = 10.dp)) {
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(count.toString(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (start > 0) "  ${formatPercent(fraction * 100.0)}" else "",
                        color = AppColors.Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ShareBar(fraction, AppColors.Rose.content, height = 6.dp)
            }
        }
    }
}

private fun BrowseFunnel.steps(): List<Pair<String, Long>> = listOf(
    "Menü açıldı" to menuOpens,
    "Kategoriye bakıldı" to categoryViews,
    "Ürüne bakıldı" to productViews,
    "Sepete eklendi" to addToCart,
    "Ödemeye geçildi" to checkoutStarts,
    "Sipariş verildi" to orderSubmitted,
)

// ---------------------------------------------------------------------------------------------------------------
// Formatting

private val MonthNames = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")

/** "2026-11-02" → "02.11". */
internal fun shortDate(isoDate: String): String {
    val parts = isoDate.split("-")
    return if (parts.size == 3) "${parts[2]}.${parts[1]}" else isoDate
}

/** "2026-11-02" → "2 Kas". */
internal fun longDate(isoDate: String): String {
    val parts = isoDate.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull()?.let { MonthNames.getOrNull(it - 1) } ?: return isoDate
    return "${parts[2].trimStart('0')} $month"
}

internal fun hourSpan(hour: Int): String = "${hour.toString().padStart(2, '0')}:00–${((hour + 1) % 24).toString().padStart(2, '0')}:00"

/** 138 → "2 sa 18 dk". */
internal fun duration(minutes: Double): String {
    val total = minutes.roundToInt()
    return when {
        total < 60 -> "$total dk"
        total % 60 == 0 -> "${total / 60} sa"
        else -> "${total / 60} sa ${total % 60} dk"
    }
}
