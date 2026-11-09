package com.algoryqr.business.feature.tables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.business.data.Branch
import com.algoryqr.business.data.BusinessRepository
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.model.TableSummary
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.StatTile
import com.algoryqr.shared.ui.TableTile

class TablesViewModel(
    private val repository: BusinessRepository,
    private val merchantId: Long?,
    private val branch: Branch,
) : LoadViewModel<List<TableSummary>>(pollMillis = 20_000) {

    init {
        start()
    }

    override suspend fun fetch(): List<TableSummary> =
        repository.tables(merchantId ?: branch.merchantId ?: error("İşletme bilgisi bulunamadı"), branch.id)
            .filter { it.active }
            .sortedWith(compareBy({ it.areaName.orEmpty() }, { it.tableNumber ?: Int.MAX_VALUE }, { it.title }))
}

@Composable
fun TablesScreen(viewModel: TablesViewModel, branchPicker: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScreen(title = "Masalar", actions = { RefreshAction(viewModel::refresh) }) {
        branchPicker()
        LoadContent(state, onRetry = viewModel::refresh) { tables ->
            val occupied = tables.count { it.isOccupied }
            val openTotal = tables.sumOf { it.openBillTotal ?: 0.0 }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = ScreenPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { StatTile("Dolu masa", "$occupied / ${tables.size}", AppIcons.Tables, AppColors.Red) }
                item { StatTile("Açık hesap", formatMoney(openTotal), AppIcons.Bill, AppColors.Amber) }
                if (tables.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        EmptyState("Masa tanımlı değil", "Masaları web panelinden ekleyebilirsiniz.", icon = AppIcons.Tables)
                    }
                }
                items(tables, key = { it.tableId }) { table -> TableTile(table) }
            }
        }
    }
}

