package com.algoryqr.business.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoryqr.business.data.Branch
import com.algoryqr.business.data.BusinessRepository
import com.algoryqr.shared.format.today
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.network.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Look-back of one report card; "1G" is today only. */
enum class ReportRange(val label: String, val description: String, val days: Int) {
    DAY("1G", "Bugün", 1),
    WEEK("7G", "Son 7 gün", 7),
    MONTH("30G", "Son 30 gün", 30),
    QUARTER("90G", "Son 90 gün", 90),
}

/** Inclusive date span sent to the analytics endpoints; also the cache key, so cards on the same range share a load. */
data class ReportWindow(val from: LocalDate, val to: LocalDate) {
    /** The span of equal length right before this one, for "önceki döneme göre" comparisons. */
    fun previous(): ReportWindow {
        val length = to.toEpochDays() - from.toEpochDays() + 1
        val previousTo = from.minus(DatePeriod(days = 1))
        return ReportWindow(previousTo.minus(DatePeriod(days = (length - 1).toInt())), previousTo)
    }
}

fun ReportRange.window(end: LocalDate = today()): ReportWindow = ReportWindow(end.minus(DatePeriod(days = days - 1)), end)

/** The report cards in screen order; each keeps its own range and offers only the ranges that make sense for it. */
enum class ReportCard(val title: String, val shortTitle: String, val ranges: List<ReportRange>) {
    TREND("Ciro trendi", "Ciro", listOf(ReportRange.WEEK, ReportRange.MONTH, ReportRange.QUARTER)),
    HOURS("Yoğun saatler", "Saatler", listOf(ReportRange.DAY, ReportRange.WEEK, ReportRange.MONTH)),
    PAYMENTS("Ödeme yöntemleri", "Ödeme", listOf(ReportRange.DAY, ReportRange.WEEK, ReportRange.MONTH)),
    PRODUCTS("Ürün performansı", "Ürünler", listOf(ReportRange.WEEK, ReportRange.MONTH, ReportRange.QUARTER)),
    CATEGORIES("Kategoriler", "Kategoriler", listOf(ReportRange.WEEK, ReportRange.MONTH, ReportRange.QUARTER)),
    TABLES("Masalar", "Masalar", listOf(ReportRange.DAY, ReportRange.WEEK, ReportRange.MONTH)),
    STAFF("Personel", "Personel", listOf(ReportRange.DAY, ReportRange.WEEK, ReportRange.MONTH, ReportRange.QUARTER)),
    QR("QR menü", "QR menü", listOf(ReportRange.WEEK, ReportRange.MONTH, ReportRange.QUARTER)),
}

/** One remote load of a card; [data] stays while a reload runs so charts don't flash empty. */
data class CardLoad<T>(val data: T? = null, val loading: Boolean = true, val error: String? = null)

/** What one request covers: a branch (and its menu) over a [ReportWindow]. Also the cache key. */
data class ReportKey(val branchId: Long, val menuId: Long?, val window: ReportWindow)

fun Branch.reportKey(window: ReportWindow) = ReportKey(id, primaryMenuId, window)

/** Loads one endpoint per [ReportKey] on demand and keeps every key it has loaded, so switching back is instant. */
class ReportCache<T>(private val scope: CoroutineScope, private val fetch: suspend (ReportKey) -> T) {
    private val _loads = MutableStateFlow<Map<ReportKey, CardLoad<T>>>(emptyMap())
    val loads: StateFlow<Map<ReportKey, CardLoad<T>>> = _loads.asStateFlow()

    fun ensure(key: ReportKey) {
        if (key !in _loads.value) load(key)
    }

    fun reload(branchId: Long) {
        _loads.value.keys.filter { it.branchId == branchId }.forEach(::load)
    }

    private fun load(key: ReportKey) {
        _loads.update { it + (key to (it[key] ?: CardLoad()).copy(loading = true, error = null)) }
        scope.launch {
            safeCall { fetch(key) }
                .onSuccess { data -> _loads.update { it + (key to CardLoad(data, loading = false)) } }
                .onFailure { error ->
                    _loads.update { it + (key to (it[key] ?: CardLoad()).copy(loading = false, error = error.userMessage())) }
                }
        }
    }
}

/**
 * Reports keep their own branch so an owner can compare branches here without changing what the rest of the app shows.
 * They start on, and follow changes of, the branch picked elsewhere in the app.
 */
class ReportsViewModel(repository: BusinessRepository, appBranch: Branch) : ViewModel() {
    val revenue = ReportCache(viewModelScope) { k -> repository.revenueReport(k.branchId, k.menuId, k.window.from, k.window.to) }
    val summary = ReportCache(viewModelScope) { k -> repository.summary(k.branchId, k.menuId, k.window.from, k.window.to) }
    val staff = ReportCache(viewModelScope) { k -> repository.staffReport(k.branchId, k.menuId, k.window.from, k.window.to) }

    private val _branch = MutableStateFlow(appBranch)
    val branch: StateFlow<Branch> = _branch.asStateFlow()
    private var lastAppBranchId = appBranch.id

    private val _ranges = MutableStateFlow(
        ReportCard.entries.associateWith { card -> if (ReportRange.WEEK in card.ranges) ReportRange.WEEK else card.ranges.first() },
    )
    val ranges: StateFlow<Map<ReportCard, ReportRange>> = _ranges.asStateFlow()

    init {
        ensureAll()
    }

    fun selectBranch(branch: Branch) {
        if (branch.id == _branch.value.id) return
        _branch.value = branch
        ensureAll()
    }

    /** Switches along only when the app-wide branch really changed, not every time the screen is shown again. */
    fun followAppBranch(appBranch: Branch) {
        if (appBranch.id == lastAppBranchId) return
        lastAppBranchId = appBranch.id
        selectBranch(appBranch)
    }

    fun selectRange(card: ReportCard, range: ReportRange) {
        _ranges.update { it + (card to range) }
        ensureData(card, range)
    }

    fun refresh() {
        val branchId = _branch.value.id
        revenue.reload(branchId)
        summary.reload(branchId)
        staff.reload(branchId)
    }

    private fun ensureAll() {
        _ranges.value.forEach { (card, range) -> ensureData(card, range) }
        // The hub's previews always show the last 7 days, whatever range a detail card was left on.
        ReportCard.entries.forEach { ensureData(it, ReportRange.WEEK) }
    }

    private fun ensureData(card: ReportCard, range: ReportRange) {
        val branch = _branch.value
        val window = range.window()
        when (card) {
            ReportCard.TREND -> {
                revenue.ensure(branch.reportKey(window))
                revenue.ensure(branch.reportKey(window.previous()))
                summary.ensure(branch.reportKey(window))
            }
            ReportCard.HOURS, ReportCard.PAYMENTS, ReportCard.PRODUCTS, ReportCard.CATEGORIES -> revenue.ensure(branch.reportKey(window))
            ReportCard.TABLES, ReportCard.QR -> summary.ensure(branch.reportKey(window))
            ReportCard.STAFF -> staff.ensure(branch.reportKey(window))
        }
    }
}

/** The load of [branch] over [range] (or the period right before it), or a pending one while it hasn't started. */
fun <T> Map<ReportKey, CardLoad<T>>.forRange(branch: Branch, range: ReportRange, previous: Boolean = false): CardLoad<T> =
    get(branch.reportKey(range.window().let { if (previous) it.previous() else it })) ?: CardLoad()
