package com.algoryqr.staff.feature.waiter

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.shared.model.TableSummary
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.ChoiceChip
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.TableTile
import com.algoryqr.staff.data.StaffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TableFilter(val label: String, val matches: (TableSummary) -> Boolean) {
    ALL("Tümü", { true }),
    ATTENTION("Bekleyen", { it.pendingOrderCount + it.readyOrderCount > 0 }),
    OCCUPIED("Dolu", { it.isOccupied }),
    FREE("Boş", { !it.isOccupied }),
}

class WaiterTablesViewModel(
    private val repository: StaffRepository,
    private val merchantId: Long?,
    private val branchId: Long?,
) : LoadViewModel<List<TableSummary>>(pollMillis = 15_000) {

    private val _filter = MutableStateFlow(TableFilter.ALL)
    val filter: StateFlow<TableFilter> = _filter.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): List<TableSummary> {
        if (merchantId == null || branchId == null) error("Hesabınız bir şubeye atanmamış. İşletme sahibinden şube ataması isteyin.")
        return repository.tables(merchantId, branchId)
            .filter { it.active }
            .sortedWith(compareBy({ it.areaName.orEmpty() }, { it.tableNumber ?: Int.MAX_VALUE }, { it.title }))
    }

    fun selectFilter(filter: TableFilter) {
        _filter.value = filter
    }
}

@Composable
fun WaiterTablesScreen(viewModel: WaiterTablesViewModel, onOpenTable: (TableSummary) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    AppScreen(title = "Masalar", actions = { RefreshAction(viewModel::refresh) }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            val tables = state.data.orEmpty()
            TableFilter.entries.forEach { option ->
                ChoiceChip("${option.label} (${tables.count(option.matches)})", selected = option == filter, onClick = { viewModel.selectFilter(option) })
            }
        }
        LoadContent(state, onRetry = viewModel::refresh) { tables ->
            val visible = tables.filter(filter.matches)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = ScreenPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (visible.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        EmptyState("Masa yok", "Bu filtrede gösterilecek masa bulunmuyor.", icon = AppIcons.Table)
                    }
                }
                items(visible, key = { it.tableId }) { table -> TableTile(table, onClick = { onOpenTable(table) }) }
            }
        }
    }
}
