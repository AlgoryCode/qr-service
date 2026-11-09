package com.algoryqr.business.feature.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.business.data.Branch
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.format.formatPercent
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.IconTile
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.Tone

/** Each report page of the hub; the cards it shows are listed in [ReportDetailScreen]. */
enum class ReportSection(val title: String, val description: String, val icon: ImageVector) {
    SALES("Satış ve ödemeler", "Ciro trendi, ödeme yöntemleri, kanallar", AppIcons.Revenue),
    HOURS("Yoğun saatler", "Saat saat ciro ve sipariş", AppIcons.Time),
    PRODUCTS("Ürünler ve kategoriler", "En çok satanlar, satılmayanlar", AppIcons.Product),
    TABLES("Masalar", "Doluluk, oturma süresi, masa cirosu", AppIcons.Tables),
    STAFF("Personel", "Sıralama, pay, verimlilik, bahşiş ve prim", AppIcons.Staff),
    QR("QR menü", "Ziyaret ve menüden siparişe dönüşüm", AppIcons.Menu),
}

/**
 * Reports start as a hub: one card per report with its headline number for the last 7 days and a tiny chart, so
 * the owner sees everything at a glance and opens only the report they want to study, each on its own page.
 */
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    branches: List<Branch>,
    appBranch: Branch,
    onOpen: (ReportSection) -> Unit,
) {
    LaunchedEffect(appBranch.id) { viewModel.followAppBranch(appBranch) }
    val branch by viewModel.branch.collectAsStateWithLifecycle()
    val revenueLoads by viewModel.revenue.loads.collectAsStateWithLifecycle()
    val summaryLoads by viewModel.summary.loads.collectAsStateWithLifecycle()
    val staffLoads by viewModel.staff.loads.collectAsStateWithLifecycle()
    val week = ReportRange.WEEK
    val revenue = revenueLoads.forRange(branch, week)
    val previous = revenueLoads.forRange(branch, week, previous = true).data
    val summary = summaryLoads.forRange(branch, week).data
    val staff = staffLoads.forRange(branch, week).data

    AppScreen(title = "Raporlar", actions = { RefreshAction(viewModel::refresh) }) {
        BranchDropdown(branches, branch, viewModel::selectBranch, Modifier.padding(horizontal = 20.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Son 7 gün", color = AppColors.Muted, style = MaterialTheme.typography.labelLarge)
            }
            item {
                val report = revenue.data
                HubCard(ReportSection.SALES, AppColors.Red, onOpen, featured = true) {
                    if (report == null) {
                        HubPending(revenue.error)
                    } else {
                        Text(formatMoney(report.kpis.totalRevenue, report.kpis.currency),
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.padding(top = 6.dp)) { ChangeChip(report.kpis.totalRevenue, previous?.kpis?.totalRevenue) }
                        Sparkline(report.daily.sortedBy { it.date }.map { it.revenue }, Modifier.fillMaxWidth().height(64.dp).padding(top = 12.dp))
                        val payments = report.paymentBreakdown
                        val currency = report.kpis.currency
                        MiniStats(
                            "Sipariş" to report.totalOrders.toString(),
                            "Kart" to formatMoney(payments?.cardRevenue ?: 0.0, currency),
                            "Nakit" to formatMoney(payments?.cashRevenue ?: 0.0, currency),
                        )
                        val platforms = report.platformChannels.filter { it.revenue > 0 }
                        if (platforms.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                platforms.forEach { channel ->
                                    MiniStat(channel.label, formatMoney(channel.revenue, currency), Modifier.weight(1f))
                                }
                                // Keeps each platform under the same column as the cells above.
                                repeat(3 - platforms.size.coerceAtMost(3)) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            item {
                val report = revenue.data
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HubCard(ReportSection.HOURS, AppColors.Amber, onOpen, Modifier.weight(1f)) {
                        val hours = report?.hourly?.filter { it.revenue > 0 }.orEmpty()
                        if (report == null) {
                            HubPending(revenue.error)
                        } else if (hours.isEmpty()) {
                            HubValue("—", "Satış yok")
                        } else {
                            val peak = hours.maxBy { it.revenue }
                            HubValue("${peak.hour.toString().padStart(2, '0')}:00", "En yoğun saat")
                            val first = hours.minOf { it.hour }
                            val last = hours.maxOf { it.hour }
                            val byHour = report.hourly.associateBy { it.hour }
                            MiniBars((first..last).map { byHour[it]?.revenue ?: 0.0 }, Modifier.fillMaxWidth().height(36.dp).padding(top = 10.dp),
                                AppColors.Amber.content)
                        }
                    }
                    HubCard(ReportSection.PRODUCTS, AppColors.Green, onOpen, Modifier.weight(1f)) {
                        val best = report?.spotlight?.byQuantity
                        when {
                            report == null -> HubPending(revenue.error)
                            best == null -> HubValue("—", "Satış yok")
                            else -> {
                                HubValue(best.name, "En çok satan · ${best.quantity} adet", small = true)
                                if (report.unsold.count > 0) {
                                    Text("${report.unsold.count} ürün hiç satılmadı", color = AppColors.Amber.content,
                                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                                }
                            }
                        }
                    }
                }
            }
            item {
                HubCard(ReportSection.STAFF, AppColors.Blue, onOpen, featured = true) {
                    val waiters = staff?.waiters.orEmpty().filter { it.revenue > 0 }.sortedByDescending { it.revenue }
                    when {
                        staff == null -> HubPending(staffLoads.forRange(branch, week).error)
                        waiters.isEmpty() -> HubValue("—", "Bu hafta personel satışı yok")
                        else -> {
                            val leader = waiters.first()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AvatarStack(waiters.map { it.displayName ?: "?" })
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Haftanın lideri", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
                                    Text(leader.displayName ?: "Personel", style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatMoney(leader.revenue, staff.kpis.currency), style = MaterialTheme.typography.titleSmall)
                            }
                            val palette = chartPalette()
                            StackedShareBar(
                                waiters.mapIndexed { i, w -> w.revenue to palette[i % palette.size] },
                                Modifier.padding(top = 14.dp),
                            )
                            Text(
                                "${waiters.size} personel · ${formatMoney(staff.kpis.totalRevenue, staff.kpis.currency)} ciro · " +
                                    "${formatMoney(staff.kpis.totalTip, staff.kpis.currency)} bahşiş",
                                color = AppColors.Muted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HubCard(ReportSection.TABLES, AppColors.Slate, onOpen, Modifier.weight(1f)) {
                        val overview = summary?.overview
                        if (overview == null) {
                            HubPending(summaryLoads.forRange(branch, week).error)
                        } else {
                            HubValue(overview.occupancyRatio?.let { formatPercent(it) } ?: "—", "Doluluk")
                            summary.tablesTeaser.topTablesByRevenue.firstOrNull()?.let {
                                Text("En iyi: ${it.tableName}", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 10.dp))
                            }
                        }
                    }
                    HubCard(ReportSection.QR, AppColors.Rose, onOpen, Modifier.weight(1f)) {
                        if (summary == null) {
                            HubPending(summaryLoads.forRange(branch, week).error)
                        } else {
                            HubValue(summary.qrTeaser.sessions.toString(), "QR ziyaret")
                            Text("${summary.qrTeaser.browseFunnel.orderSubmitted} QR sipariş", color = AppColors.Muted,
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A report's card on the hub: icon, name and its preview; the whole card opens the report. */
@Composable
private fun HubCard(
    section: ReportSection,
    tone: Tone,
    onOpen: (ReportSection) -> Unit,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
    content: @Composable () -> Unit,
) {
    AppCard(modifier = modifier.heightIn(min = 150.dp), onClick = { onOpen(section) }, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(section.icon, tone, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (featured) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (featured) {
                    Text(section.description, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(AppIcons.Next, contentDescription = "Raporu aç", tint = AppColors.Muted, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun HubValue(value: String, caption: String, small: Boolean = false) {
    Text(
        value,
        style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(caption, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
}

@Composable
private fun HubPending(error: String?) {
    if (error != null) {
        Text(error, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
    } else {
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.Subtle))
    }
}

/** Overlapping initials of the first few people, "+n" for the rest. */
@Composable
private fun AvatarStack(names: List<String>) {
    val shown = names.take(4)
    // Same order and colours as the share bar under it, so each face matches its slice.
    val tones = listOf(AppColors.Red, AppColors.Blue, AppColors.Amber, AppColors.Green)
    Box(Modifier.width((28 * shown.size + 12).dp)) {
        shown.forEachIndexed { i, name ->
            Box(Modifier.offset(x = (i * 28).dp).clip(CircleShape).border(2.dp, AppColors.Surface, CircleShape)) {
                Initials(name, size = 38.dp, tone = tones[i])
            }
        }
    }
    if (names.size > shown.size) {
        Text("+${names.size - shown.size}", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

/** One report on its own page; the staff report has its own, richer screen. */
@Composable
fun ReportDetailScreen(
    viewModel: ReportsViewModel,
    branches: List<Branch>,
    section: ReportSection,
    onBack: () -> Unit,
) {
    if (section == ReportSection.STAFF) {
        StaffReportScreen(viewModel, branches, onBack)
        return
    }
    val branch by viewModel.branch.collectAsStateWithLifecycle()
    val ranges by viewModel.ranges.collectAsStateWithLifecycle()
    val revenue by viewModel.revenue.loads.collectAsStateWithLifecycle()
    val summary by viewModel.summary.loads.collectAsStateWithLifecycle()
    val cards = when (section) {
        ReportSection.SALES -> listOf(ReportCard.TREND, ReportCard.PAYMENTS)
        ReportSection.HOURS -> listOf(ReportCard.HOURS)
        ReportSection.PRODUCTS -> listOf(ReportCard.PRODUCTS, ReportCard.CATEGORIES)
        ReportSection.TABLES -> listOf(ReportCard.TABLES)
        ReportSection.QR -> listOf(ReportCard.QR)
        ReportSection.STAFF -> emptyList()
    }

    AppScreen(title = section.title, onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        BranchDropdown(branches, branch, viewModel::selectBranch, Modifier.padding(horizontal = 20.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            cards.forEach { card ->
                item(key = card.name) {
                    val range = ranges.getValue(card)
                    val onRange: (ReportRange) -> Unit = { viewModel.selectRange(card, it) }
                    when (card) {
                        ReportCard.TREND -> TrendCard(range, onRange, revenue.forRange(branch, range),
                            revenue.forRange(branch, range, previous = true), summary.forRange(branch, range), viewModel::refresh)
                        ReportCard.HOURS -> HoursCard(range, onRange, revenue.forRange(branch, range), viewModel::refresh)
                        ReportCard.PAYMENTS -> PaymentsCard(range, onRange, revenue.forRange(branch, range), viewModel::refresh)
                        ReportCard.PRODUCTS -> ProductsCard(range, onRange, revenue.forRange(branch, range), viewModel::refresh)
                        ReportCard.CATEGORIES -> CategoriesCard(range, onRange, revenue.forRange(branch, range), viewModel::refresh)
                        ReportCard.TABLES -> TablesCard(range, onRange, summary.forRange(branch, range), viewModel::refresh)
                        ReportCard.QR -> QrCard(range, onRange, summary.forRange(branch, range), viewModel::refresh)
                        ReportCard.STAFF -> Unit
                    }
                }
            }
        }
    }
}

/**
 * Which branch the reports cover: a pill with the branch icon, name and a caret that opens the list of branches.
 * With a single branch it still names the branch, but there is nothing to open.
 */
@Composable
internal fun BranchDropdown(branches: List<Branch>, selected: Branch, onSelect: (Branch) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val choosable = branches.size > 1
    val shape = RoundedCornerShape(14.dp)
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .background(AppColors.Surface)
                .border(1.dp, if (open) AppColors.Accent else AppColors.Line, shape)
                .then(
                    if (choosable) {
                        Modifier.clickable(role = Role.DropdownList, onClickLabel = "Şube seç") { open = true }
                    } else {
                        Modifier
                    },
                )
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconTile(AppIcons.Branch, AppColors.Red, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(max = 240.dp)) {
                Text("Şube", color = AppColors.Muted, style = MaterialTheme.typography.labelSmall)
                Text(selected.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (choosable) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    AppIcons.Expand,
                    contentDescription = null,
                    tint = AppColors.Muted,
                    modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = AppColors.Surface,
            border = BorderStroke(1.dp, AppColors.Line),
        ) {
            Text(
                "Raporlarda gösterilecek şube",
                color = AppColors.Muted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            branches.forEach { branch ->
                val isSelected = branch.id == selected.id
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(branch.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.SemiBold else null,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            branch.address?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    leadingIcon = { Icon(AppIcons.Branch, contentDescription = null, tint = if (isSelected) AppColors.AccentText else AppColors.Muted) },
                    trailingIcon = if (isSelected) {
                        { Icon(AppIcons.Check, contentDescription = "Seçili", tint = AppColors.AccentText) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(branch)
                    },
                    modifier = Modifier.widthIn(min = 260.dp),
                )
            }
        }
    }
}

