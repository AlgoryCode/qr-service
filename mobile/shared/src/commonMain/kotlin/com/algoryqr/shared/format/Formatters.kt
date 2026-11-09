package com.algoryqr.shared.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock

/** Turkish money format: `₺1.234,50`; qr-service sends amounts as plain JSON numbers. */
fun formatMoney(amount: Double?, currency: String? = "TRY"): String {
    val value = amount ?: 0.0
    val cents = abs((value * 100).roundToLong())
    val whole = (cents / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    val fraction = (cents % 100).toString().padStart(2, '0')
    val sign = if (value < 0 && cents > 0) "-" else ""
    val symbol = when (currency?.uppercase()) {
        null, "", "TRY", "TL" -> "₺"
        "USD" -> "$"
        "EUR" -> "€"
        else -> "$currency "
    }
    return "$sign$symbol$whole,$fraction"
}

fun formatPercent(value: Double?): String {
    if (value == null) return "—"
    val tenths = (value * 10).roundToLong()
    val text = if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10},${abs(tenths % 10)}"
    return "%$text"
}

/** Backend timestamps are zone-less `LocalDateTime` strings in the business's local time. */
private fun parse(value: String?): LocalDateTime? = value?.let { runCatching { LocalDateTime.parse(it.removeSuffix("Z")) }.getOrNull() }

/** "14:05" for today, "28.10 14:05" otherwise. */
fun formatTime(value: String?): String {
    val time = parse(value) ?: return "—"
    val clock = "${time.hour.pad()}:${time.minute.pad()}"
    return if (time.date == today()) clock else "${time.day.pad()}.${time.month.number.pad()} $clock"
}

fun formatDateTime(value: String?): String {
    val time = parse(value) ?: return "—"
    return "${time.day.pad()}.${time.month.number.pad()}.${time.year} ${time.hour.pad()}:${time.minute.pad()}"
}

/** "3 dk", "1 sa 20 dk": how long ago an order came in, for the kitchen and waiter boards. */
fun formatElapsed(value: String?): String {
    val time = parse(value) ?: return ""
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val minutes = ((now.toEpochMinutes() - time.toEpochMinutes())).coerceAtLeast(0)
    return when {
        minutes < 1 -> "şimdi"
        minutes < 60 -> "$minutes dk"
        else -> "${minutes / 60} sa ${minutes % 60} dk"
    }
}

fun elapsedMinutes(value: String?): Long {
    val time = parse(value) ?: return 0
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return (now.toEpochMinutes() - time.toEpochMinutes()).coerceAtLeast(0)
}

/** Minutes since midnight (14:05 → 845), for filtering the day's records by hour. */
fun minuteOfDay(value: String?): Int? = parse(value)?.let { it.hour * 60 + it.minute }

fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun LocalDateTime.toEpochMinutes(): Long = date.toEpochDays() * 24 * 60 + hour * 60 + minute

private fun Int.pad(): String = toString().padStart(2, '0')

/** Lower-cased and stripped of Turkish letters, so "cay" finds "Çay" and "SIS" finds "Şiş". */
fun String.searchKey(): String = buildString(length) {
    for (char in this@searchKey) {
        append(
            when (char) {
                'ç', 'Ç' -> 'c'
                'ğ', 'Ğ' -> 'g'
                'ı', 'I', 'İ', 'i' -> 'i'
                'ö', 'Ö' -> 'o'
                'ş', 'Ş' -> 's'
                'ü', 'Ü' -> 'u'
                else -> char.lowercaseChar()
            },
        )
    }
}
