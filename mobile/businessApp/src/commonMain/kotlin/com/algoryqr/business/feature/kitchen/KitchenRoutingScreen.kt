package com.algoryqr.business.feature.kitchen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.business.data.BusinessRepository
import com.algoryqr.business.data.MenuCategory
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SwitchRow

class KitchenRoutingViewModel(
    private val repository: BusinessRepository,
    private val menuId: Long?,
) : LoadViewModel<List<MenuCategory>>() {

    init {
        start()
    }

    override suspend fun fetch(): List<MenuCategory> = menuId?.let { repository.categories(it) }.orEmpty()

    fun setSkipKitchen(category: MenuCategory, skip: Boolean) {
        val id = menuId ?: return
        runAction { repository.setSkipKitchen(id, category.id, skip) }
    }
}

/** Categories whose products never reach the kitchen ticket, e.g. bottled drinks the waiter brings straight from the fridge. */
@Composable
fun KitchenRoutingScreen(viewModel: KitchenRoutingViewModel, kitchenEnabled: Boolean, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScreen(title = "Mutfağa gitmeyenler", onBack = onBack) {
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) { categories ->
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                item {
                    AppCard {
                        Text(
                            "İşaretlediğiniz kategorilerdeki ürünler mutfak fişine düşmez; garson siparişi gönderdiğinde " +
                                "doğrudan adisyona servis edilmiş olarak yazılır. Su, kutu içecek gibi ürünler için kullanın.",
                            color = AppColors.Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!kitchenEnabled) {
                            Text(
                                "Bu şubede mutfak kapalı; işaretler mutfak açıldığında geçerli olur.",
                                color = AppColors.Amber.content,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                if (categories.isEmpty()) {
                    item { EmptyState("Kategori yok", "Menünüze kategori ekledikten sonra burada görünür.", icon = AppIcons.Menu) }
                }
                items(categories, key = { it.id }) { category ->
                    AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        SwitchRow(
                            title = category.name,
                            checked = category.skipKitchen,
                            onCheckedChange = { viewModel.setSkipKitchen(category, it) },
                            description = if (category.skipKitchen) "Mutfağa gitmez · doğrudan adisyona" else "Mutfağa gider",
                            icon = if (category.skipKitchen) AppIcons.Tables else AppIcons.Kitchen,
                            tone = if (category.skipKitchen) AppColors.Blue else AppColors.Amber,
                            enabled = !state.busy,
                        )
                    }
                }
            }
        }
    }
}
