package com.algoryqr.staff.feature.waiter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.ChoiceChip
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.SegmentedSwitch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.math.round
import kotlin.time.Clock

enum class ListSort(val label: String) {
    NEWEST("En yeni"),
    AMOUNT("En yüksek tutar"),
}

/** Where an order stands, in the three groups a waiter looks for. */
enum class ListStatus(val label: String) { OPEN("Açık"), DONE("Tamamlandı"), CANCELLED("İptal") }

/**
 * Narrows a day's list (orders, past bills) by when it started and how much it came to.
 * [hours] in whole hours of the day, [amount] in lira; `null` means no limit.
 */
data class TimeAmountFilter(
    val sort: ListSort = ListSort.NEWEST,
    val hours: ClosedFloatingPointRange<Float>? = null,
    val amount: ClosedFloatingPointRange<Float>? = null,
    /** Only lists whose entries have a status (orders) offer it. */
    val status: ListStatus? = null,
) {
    val active: Boolean get() = hours != null || amount != null || status != null

    /** How many settings differ from the default, for the count on "Filtrele". */
    val count: Int get() = listOf(sort != ListSort.NEWEST, hours != null, amount != null, status != null).count { it }
}

/** The span the sliders cover: from the first entry's hour to the hour after the last, and 0 to the largest amount. */
data class TimeAmountBounds(val hours: ClosedFloatingPointRange<Float>, val amount: ClosedFloatingPointRange<Float>) {
    companion object {
        fun of(minutes: List<Int>, amounts: List<Double>): TimeAmountBounds {
            val firstHour = (minutes.minOrNull() ?: 0) / 60
            val lastHour = ((minutes.maxOrNull() ?: (23 * 60)) / 60 + 1).coerceAtMost(24)
            val maxAmount = ceil(amounts.maxOrNull() ?: 0.0).toFloat()
            return TimeAmountBounds(firstHour.toFloat()..lastHour.toFloat(), 0f..maxAmount)
        }
    }
}

/** Keeps the entries inside [filter]; "En yeni" keeps the incoming order, which is already newest first. */
fun <T> List<T>.filteredBy(
    filter: TimeAmountFilter,
    minuteOf: (T) -> Int?,
    amountOf: (T) -> Double,
    statusOf: ((T) -> ListStatus)? = null,
): List<T> {
    val matching = filter { entry ->
        val minute = minuteOf(entry)
        val amount = amountOf(entry)
        val inHours = filter.hours?.let { range -> minute != null && minute >= range.start * 60 && minute < range.endInclusive * 60 } ?: true
        val inAmount = filter.amount?.let { range -> amount >= range.start && amount <= range.endInclusive } ?: true
        val inStatus = filter.status?.let { wanted -> statusOf == null || statusOf(entry) == wanted } ?: true
        inHours && inAmount && inStatus
    }
    return when (filter.sort) {
        ListSort.NEWEST -> matching
        ListSort.AMOUNT -> matching.sortedByDescending(amountOf)
    }
}

/**
 * Above the list: the result summary and one "Filtrele" button (with how many settings are on), and under it each
 * setting in use as a badge that clears with its ×. The button opens one sheet with every setting; changes apply
 * as they are made, so the button at its bottom already shows how many entries are left.
 */
@Composable
fun TimeAmountFilterBar(
    filter: TimeAmountFilter,
    bounds: TimeAmountBounds,
    /** "10 sipariş · ₺460,00" for the list as filtered. */
    summary: String,
    /** "10 siparişi göster" on the sheet's button. */
    showLabel: String,
    onChange: (TimeAmountFilter) -> Unit,
    /** Offers the status section; only for lists whose entries have one. */
    withStatus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterSummary(summary, Modifier.weight(1f))
            FilterButton(count = filter.count, onClick = { open = true })
        }
        if (filter.count > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                if (filter.sort != ListSort.NEWEST) {
                    ActiveBadge(filter.sort.label, AppIcons.Report) { onChange(filter.copy(sort = ListSort.NEWEST)) }
                }
                filter.status?.let { ActiveBadge(it.label, AppIcons.Orders) { onChange(filter.copy(status = null)) } }
                filter.hours?.let { ActiveBadge("${hourText(it.start)}–${hourText(it.endInclusive)}", AppIcons.Time) { onChange(filter.copy(hours = null)) } }
                filter.amount?.let { ActiveBadge(amountText(it), AppIcons.Cash) { onChange(filter.copy(amount = null)) } }
            }
        }
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppColors.Surface,
        ) {
            Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Filtrele", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange(TimeAmountFilter()) }, enabled = filter.count > 0) {
                        Text("Sıfırla", color = if (filter.count > 0) AppColors.AccentText else AppColors.Muted)
                    }
                }
                SheetSection("Sıralama")
                SegmentedSwitch(options = ListSort.entries.map { it to it.label }, selected = filter.sort, onSelect = { onChange(filter.copy(sort = it)) })
                if (withStatus) {
                    SheetSection("Durum")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        ChoiceChip("Tümü", selected = filter.status == null, onClick = { onChange(filter.copy(status = null)) })
                        ListStatus.entries.forEach { status ->
                            ChoiceChip(status.label, selected = filter.status == status, onClick = { onChange(filter.copy(status = status)) })
                        }
                    }
                }
                SheetSection("Saat", filter.hours?.let { "${hourText(it.start)} – ${hourText(it.endInclusive)}" } ?: "Tüm gün")
                HoursPicker(filter.hours, bounds.hours) { onChange(filter.copy(hours = it)) }
                SheetSection("Tutar", filter.amount?.let { amountText(it) } ?: "Tümü")
                AmountPicker(filter.amount, bounds.amount) { onChange(filter.copy(amount = it)) }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(showLabel, onClick = { open = false })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HoursPicker(
    hours: ClosedFloatingPointRange<Float>?,
    bounds: ClosedFloatingPointRange<Float>,
    onHours: (ClosedFloatingPointRange<Float>?) -> Unit,
) {
    val nowHour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour.toFloat()
    val presets = listOf(
        "Son 1 saat" to (nowHour - 1f).coerceAtLeast(0f)..(nowHour + 1f).coerceAtMost(24f),
        "Son 3 saat" to (nowHour - 3f).coerceAtLeast(0f)..(nowHour + 1f).coerceAtMost(24f),
        "Öğle" to 11f..15f,
        "Akşam" to 17f..24f,
    )
    PresetRow(presets, hours, onHours)
    // The slider spans the day's own hours; a preset outside them still filters, the slider just shows its edge.
    val span = bounds.endInclusive - bounds.start
    if (span > 1f) {
        val value = (hours ?: bounds).let { it.start.coerceIn(bounds)..it.endInclusive.coerceIn(bounds) }
        RangeSlider(
            value = value,
            onValueChange = { range -> onHours((round(range.start)..round(range.endInclusive)).takeUnless { it == bounds }) },
            valueRange = bounds,
            steps = (span.toInt() - 1).coerceAtLeast(0),
            colors = sliderColors(),
        )
    }
}

@Composable
private fun AmountPicker(
    amount: ClosedFloatingPointRange<Float>?,
    bounds: ClosedFloatingPointRange<Float>,
    onAmount: (ClosedFloatingPointRange<Float>?) -> Unit,
) {
    val top = maxOf(bounds.endInclusive, 1000f)
    val presets = listOf(
        "₺100 altı" to 0f..100f,
        "₺100–300" to 100f..300f,
        "₺300–500" to 300f..500f,
        "₺500+" to 500f..top,
    )
    PresetRow(presets, amount, onAmount)
    if (bounds.endInclusive > 0f) {
        val value = (amount ?: bounds).let { it.start.coerceIn(bounds)..it.endInclusive.coerceIn(bounds) }
        RangeSlider(
            value = value,
            onValueChange = { range -> onAmount((round(range.start)..round(range.endInclusive)).takeUnless { it == bounds }) },
            valueRange = bounds,
            colors = sliderColors(),
        )
    }
}

@Composable
private fun SheetSection(title: String, value: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        value?.let { Text(it, color = AppColors.AccentText, style = MaterialTheme.typography.labelLarge) }
    }
}

/** A preset tapped again turns off. */
@Composable
private fun PresetRow(
    presets: List<Pair<String, ClosedFloatingPointRange<Float>>>,
    current: ClosedFloatingPointRange<Float>?,
    onSelect: (ClosedFloatingPointRange<Float>?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
    ) {
        presets.forEach { (label, range) ->
            ChoiceChip(label, selected = current == range, onClick = { onSelect(range.takeUnless { it == current }) })
        }
    }
}

/** "Filtrele" with a small solid count once anything is set. */
@Composable
private fun FilterButton(count: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val active = count > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(38.dp)
            .clip(shape)
            .background(if (active) AppColors.Red.container else AppColors.Surface)
            .border(1.dp, if (active) AppColors.Accent.copy(alpha = 0.4f) else AppColors.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        val tint = if (active) AppColors.AccentText else AppColors.Ink
        Icon(AppIcons.Actions, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Filtrele", color = tint, style = MaterialTheme.typography.labelLarge)
        if (active) {
            Spacer(Modifier.width(6.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp).clip(CircleShape).background(AppColors.Accent)) {
                Text(count.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A setting in use, under the summary; its × turns just that one off. */
@Composable
private fun ActiveBadge(label: String, icon: ImageVector, onClear: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(AppColors.Red.container)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClear)
            .padding(start = 10.dp, end = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.AccentText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = AppColors.AccentText, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Icon(AppIcons.Close, contentDescription = "$label kaldır", tint = AppColors.AccentText, modifier = Modifier.padding(start = 4.dp).size(16.dp))
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = AppColors.Accent,
    activeTrackColor = AppColors.Accent,
    inactiveTrackColor = AppColors.Line,
)

/** "9 sipariş · ₺1.560,00" over the filtered list. */
@Composable
fun FilterSummary(text: String, modifier: Modifier = Modifier) {
    Text(text, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

private fun amountText(range: ClosedFloatingPointRange<Float>): String =
    "₺${range.start.toInt()}–${range.endInclusive.toInt()}"

private fun hourText(hour: Float): String = "${hour.toInt().toString().padStart(2, '0')}:00"
