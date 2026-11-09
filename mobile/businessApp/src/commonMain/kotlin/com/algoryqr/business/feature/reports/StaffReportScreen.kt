package com.algoryqr.business.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.business.data.Branch
import com.algoryqr.business.data.StaffPerformanceReport
import com.algoryqr.business.data.StaffPerformanceRow
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.format.formatPercent
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.StatusChip

/** What the leaderboard ranks people by. */
private enum class StaffMetric(val label: String) { REVENUE("Ciro"), ORDERS("Sipariş"), TIPS("Bahşiş"), BASKET("Ort. sepet") }

/**
 * The staff report on its own page. Every card reads the same waiter-performance load, so one range at the top
 * drives the whole page; tapping a person opens their own breakdown.
 */
@Composable
fun StaffReportScreen(viewModel: ReportsViewModel, branches: List<Branch>, onBack: () -> Unit) {
    val branch by viewModel.branch.collectAsStateWithLifecycle()
    val ranges by viewModel.ranges.collectAsStateWithLifecycle()
    val loads by viewModel.staff.loads.collectAsStateWithLifecycle()
    val range = ranges.getValue(ReportCard.STAFF)
    val load = loads.forRange(branch, range)
    var opened by remember { mutableStateOf<StaffPerformanceRow?>(null) }

    AppScreen(title = "Personel raporu", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        BranchDropdown(branches, branch, viewModel::selectBranch, Modifier.padding(horizontal = 20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        ) {
            Text(range.description, style = MaterialTheme.typography.titleSmall, color = AppColors.Muted, modifier = Modifier.weight(1f))
            RangePills(ReportCard.STAFF.ranges, range) { viewModel.selectRange(ReportCard.STAFF, it) }
        }
        Box(Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            if (load.loading && load.data != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(50)), color = AppColors.Accent,
                    trackColor = AppColors.Subtle)
            }
        }
        val report = load.data
        when {
            report != null -> StaffReportContent(report, onOpen = { opened = it })
            load.error != null -> CardMessage(load.error, action = "Tekrar dene", onAction = viewModel::refresh)
            else -> Column(Modifier.padding(horizontal = 20.dp)) { Placeholder() }
        }
    }

    opened?.let { person ->
        StaffPersonSheet(person, load.data?.kpis?.currency, onDismiss = { opened = null })
    }
}

@Composable
private fun StaffReportContent(report: StaffPerformanceReport, onOpen: (StaffPerformanceRow) -> Unit) {
    val currency = report.kpis.currency
    val people = report.waiters.filter { it.orderCount > 0 || it.revenue > 0 }.sortedByDescending { it.revenue }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TeamSummary(report) }
        if (people.isEmpty()) {
            item { AppCard { CardMessage("Bu dönemde personel satışı yok") } }
            return@LazyColumn
        }
        item { Leaderboard(people, currency, onOpen) }
        item { RevenueShare(people, currency) }
        if (report.daily.size > 1 && report.daily.any { it.revenue > 0 }) item { TeamDaily(report, currency) }
        if (report.hourly.any { it.orderCount > 0 }) item { TeamHours(report, currency) }
        item { Efficiency(people, currency, onOpen) }
        if (report.products.isNotEmpty()) item { TeamProducts(report, currency) }
    }
}

/** Card with a heading and an optional one-line explanation, for the staff page's sections. */
@Composable
private fun StaffSection(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    AppCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                subtitle?.let { Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall) }
            }
            trailing()
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun TeamSummary(report: StaffPerformanceReport) {
    val kpis = report.kpis
    val currency = kpis.currency
    val orders = kpis.assignedOrderCount
    val allOrders = orders + kpis.unassignedOrderCount
    StaffSection("Ekip özeti") {
        Text(formatMoney(kpis.totalRevenue, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("personelin getirdiği ciro", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(color = AppColors.Line, modifier = Modifier.padding(vertical = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat("Sipariş", orders.toString(), Modifier.weight(1f))
            MiniStat("Ort. sepet", formatMoney(if (orders > 0) kpis.totalRevenue / orders else 0.0, currency), Modifier.weight(1f))
            MiniStat("Kapanan hesap", kpis.billsClosedCount.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat("Bahşiş", formatMoney(kpis.totalTip, currency), Modifier.weight(1f))
            MiniStat("Prim", formatMoney(kpis.totalCommission, currency), Modifier.weight(1f))
            MiniStat("Satılan ürün", kpis.itemCount.toString(), Modifier.weight(1f))
        }
        if (kpis.unassignedOrderCount > 0 && allOrders > 0) {
            Row(Modifier.padding(top = 14.dp)) {
                StatusChip(
                    "${kpis.unassignedOrderCount} sipariş (${formatPercent(kpis.unassignedOrderCount * 100.0 / allOrders)}) personele atanmamış",
                    AppColors.Amber,
                )
            }
        }
    }
}

@Composable
private fun Leaderboard(people: List<StaffPerformanceRow>, currency: String?, onOpen: (StaffPerformanceRow) -> Unit) {
    var metric by rememberSaveable { mutableStateOf(StaffMetric.REVENUE) }
    val value: (StaffPerformanceRow) -> Double = {
        when (metric) {
            StaffMetric.REVENUE -> it.revenue
            StaffMetric.ORDERS -> it.orderCount.toDouble()
            StaffMetric.TIPS -> it.tipAmount
            StaffMetric.BASKET -> it.avgOrderValue
        }
    }
    val label: (StaffPerformanceRow) -> String = {
        when (metric) {
            StaffMetric.ORDERS -> "${it.orderCount} sipariş"
            else -> formatMoney(value(it), currency)
        }
    }
    val ranked = people.sortedByDescending(value)
    val max = ranked.maxOf(value).takeIf { it > 0 } ?: 1.0
    StaffSection("Sıralama", "Bir kişiye dokunun, ayrıntıları açılsın") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            OptionPills(StaffMetric.entries, metric, { metric = it }, label = { it.label })
        }
        Spacer(Modifier.height(8.dp))
        ranked.forEachIndexed { i, person ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Ayrıntıları aç") { onOpen(person) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    "${i + 1}",
                    color = if (i == 0) AppColors.AccentText else AppColors.Muted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(20.dp),
                )
                Initials(person.displayName ?: "?", size = 36.dp, tone = if (i == 0) null else AppColors.Slate)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(person.displayName ?: "Personel", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(label(person), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    ShareBar((value(person) / max).toFloat(), if (i == 0) AppColors.Accent else AppColors.Blue.content, height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun RevenueShare(people: List<StaffPerformanceRow>, currency: String?) {
    val palette = chartPalette()
    val head = people.take(5)
    val other = people.drop(5).sumOf { it.revenue }
    val slices = head.mapIndexed { i, p -> Slice(p.displayName ?: "Personel", p.revenue, palette[i % palette.size]) } +
        listOfNotNull(other.takeIf { it > 0 }?.let { Slice("Diğer", it, AppColors.Line) })
    val total = slices.sumOf { it.value }
    StaffSection("Ciro payı", "Ekip cirosunun kişilere dağılımı") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices,
                description = slices.joinToString { "${it.label} ${formatPercent(it.value / total * 100)}" },
                modifier = Modifier.size(124.dp),
                thickness = 16.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${people.size}", style = MaterialTheme.typography.titleLarge)
                    Text("kişi", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                slices.forEach { slice ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(slice.color))
                        Spacer(Modifier.width(8.dp))
                        Text(slice.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        Text(formatPercent(slice.value / total * 100), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        // Equal shares mean the work is spread evenly; a large gap is worth a look at shifts and table assignment.
        if (people.size > 1) {
            val top = people.first().revenue / total * 100
            val fair = 100.0 / people.size
            Text(
                if (top > fair * 1.5) "${people.first().displayName} ekibin ortalamasının belirgin üstünde: ${formatPercent(top)} pay."
                else "Ciro ekip içinde dengeli dağılmış.",
                color = AppColors.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun TeamDaily(report: StaffPerformanceReport, currency: String?) {
    val days = report.daily.sortedBy { it.date }
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    val picked = selected?.let(days::getOrNull)
    StaffSection("Günlük ekip cirosu") {
        if (picked != null) {
            BigValue(formatMoney(picked.revenue, currency), "${longDate(picked.date)} · ${picked.orderCount} sipariş")
        } else {
            val best = days.maxBy { it.revenue }
            BigValue(formatMoney(days.sumOf { it.revenue } / days.size, currency), "günlük ortalama · en iyi gün ${longDate(best.date)}")
        }
        Spacer(Modifier.height(14.dp))
        AreaChart(
            values = days.map { it.revenue },
            previous = null,
            selected = selected,
            onSelect = { selected = it },
            description = "Personelin günlük cirosu",
            modifier = Modifier.fillMaxWidth().height(140.dp),
        )
        AxisLabels(shortDate(days.first().date), shortDate(days[days.size / 2].date), shortDate(days.last().date))
    }
}

@Composable
private fun TeamHours(report: StaffPerformanceReport, currency: String?) {
    val busy = report.hourly.filter { it.orderCount > 0 }
    val first = busy.minOf { it.hour }
    val last = busy.maxOf { it.hour }
    val byHour = report.hourly.associateBy { it.hour }
    val hours = (first..last).toList()
    val values = hours.map { (byHour[it]?.orderCount ?: 0).toDouble() }
    val peak = values.indices.maxBy { values[it] }
    var selected by remember(report) { mutableStateOf<Int?>(null) }
    val shown = byHour[hours[selected ?: peak]]
    StaffSection("Ekibin yoğun saatleri", "Saat başına kapanan sipariş") {
        BigValue(hourSpan(hours[selected ?: peak]), "${shown?.orderCount ?: 0} sipariş · ${formatMoney(shown?.revenue ?: 0.0, currency)}")
        Spacer(Modifier.height(14.dp))
        ColumnChart(
            values = values,
            highlight = peak,
            selected = selected,
            onSelect = { selected = it },
            description = "Saatlik sipariş, en yoğun saat ${hourSpan(hours[peak])}",
            modifier = Modifier.fillMaxWidth().height(110.dp),
            )
        val step = if (hours.size > 12) 3 else 2
        AxisLabels(*hours.filterIndexed { i, _ -> i % step == 0 }.map { "${it.toString().padStart(2, '0')}:00" }.toTypedArray())
    }
}

/** Side-by-side comparison of how each person sells, not just how much. */
@Composable
private fun Efficiency(people: List<StaffPerformanceRow>, currency: String?, onOpen: (StaffPerformanceRow) -> Unit) {
    val itemsPerOrder: (StaffPerformanceRow) -> Double = { if (it.orderCount > 0) it.itemCount.toDouble() / it.orderCount else 0.0 }
    val tipRate: (StaffPerformanceRow) -> Double = { if (it.revenue > 0) it.tipAmount / it.revenue * 100 else 0.0 }
    // Compared as shown (one decimal), so two people who read the same are both highlighted.
    val shown: (Double) -> Long = { kotlin.math.round(it * 10).toLong() }
    val bestBasket = people.maxOf { shown(it.avgOrderValue) }
    val bestItems = people.maxOf { shown(itemsPerOrder(it)) }
    val bestTip = people.maxOf { shown(tipRate(it)) }
    StaffSection("Verimlilik", "Her sütunda en iyi değer vurgulanır") {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Spacer(Modifier.weight(1.3f))
            listOf("Ort. sepet", "Ürün/sipariş", "Bahşiş oranı").forEach {
                Text(it, color = AppColors.Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f))
            }
        }
        people.forEach { person ->
            HorizontalDivider(color = AppColors.Line)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(person) }.padding(vertical = 10.dp),
            ) {
                Text(person.displayName ?: "Personel", style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.3f))
                EfficiencyCell(formatMoney(person.avgOrderValue, currency), shown(person.avgOrderValue) == bestBasket)
                EfficiencyCell(oneDecimal(itemsPerOrder(person)), shown(itemsPerOrder(person)) == bestItems)
                EfficiencyCell(formatPercent(tipRate(person)), shown(tipRate(person)) == bestTip && bestTip > 0)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.EfficiencyCell(text: String, best: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (best) FontWeight.SemiBold else FontWeight.Normal,
        color = if (best) AppColors.Green.content else Color.Unspecified,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun TeamProducts(report: StaffPerformanceReport, currency: String?) {
    val top = report.products.sortedByDescending { it.quantity }.take(5)
    val max = top.maxOf { it.quantity }.coerceAtLeast(1).toDouble()
    StaffSection("Ekibin en çok sattıkları") {
        top.forEachIndexed { i, product ->
            RankedBar(i + 1, product.name, "${product.quantity} adet", formatMoney(product.revenue, currency),
                (product.quantity / max).toFloat(), AppColors.Green.content)
        }
    }
}

/** One person's numbers, shares of the team and best sellers, in a sheet over the report. */
@Composable
private fun StaffPersonSheet(person: StaffPerformanceRow, currency: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(person.displayName ?: "?", size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.displayName ?: "Personel", style = MaterialTheme.typography.titleLarge)
                    Text("Ekip cirosundaki payı: ${formatPercent(person.revenueSharePercent)}", color = AppColors.Muted,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(formatMoney(person.revenue, currency), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
            HorizontalDivider(color = AppColors.Line, modifier = Modifier.padding(vertical = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Sipariş", person.orderCount.toString(), Modifier.weight(1f))
                MiniStat("Satılan ürün", person.itemCount.toString(), Modifier.weight(1f))
                MiniStat("Ort. sepet", formatMoney(person.avgOrderValue, currency), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Kapanan hesap", person.billsClosedCount.toString(), Modifier.weight(1f))
                MiniStat("Bahşiş", formatMoney(person.tipAmount, currency), Modifier.weight(1f))
                MiniStat("Prim", formatMoney(person.commissionAmount, currency), Modifier.weight(1f))
            }
            Text("Ekip içindeki payı", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            listOf(
                "Ciro" to person.revenueSharePercent,
                "Sipariş" to person.orderSharePercent,
                "Ürün" to person.itemSharePercent,
            ).forEach { (label, share) ->
                Column(Modifier.padding(top = 8.dp)) {
                    Row {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatPercent(share), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    ShareBar((share / 100).toFloat(), AppColors.Accent, height = 6.dp)
                }
            }
            if (person.topProducts.isNotEmpty()) {
                Text("En çok sattıkları", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted,
                    modifier = Modifier.padding(top = 20.dp))
                val max = person.topProducts.maxOf { it.quantity }.coerceAtLeast(1).toDouble()
                person.topProducts.take(5).forEachIndexed { i, product ->
                    RankedBar(i + 1, product.name, "${product.quantity} adet", formatMoney(product.revenue, currency),
                        (product.quantity / max).toFloat(), AppColors.Blue.content)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 4.85 → "4,9". */
private fun oneDecimal(value: Double): String {
    val tenths = kotlin.math.round(value * 10).toLong()
    return "${tenths / 10},${tenths % 10}"
}
