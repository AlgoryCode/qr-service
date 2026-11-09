package com.algoryqr.staff.feature.waiter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.model.Bill
import com.algoryqr.shared.model.CatalogProduct
import com.algoryqr.shared.model.ProductOptionGroup
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.AppTextField
import com.algoryqr.shared.ui.ChoiceChip
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.OutlineButton
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.QuantityStepper
import com.algoryqr.shared.ui.SectionLabel
import com.algoryqr.shared.ui.StatusChip
import com.algoryqr.staff.data.Cart
import com.algoryqr.staff.data.CartLine

/** Notes waiters type most; one tap instead of the keyboard. */
private val QUICK_NOTES = listOf("Az pişmiş", "İyi pişmiş", "Acılı", "Acısız", "Soğansız", "Buzsuz", "Şekersiz", "Paket")

/** From this width the cart stays open beside the menu (tablets, landscape). */
private val SPLIT_WIDTH = 760.dp

@Composable
fun OrderComposerScreen(
    viewModel: OrderComposerViewModel,
    tableName: String,
    /** The branch runs the kitchen process; otherwise "Mutfağa gönder" is disabled. */
    kitchenActive: Boolean,
    /** Returns to the table screen, where the bill lives. */
    onBack: () -> Unit,
    /** [toKitchen]: sent with "Mutfağa gönder" rather than "Adisyona ekle". */
    onSent: (toKitchen: Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedTab by viewModel.tab.collectAsStateWithLifecycle()
    val sendState by viewModel.sendState.collectAsStateWithLifecycle()
    val restoredItems by viewModel.restoredItems.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var configuring by remember { mutableStateOf<CatalogProduct?>(null) }
    var cartOpen by remember { mutableStateOf(false) }
    // Search stays a top-bar icon until needed, so the grid gets the row back.
    var searchOpen by remember { mutableStateOf(query.isNotBlank()) }

    LaunchedEffect(sendState) {
        if (sendState == SendState.Sent) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onSent(viewModel.lastSendToKitchen)
        }
    }

    AppScreen(
        title = tableName,
        onBack = onBack,
        actions = {
            if (!searchOpen) {
                IconButton(onClick = { searchOpen = true }) { Icon(AppIcons.Search, contentDescription = "Ürün ara", tint = AppColors.Ink) }
            }
        },
    ) {
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) { menu ->
            val tab = selectedTab ?: if (menu.popular.isNotEmpty()) BrowseTab.Popular else BrowseTab.All
            val products = viewModel.visibleProducts(menu, query, tab)
            val locked = sendState != SendState.Idle
            val onTap: (CatalogProduct) -> Unit = { product ->
                if (!locked) {
                    if (product.needsChoice) {
                        configuring = product
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.addPlain(product)
                    }
                }
            }
            val onLongPress: (CatalogProduct) -> Unit = { product ->
                if (!locked) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    configuring = product
                }
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val split = maxWidth >= SPLIT_WIDTH
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        TableStrip(menu, onBack)
                        if (restoredItems > 0 && !cart.isEmpty) {
                            RestoredCartBanner(restoredItems, onClear = viewModel::clearCart, onKeep = viewModel::keepRestored)
                        }
                        AnimatedVisibility(visible = searchOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            SearchField(
                                query = query,
                                onChange = viewModel::onQueryChange,
                                onClose = {
                                    viewModel.onQueryChange("")
                                    searchOpen = false
                                },
                            )
                        }
                        BrowseTabs(menu, tab, query.isNotBlank(), viewModel::selectTab)
                        ProductGrid(
                            products = products,
                            cart = cart,
                            emptyHint = if (query.isNotBlank()) "\"$query\" ile eşleşen ürün yok." else "Bu bölümde ürün yok.",
                            onTap = onTap,
                            onLongPress = onLongPress,
                            onRemoveOne = { if (!locked) viewModel.removeOne(it) },
                            onRemoveAll = { if (!locked) viewModel.removeProduct(it) },
                            onSetQuantity = { product, quantity -> if (!locked) viewModel.setProductQuantity(product, quantity) },
                            modifier = Modifier.weight(1f),
                        )
                        if (!split) {
                            // Slides up with the first product, so sending never needs the cart sheet.
                            AnimatedVisibility(
                                visible = !cart.isEmpty || locked,
                                enter = slideInVertically { it } + fadeIn(),
                                exit = slideOutVertically { it } + fadeOut(),
                            ) {
                                BottomArea(cart, sendState, kitchenActive, menu.openBill, viewModel.lastSendToKitchen, onOpenCart = { cartOpen = true }, onSend = viewModel::send, onUndo = viewModel::undo)
                            }
                        }
                    }
                    if (split) {
                        Column(
                            Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                                .background(AppColors.Surface)
                                .border(1.dp, AppColors.Line)
                                .navigationBarsPadding()
                                .imePadding(),
                        ) {
                            CartContent(
                                cart = cart,
                                firstVisit = menu.firstVisit,
                                locked = locked,
                                kitchenActive = kitchenActive,
                                viewModel = viewModel,
                                modifier = Modifier.weight(1f),
                            )
                            BottomArea(cart, sendState, kitchenActive, menu.openBill, viewModel.lastSendToKitchen, onOpenCart = null, onSend = viewModel::send, onUndo = viewModel::undo)
                        }
                    }
                }
            }

            if (cartOpen) {
                ModalBottomSheet(
                    onDismissRequest = { cartOpen = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = AppColors.Surface,
                ) {
                    CartContent(
                        cart = cart,
                        firstVisit = menu.firstVisit,
                        locked = locked,
                        kitchenActive = kitchenActive,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    BottomArea(
                        cart,
                        sendState,
                        kitchenActive,
                        menu.openBill,
                        viewModel.lastSendToKitchen,
                        onOpenCart = null,
                        onSend = { toKitchen ->
                            cartOpen = false
                            viewModel.send(toKitchen)
                        },
                        onUndo = viewModel::undo,
                    )
                }
            }
        }
    }

    configuring?.let { product ->
        OptionSheet(
            product = product,
            onDismiss = { configuring = null },
            onAdd = { line ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.addLine(line)
                configuring = null
            },
        )
    }
}

/** Leftover cart from an earlier visit to this screen; the waiter decides before anything is sent with it. */
@Composable
private fun RestoredCartBanner(items: Int, onClear: () -> Unit, onKeep: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Amber.container)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            "Kaydedilmiş sepet açıldı · $items ürün",
            color = AppColors.Amber.content,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) { Text("Temizle", color = AppColors.Danger) }
        TextButton(onClick = onKeep) { Text("Devam", color = AppColors.Ink) }
    }
}

/** What is already on the bill, as context while picking; a tap goes back to the table screen. */
@Composable
private fun TableStrip(menu: OrderMenu, onOpenTable: () -> Unit) {
    val bill = menu.openBill
    if (bill == null && menu.openOrderCount == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Blue.container)
            .clickable(onClick = onOpenTable)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(AppIcons.Bill, contentDescription = null, tint = AppColors.Blue.content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            listOfNotNull(
                bill?.let { "Adisyon ${formatMoney(it.remainingTotal, it.currency)}" },
                menu.openOrderCount.takeIf { it > 0 }?.let { "$it sipariş yolda" },
            ).joinToString(" · "),
            color = AppColors.Blue.content,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(AppIcons.Next, contentDescription = null, tint = AppColors.Blue.content, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Ürün ara", color = AppColors.Muted) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = AppColors.Muted) },
        trailingIcon = {
            IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = "Aramayı kapat", tint = AppColors.Muted) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.AccentText,
            unfocusedBorderColor = AppColors.Line,
            focusedContainerColor = AppColors.Surface,
            unfocusedContainerColor = AppColors.Surface,
            cursorColor = AppColors.AccentText,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).focusRequester(focus),
    )
}

@Composable
private fun BrowseTabs(menu: OrderMenu, selected: BrowseTab, searching: Boolean, onSelect: (BrowseTab) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        if (menu.popular.isNotEmpty()) {
            ChoiceChip("★ Sık satılanlar", selected = !searching && selected == BrowseTab.Popular, onClick = { onSelect(BrowseTab.Popular) })
        }
        ChoiceChip("Tümü", selected = !searching && selected == BrowseTab.All, onClick = { onSelect(BrowseTab.All) })
        menu.categories.forEach { name ->
            val tab = BrowseTab.Category(name)
            ChoiceChip(name, selected = !searching && selected == tab, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun ProductGrid(
    products: List<CatalogProduct>,
    cart: Cart,
    emptyHint: String,
    onTap: (CatalogProduct) -> Unit,
    onLongPress: (CatalogProduct) -> Unit,
    onRemoveOne: (CatalogProduct) -> Unit,
    onRemoveAll: (CatalogProduct) -> Unit,
    onSetQuantity: (CatalogProduct, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typing by remember { mutableStateOf<CatalogProduct?>(null) }
    typing?.let { product ->
        QuantityDialog(
            product = product,
            current = cart.quantityOf(product.productId),
            onDismiss = { typing = null },
            onConfirm = { quantity ->
                onSetQuantity(product, quantity)
                typing = null
            },
        )
    }
    LazyVerticalGrid(
        // One row per product on phones; tablets fit two columns of rows.
        columns = GridCells.Adaptive(320.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        if (products.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Ürün bulunamadı", emptyHint, icon = AppIcons.Product) }
        }
        items(products, key = { it.productId }) { product ->
            ProductCard(
                product = product,
                quantity = cart.quantityOf(product.productId),
                onAdd = onTap,
                onLongPress = onLongPress,
                onRemoveOne = onRemoveOne,
                onRemoveAll = onRemoveAll,
                onTypeQuantity = { typing = it },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "+ ile ekle · Adede dokun: elle gir · Basılı tut: seçenek ve not",
                color = AppColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * One product as a list row: a small photo, name and price, and on the right the "+" that puts it in the cart.
 * Tapping the row itself never adds, so a stray tap can't double an order. Once in the cart the stepper changes the
 * quantity, the number opens a field to type it, and the "✕" on the photo takes the product out. A long press opens
 * options and notes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductCard(
    product: CatalogProduct,
    quantity: Int,
    onAdd: (CatalogProduct) -> Unit,
    onLongPress: (CatalogProduct) -> Unit,
    onRemoveOne: (CatalogProduct) -> Unit,
    onRemoveAll: (CatalogProduct) -> Unit,
    onTypeQuantity: (CatalogProduct) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val selected = quantity > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PRODUCT_ROW_HEIGHT)
            .clip(shape)
            .background(if (selected) AppColors.Red.container else AppColors.Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) AppColors.Accent else AppColors.Line, shape)
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(product) })
            .padding(horizontal = 10.dp),
    ) {
        Box {
            ProductImage(product)
            if (selected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AppColors.Surface)
                        .border(1.dp, AppColors.Line, CircleShape)
                        .clickable { onRemoveAll(product) },
                ) {
                    Icon(AppIcons.Close, contentDescription = "İptal", tint = AppColors.AccentText, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatMoney(product.price, product.currency), color = AppColors.AccentText, style = MaterialTheme.typography.labelLarge)
                if (product.optionGroups.isNotEmpty()) {
                    StatusChip(if (product.needsChoice) "Seçim gerekli" else "Seçenekli", if (product.needsChoice) AppColors.Amber else null)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (selected) {
            QuantityStepper(
                quantity = quantity,
                onRemoveOne = { onRemoveOne(product) },
                onAddOne = { onAdd(product) },
                onTypeQuantity = { onTypeQuantity(product) },
            )
        } else {
            StepperButton(icon = AppIcons.Add, description = "Ekle", onClick = { onAdd(product) }, filled = true)
        }
    }
}

/** The product photo as a small avatar, or the dish icon when the menu has none or it fails to load. */
@Composable
private fun ProductImage(product: CatalogProduct) {
    val modifier = Modifier.padding(4.dp).size(52.dp).clip(RoundedCornerShape(12.dp))
    val placeholder = @Composable {
        Box(contentAlignment = Alignment.Center, modifier = modifier.background(AppColors.Subtle)) {
            Icon(AppIcons.Product, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(24.dp))
        }
    }
    val url = product.imageUrl?.takeIf { it.isNotBlank() }
    if (url == null) {
        placeholder()
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = product.name,
            contentScale = ContentScale.Crop,
            loading = { placeholder() },
            error = { placeholder() },
            modifier = modifier,
        )
    }
}

/** "−" drops one (at the last one the "✕" on the photo takes it out), the number opens [QuantityDialog], "+" adds one. */
@Composable
private fun QuantityStepper(quantity: Int, onRemoveOne: () -> Unit, onAddOne: () -> Unit, onTypeQuantity: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton(icon = AppIcons.Remove, description = "Bir azalt", onClick = onRemoveOne)
        Text(
            quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(44.dp)
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onTypeQuantity)
                .padding(vertical = 6.dp),
        )
        StepperButton(icon = AppIcons.Add, description = "Bir artır", onClick = onAddOne, filled = true)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit, filled: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(if (filled) AppColors.Accent else AppColors.Surface)
            .border(1.dp, if (filled) AppColors.Accent else AppColors.Line, shape)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = if (filled) Color.White else AppColors.AccentText, modifier = Modifier.size(18.dp))
    }
}

/** Typing "12" is quicker than twelve taps on "+"; 0 takes the product out. */
@Composable
private fun QuantityDialog(product: CatalogProduct, current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    // Starts empty and focused: the waiter types the new number straight away instead of deleting the old one.
    var text by remember { mutableStateOf("") }
    val quantity = text.toIntOrNull()?.takeIf { it in 0..MAX_TYPED_QUANTITY }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text(product.name, style = MaterialTheme.typography.titleLarge) },
        text = {
            AppTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(3) },
                label = "Adet (şu an $current)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { quantity?.let(onConfirm) }, enabled = quantity != null) {
                Text("Tamam", color = if (quantity != null) AppColors.AccentText else AppColors.Muted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = AppColors.Muted) } },
    )
}

private const val MAX_TYPED_QUANTITY = 99

/** Every row the same height, whatever the name length or chips, so the list stays even. */
private val PRODUCT_ROW_HEIGHT = 76.dp

/**
 * Sticky footer. Under the product grid it is the cart summary (opens the cart) beside the bill button; inside the cart it holds
 * the send buttons: "Adisyona ekle" (straight onto the bill, or "Adisyon oluştur" when the table has none yet) and,
 * while the branch runs the kitchen, "Mutfağa gönder". Once sending has started it turns into the undo countdown.
 */
@Composable
private fun BottomArea(
    cart: Cart,
    sendState: SendState,
    kitchenActive: Boolean,
    /** The table's open bill; with it the footer shows what it comes to once the cart is added. */
    bill: Bill?,
    sendingToKitchen: Boolean,
    onOpenCart: (() -> Unit)?,
    onSend: (toKitchen: Boolean) -> Unit,
    onUndo: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(AppColors.Surface)) {
        HorizontalDivider(color = AppColors.Line)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (sendState) {
                is SendState.Countdown, SendState.Sending -> SendingBar(sendState, sendingToKitchen, onUndo)
                else -> {
                    val billLabel = if (bill != null) "Adisyona ekle" else "Adisyon oluştur"
                    if (bill != null && !cart.isEmpty) BillTotalsPreview(bill, cart.total)
                    if (onOpenCart != null) {
                        // Cart summary opens the sheet for notes and edits; the button sends right from the grid.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(
                                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenCart).padding(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${cart.itemCount} ürün", style = MaterialTheme.typography.labelLarge)
                                    Icon(AppIcons.Next, contentDescription = "Sepeti aç", tint = AppColors.Muted, modifier = Modifier.size(14.dp))
                                }
                                Text(formatMoney(cart.total), style = MaterialTheme.typography.titleMedium, color = AppColors.AccentText)
                            }
                            // Bill first: nothing reaches the kitchen without a bill; the table screen sends it on.
                            PrimaryButton(billLabel, onClick = { onSend(false) }, icon = AppIcons.Bill, modifier = Modifier.weight(1f))
                        }
                    } else {
                        if (kitchenActive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton(billLabel, onClick = { onSend(false) }, enabled = !cart.isEmpty, icon = AppIcons.Bill, modifier = Modifier.weight(1f))
                                OutlineButton("Mutfağa gönder", onClick = { onSend(true) }, enabled = !cart.isEmpty, icon = AppIcons.Kitchen, modifier = Modifier.weight(1f))
                            }
                        } else {
                            PrimaryButton(billLabel, onClick = { onSend(false) }, enabled = !cart.isEmpty, icon = AppIcons.Bill)
                        }
                    }
                }
            }
        }
    }
}

/** "Mevcut adisyon + Eklenen = Yeni toplam", so the waiter can tell the guest the new amount before sending. */
@Composable
private fun BillTotalsPreview(bill: Bill, added: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Subtle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        TotalsCell("Mevcut adisyon", formatMoney(bill.totalAmount, bill.currency), Modifier.weight(1f))
        Text("+", color = AppColors.Muted, style = MaterialTheme.typography.titleMedium)
        TotalsCell("Eklenen", formatMoney(added, bill.currency), Modifier.weight(1f), AppColors.AccentText)
        Text("=", color = AppColors.Muted, style = MaterialTheme.typography.titleMedium)
        TotalsCell("Yeni toplam", formatMoney(bill.totalAmount + added, bill.currency), Modifier.weight(1f), emphasized = true)
    }
}

@Composable
private fun TotalsCell(label: String, value: String, modifier: Modifier, color: Color = AppColors.Ink, emphasized: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, color = AppColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(
            value,
            color = color,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun SendingBar(sendState: SendState, toKitchen: Boolean, onUndo: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Ink)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Text(
            when (sendState) {
                is SendState.Countdown -> (if (toKitchen) "Mutfağa gönderiliyor… " else "Adisyona ekleniyor… ") + sendState.secondsLeft
                else -> "Gönderiliyor…"
            },
            color = AppColors.Base,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        if (sendState is SendState.Countdown) {
            TextButton(onClick = onUndo) { Text("Geri al", color = AppColors.Red.container, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Cart lines with quantity, swipe-to-delete and per-line notes; shown in a sheet on phones, beside the menu on tablets. */
@Composable
private fun CartContent(
    cart: Cart,
    firstVisit: Boolean,
    locked: Boolean,
    kitchenActive: Boolean,
    viewModel: OrderComposerViewModel,
    modifier: Modifier = Modifier,
) {
    var editingNote by remember { mutableStateOf<Long?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sepet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (!cart.isEmpty) Text(formatMoney(cart.total), style = MaterialTheme.typography.titleLarge, color = AppColors.AccentText)
            }
        }
        if (firstVisit) {
            item {
                AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Kişi sayısı", style = MaterialTheme.typography.titleMedium)
                            Text("Masanın ilk siparişi", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        QuantityStepper(
                            quantity = cart.coverCount ?: 0,
                            onDecrease = { viewModel.setCoverCount((cart.coverCount ?: 1) - 1) },
                            onIncrease = { viewModel.setCoverCount((cart.coverCount ?: 1) + 1) },
                        )
                    }
                }
            }
        }
        if (cart.isEmpty) {
            item {
                Text(
                    "Henüz ürün yok. Menüden ürüne dokunarak ekleyin.",
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(cart.lines, key = { it.id }) { line ->
            CartLineRow(
                line = line,
                locked = locked,
                kitchenActive = kitchenActive,
                editingNote = editingNote == line.id,
                onToggleNote = { editingNote = if (editingNote == line.id) null else line.id },
                onQuantity = { viewModel.setQuantity(line, it) },
                onNote = { viewModel.setLineNote(line, it) },
            )
        }
        if (!cart.isEmpty) {
            item {
                Spacer(Modifier.height(4.dp))
                AppTextField(cart.note, viewModel::setOrderNote, "Sipariş notu (tüm sipariş için)")
            }
        }
    }
}

@Composable
private fun CartLineRow(
    line: CartLine,
    locked: Boolean,
    kitchenActive: Boolean,
    editingNote: Boolean,
    onToggleNote: () -> Unit,
    onQuantity: (Int) -> Unit,
    onNote: (String) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && !locked) onQuantity(0)
            value == SwipeToDismissBoxValue.EndToStart && !locked
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(AppColors.Accent).padding(end = 20.dp),
            ) {
                Text("Sil", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        AppCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (line.skipKitchen && kitchenActive) {
                        Text("Mutfağa gitmez · doğrudan adisyona", color = AppColors.Blue.content, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (line.details.isNotBlank()) {
                        Text(line.details, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(formatMoney(line.total), color = AppColors.AccentText, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = onToggleNote, enabled = !locked) {
                    Icon(AppIcons.Note, contentDescription = "Not ekle", tint = if (line.note.isBlank()) AppColors.Muted else AppColors.Amber.content)
                }
                QuantityStepper(line.quantity, onDecrease = { onQuantity(line.quantity - 1) }, onIncrease = { onQuantity(line.quantity + 1) })
            }
            if (editingNote) {
                Spacer(Modifier.height(8.dp))
                QuickNoteChips(line.note, onNote)
                Spacer(Modifier.height(8.dp))
                AppTextField(line.note, onNote, "Bu ürün için not")
            }
        }
    }
}

/** Toggles a ready-made phrase in a comma-separated note, keeping whatever was typed by hand. */
@Composable
private fun QuickNoteChips(note: String, onNote: (String) -> Unit) {
    val parts = note.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        QUICK_NOTES.forEach { phrase ->
            val on = phrase in parts
            ChoiceChip(phrase, selected = on, onClick = {
                onNote((if (on) parts - phrase else parts + phrase).joinToString(", "))
            })
        }
    }
}

/** Options, notes and quantity for one product; "Sepete ekle" unlocks once every required group is answered. */
@Composable
private fun OptionSheet(product: CatalogProduct, onDismiss: () -> Unit, onAdd: (CartLine) -> Unit) {
    val groups = product.optionGroups.sortedBy { it.sortOrder }
    val chosen = remember(product.productId) { mutableStateListOf<Long>() }
    var note by remember(product.productId) { mutableStateOf("") }
    var quantity by remember(product.productId) { mutableStateOf(1) }

    val options = groups.flatMap { it.options }.associateBy { it.optionId }
    val unitPrice = product.price + chosen.sumOf { options[it]?.priceDelta ?: 0.0 }
    val missing = groups.filter { group -> group.options.count { it.optionId in chosen } < group.minSelect }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(product.name, style = MaterialTheme.typography.titleLarge)
            Text(formatMoney(product.price, product.currency), color = AppColors.AccentText, style = MaterialTheme.typography.labelLarge)
            product.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            groups.forEach { group -> OptionGroupPicker(group, chosen) }

            SectionLabel("Not", Modifier.padding(top = 12.dp))
            QuickNoteChips(note) { note = it }
            Spacer(Modifier.height(8.dp))
            AppTextField(note, { note = it }, "Mutfağa not (isteğe bağlı)")

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Adet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                QuantityStepper(quantity, onDecrease = { quantity = (quantity - 1).coerceAtLeast(1) }, onIncrease = { quantity += 1 })
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (missing.isEmpty()) "Sepete ekle · ${formatMoney(unitPrice * quantity, product.currency)}" else "Önce seçim yapın: ${missing.first().name}",
                enabled = missing.isEmpty(),
                onClick = {
                    val picked = groups.flatMap { group -> group.options.filter { it.optionId in chosen } }
                    onAdd(
                        CartLine(
                            id = 0,
                            productId = product.productId,
                            name = product.name,
                            unitPrice = unitPrice,
                            quantity = quantity,
                            optionIds = picked.map { it.optionId },
                            optionLabels = picked.map { it.name },
                            skipKitchen = product.skipKitchen,
                            note = note.trim(),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.OptionGroupPicker(group: ProductOptionGroup, chosen: MutableList<Long>) {
    val available = group.options.filter { it.available }.sortedBy { it.sortOrder }
    if (available.isEmpty()) return
    val rule = when {
        group.required && group.single -> "Zorunlu · 1 seçim"
        group.required -> "Zorunlu · ${group.minSelect}–${group.maxSelect} seçim"
        group.single -> "İsteğe bağlı"
        else -> "İsteğe bağlı · en fazla ${group.maxSelect}"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        StatusChip(rule, if (group.required) AppColors.Amber else null)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        available.forEach { option ->
            val on = option.optionId in chosen
            val label = if (option.priceDelta != 0.0) "${option.name}  +${formatMoney(option.priceDelta)}" else option.name
            ChoiceChip(label, selected = on, onClick = {
                val inGroup = group.options.map { it.optionId }.toSet()
                when {
                    on -> chosen.remove(option.optionId)
                    // A single-choice group behaves like radio buttons.
                    group.single -> {
                        chosen.removeAll { it in inGroup }
                        chosen.add(option.optionId)
                    }
                    chosen.count { it in inGroup } < group.maxSelect -> chosen.add(option.optionId)
                }
            })
        }
    }
}
