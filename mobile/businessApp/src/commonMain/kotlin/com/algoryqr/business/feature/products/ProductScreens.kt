package com.algoryqr.business.feature.products

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.algoryqr.business.data.BusinessRepository
import com.algoryqr.business.data.CreateProductRequest
import com.algoryqr.business.data.MenuCategory
import com.algoryqr.business.data.MenuProduct
import com.algoryqr.shared.ui.SegmentedSwitch
import com.algoryqr.business.data.NutritionFacts
import com.algoryqr.business.data.NutritionBasis
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.network.userMessage
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.AppTextField
import com.algoryqr.shared.ui.ChoiceChip
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.ErrorText
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SectionLabel
import com.algoryqr.shared.ui.StatusChip
import com.algoryqr.shared.ui.SwitchRow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.round

/** The menu's categories with its products, for the list and its category filter. */
data class ProductCatalog(val categories: List<MenuCategory>, val products: List<MenuProduct>)

class ProductsViewModel(private val repository: BusinessRepository, private val menuId: Long) : LoadViewModel<ProductCatalog>() {

    init {
        start()
    }

    override suspend fun fetch(): ProductCatalog = coroutineScope {
        val categories = async { repository.categories(menuId) }
        val products = async { repository.products(menuId) }
        ProductCatalog(categories.await(), products.await().sortedWith(compareBy({ it.mainCategoryName }, { it.sortOrder })))
    }
}

/** Every product of the branch's menu, filtered by main category; "+" opens the form for a new one. */
@Composable
fun ProductsScreen(viewModel: ProductsViewModel, onBack: () -> Unit, onAdd: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf<Long?>(null) }
    // Coming back from the form should show the new product straight away.
    LaunchedEffect(Unit) { viewModel.refresh() }

    AppScreen(
        title = "Ürünler",
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) { Icon(AppIcons.Add, contentDescription = "Ürün ekle", tint = AppColors.AccentText) }
        },
    ) {
        LoadContent(state, onRetry = viewModel::refresh) { catalog ->
            val visible = catalog.products.filter { category == null || it.mainCategoryId == category }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                ChoiceChip("Tümü (${catalog.products.size})", selected = category == null, onClick = { category = null })
                catalog.categories.forEach { main ->
                    val count = catalog.products.count { it.mainCategoryId == main.id }
                    ChoiceChip("${main.name} ($count)", selected = category == main.id, onClick = { category = main.id })
                }
            }
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            "Ürün yok",
                            "Sağ üstteki + ile menüye ilk ürünü ekleyin.",
                            icon = AppIcons.Product,
                        )
                    }
                }
                items(visible, key = { it.productId }) { product -> ProductRow(product) }
            }
        }
    }
}

@Composable
private fun ProductRow(product: MenuProduct) {
    AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(product.mainCategoryName, product.subCategoryName).joinToString(" · "),
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(product.price?.let { formatMoney(it, product.currency) } ?: "Fiyat yok", style = MaterialTheme.typography.labelLarge)
                if (!product.available) StatusChip("Satışta değil", AppColors.Amber)
            }
        }
    }
}

data class ProductFormState(
    val categories: List<MenuCategory> = emptyList(),
    val loading: Boolean = true,
    val name: String = "",
    val priceText: String = "",
    val subCategoryId: Long? = null,
    val description: String = "",
    val available: Boolean = true,
    val basis: NutritionBasis = NutritionBasis.PER_100G,
    /** Nutrition inputs as typed, keyed by [NutritionField]. */
    val nutrition: Map<NutritionField, String> = emptyMap(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    /** Lira with an optional comma or dot for kuruş; blank means no price yet. */
    val price: Double? get() = priceText.replace(',', '.').toDoubleOrNull()?.let { round(it * 100) / 100 }
    val priceValid: Boolean get() = priceText.isBlank() || (price != null && price!! >= 0)
    fun amount(field: NutritionField): Double? =
        nutrition[field]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it >= 0 }

    /** The calories are in; a value typed into an optional line must still be a number of zero or more. */
    val nutritionComplete: Boolean get() = NutritionField.entries.all { field ->
        val typed = nutrition[field].orEmpty()
        if (field.required) amount(field) != null else typed.isBlank() || amount(field) != null
    }

    val canSubmit: Boolean get() = name.isNotBlank() && subCategoryId != null && priceValid && nutritionComplete

    /** kJ is not asked for: it follows from kcal (1 kcal = 4.184 kJ). */
    fun nutritionFacts(): NutritionFacts? {
        val kcal = amount(NutritionField.ENERGY_KCAL) ?: return null
        return NutritionFacts(
            basis = basis,
            energyKj = round(kcal * KJ_PER_KCAL * 10) / 10,
            energyKcal = kcal,
            fat = amount(NutritionField.FAT),
            saturatedFat = amount(NutritionField.SATURATED_FAT),
            carbohydrate = amount(NutritionField.CARBOHYDRATE),
            sugars = amount(NutritionField.SUGARS),
            fibre = amount(NutritionField.FIBRE),
            protein = amount(NutritionField.PROTEIN),
            salt = amount(NutritionField.SALT),
        )
    }
}

private const val KJ_PER_KCAL = 4.184

/** The label lines in the order a nutrition table shows them; only the calories are required. */
enum class NutritionField(val label: String, val unit: String, val required: Boolean = false, val indented: Boolean = false) {
    ENERGY_KCAL("Enerji", "kcal", required = true),
    FAT("Yağ", "g"),
    SATURATED_FAT("Doymuş yağ", "g", indented = true),
    CARBOHYDRATE("Karbonhidrat", "g"),
    SUGARS("Şekerler", "g", indented = true),
    FIBRE("Lif", "g"),
    PROTEIN("Protein", "g"),
    SALT("Tuz", "g"),
}

class ProductFormViewModel(private val repository: BusinessRepository, private val menuId: Long) : ViewModel() {
    private val _state = MutableStateFlow(ProductFormState())
    val state: StateFlow<ProductFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            safeCall { repository.categories(menuId) }
                .onSuccess { categories ->
                    // One sub-category in the whole menu: nothing to choose, so it is picked already.
                    val only = categories.flatMap { it.subs }.singleOrNull()?.id
                    _state.update { it.copy(categories = categories, loading = false, subCategoryId = it.subCategoryId ?: only) }
                }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.userMessage()) } }
        }
    }

    fun update(transform: ProductFormState.() -> ProductFormState) = _state.update { it.transform().copy(error = null) }

    fun submit() {
        val form = _state.value
        val subCategoryId = form.subCategoryId ?: return
        val nutrition = form.nutritionFacts() ?: return
        if (!form.canSubmit || form.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            safeCall {
                repository.createProduct(
                    menuId,
                    CreateProductRequest(
                        name = form.name.trim(),
                        subCategoryId = subCategoryId,
                        nutrition = nutrition,
                        price = form.price,
                        description = form.description.trim().ifBlank { null },
                        available = form.available,
                    ),
                )
            }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { error -> _state.update { it.copy(saving = false, error = error.userMessage()) } }
        }
    }
}

/** The label qr-service requires: basis, then one number per line; kJ is worked out from kcal. */
@Composable
private fun NutritionSection(state: ProductFormState, viewModel: ProductFormViewModel) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SegmentedSwitch(
                options = NutritionBasis.entries.map { it to it.label },
                selected = state.basis,
                onSelect = { basis -> viewModel.update { copy(basis = basis) } },
            )
            NutritionField.entries.forEach { field ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        field.label + if (field.required) " *" else "",
                        color = if (field.indented) AppColors.Muted else AppColors.Ink,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(start = if (field.indented) 12.dp else 0.dp),
                    )
                    AppTextField(
                        state.nutrition[field].orEmpty(),
                        { value ->
                            val clean = value.filter { it.isDigit() || it == ',' || it == '.' }.take(7)
                            viewModel.update { copy(nutrition = nutrition + (field to clean)) }
                        },
                        field.unit,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
            val kcal = state.amount(NutritionField.ENERGY_KCAL)
            Text(
                if (kcal != null) "Enerji: ${kcal.toString().removeSuffix(".0")} kcal ≈ ${(round(kcal * KJ_PER_KCAL * 10) / 10).toString().removeSuffix(".0")} kJ"
                else "kJ, kcal değerinden otomatik hesaplanır.",
                color = AppColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "* Yalnızca kalori zorunlu; diğer değerleri biliyorsanız girin.",
                color = AppColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Name, price and category are what a product needs to be sold; description and availability can follow. */
@Composable
fun ProductFormScreen(viewModel: ProductFormViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    AppScreen(title = "Yeni ürün", onBack = onBack) {
        Column(
            verticalArrangement = ItemSpacing,
            modifier = Modifier.verticalScroll(rememberScrollState()).imePadding().padding(ScreenPadding),
        ) {
            SectionLabel("Ürün")
            AppTextField(
                state.name,
                { value -> viewModel.update { copy(name = value.take(120)) } },
                "Ürün adı",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            AppTextField(
                state.priceText,
                { value -> viewModel.update { copy(priceText = value.filter { it.isDigit() || it == ',' || it == '.' }.take(9)) } },
                "Fiyat (₺)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
            if (!state.priceValid) ErrorText("Geçerli bir fiyat girin")

            SectionLabel("Kategori")
            when {
                state.loading -> Text("Kategoriler yükleniyor…", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                state.categories.none { it.subs.isNotEmpty() } -> Text(
                    "Menüde alt kategori yok. Kategorileri AlgoryQR web panelinden ekleyin.",
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> state.categories.filter { it.subs.isNotEmpty() }.forEach { main ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(main.name, color = AppColors.Muted, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            main.subs.sortedBy { it.sortOrder }.forEach { sub ->
                                ChoiceChip(sub.name, selected = state.subCategoryId == sub.id, onClick = { viewModel.update { copy(subCategoryId = sub.id) } })
                            }
                        }
                    }
                }
            }

            SectionLabel("Besin değerleri")
            NutritionSection(state, viewModel)

            SectionLabel("Ayrıntılar")
            AppTextField(
                state.description,
                { value -> viewModel.update { copy(description = value.take(500)) } },
                "Açıklama (isteğe bağlı)",
                singleLine = false,
                minLines = 3,
            )
            AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SwitchRow(
                    title = "Satışta",
                    checked = state.available,
                    onCheckedChange = { checked -> viewModel.update { copy(available = checked) } },
                    description = if (state.available) "Menüde ve sipariş ekranında görünür" else "Eklenir ama satışa kapalı kalır",
                    icon = AppIcons.Product,
                )
            }
            ErrorText(state.error)
            PrimaryButton("Ürünü ekle", onClick = viewModel::submit, enabled = state.canSubmit, loading = state.saving, icon = AppIcons.Add)
        }
    }
}
