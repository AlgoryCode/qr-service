package com.algoryqr.staff.feature.waiter

import com.algoryqr.shared.format.searchKey
import com.algoryqr.shared.model.Bill
import com.algoryqr.shared.model.CatalogProduct
import com.algoryqr.shared.model.TableBillStatus
import com.algoryqr.shared.model.WaiterCreateOrderRequest
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.network.userMessage
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.staff.data.Cart
import com.algoryqr.staff.data.CartDrafts
import com.algoryqr.staff.data.CartLine
import com.algoryqr.staff.data.StaffRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Seconds the waiter has to undo a send before the order reaches the kitchen. */
private const val UNDO_SECONDS = 4

private const val POPULAR_LIMIT = 8

/** Everything the order screen shows besides the cart. */
data class OrderMenu(
    val products: List<CatalogProduct>,
    /** Most ordered products of the day, best first; empty early in the day. */
    val popular: List<CatalogProduct>,
    val categories: List<String>,
    val openBill: Bill?,
    val openOrderCount: Int,
) {
    /** No bill and no order yet: the waiter is seating new guests, so the screen asks how many. */
    val firstVisit: Boolean get() = openBill == null && openOrderCount == 0
}

sealed interface BrowseTab {
    data object Popular : BrowseTab
    data object All : BrowseTab
    data class Category(val name: String) : BrowseTab
}

sealed interface SendState {
    data object Idle : SendState
    data class Countdown(val secondsLeft: Int) : SendState
    data object Sending : SendState
    data object Sent : SendState
}

val CatalogProduct.category: String get() = mainCategoryName ?: subCategoryName ?: "Diğer"

class OrderComposerViewModel(
    private val repository: StaffRepository,
    private val drafts: CartDrafts,
    /** Outlives the screen, so leaving during the undo window still sends the order instead of dropping it. */
    private val appScope: CoroutineScope,
    private val tableId: Long,
) : LoadViewModel<OrderMenu>() {

    private val _cart = MutableStateFlow(drafts.load(tableId))
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    /**
     * Item count of an unsent cart found on opening, so the screen can say so: otherwise a tap on "Adisyona ekle"
     * would quietly send leftovers along with what the waiter just picked.
     */
    private val _restoredItems = MutableStateFlow(_cart.value.itemCount)
    val restoredItems: StateFlow<Int> = _restoredItems.asStateFlow()

    fun keepRestored() {
        _restoredItems.value = 0
    }

    fun clearCart() {
        _restoredItems.value = 0
        updateCart { Cart() }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tab = MutableStateFlow<BrowseTab?>(null)
    val tab: StateFlow<BrowseTab?> = _tab.asStateFlow()

    /** Which button started the current send, for the countdown text. */
    private var _lastSendToKitchen = false
    val lastSendToKitchen: Boolean get() = _lastSendToKitchen

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()
    private var sendJob: Job? = null

    init {
        start()
    }

    override suspend fun fetch(): OrderMenu = coroutineScope {
        val catalog = async { repository.catalog(tableId).products.filter { it.available } }
        // The extras only decorate the screen; a failure there must not block taking the order.
        val today = async { safeCall { repository.todayOrders() }.getOrNull().orEmpty() }
        val bill = async { safeCall { repository.tableBill(tableId) }.getOrNull() }
        val tableOrders = async { safeCall { repository.tableOrders(tableId) }.getOrNull().orEmpty() }

        val products = catalog.await()
        val byId = products.associateBy { it.productId }
        val popular = today.await()
            .flatMap { order -> order.items }
            .groupBy { it.productId }
            .mapNotNull { (productId, items) -> byId[productId]?.let { it to items.sumOf { item -> item.quantity } } }
            .sortedByDescending { it.second }
            .take(POPULAR_LIMIT)
            .map { it.first }
        OrderMenu(
            products = products,
            popular = popular,
            categories = products.map { it.category }.distinct(),
            openBill = bill.await()?.takeIf { it.status == TableBillStatus.OPEN },
            openOrderCount = tableOrders.await().count { it.status.isOpen },
        )
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun selectTab(tab: BrowseTab) {
        _tab.value = tab
        _query.value = ""
    }

    /** Products for the current search or tab; a search always looks through the whole menu. */
    fun visibleProducts(menu: OrderMenu, query: String, tab: BrowseTab): List<CatalogProduct> {
        val key = query.trim().searchKey()
        if (key.isNotEmpty()) {
            return menu.products.filter { product ->
                val name = product.name.searchKey()
                name.contains(key) || name.split(' ').any { it.startsWith(key) } || product.category.searchKey().contains(key)
            }
        }
        return when (tab) {
            BrowseTab.Popular -> menu.popular
            BrowseTab.All -> menu.products
            is BrowseTab.Category -> menu.products.filter { it.category == tab.name }
        }
    }

    fun addPlain(product: CatalogProduct) = updateCart { it.addPlain(product) }

    fun removeOne(product: CatalogProduct) = updateCart { it.removeOne(product.productId) }

    fun removeProduct(product: CatalogProduct) = updateCart { it.removeProduct(product.productId) }

    fun setProductQuantity(product: CatalogProduct, quantity: Int) = updateCart { it.withProductQuantity(product.productId, quantity) }

    fun addLine(line: CartLine) = updateCart { it.add(line) }

    fun setQuantity(line: CartLine, quantity: Int) = updateCart { it.withQuantity(line.id, quantity) }

    fun setLineNote(line: CartLine, note: String) = updateCart { it.withLineNote(line.id, note) }

    fun setOrderNote(note: String) = updateCart { it.copy(note = note) }

    fun setCoverCount(count: Int?) = updateCart { it.copy(coverCount = count?.coerceIn(1, 50)) }

    private fun updateCart(transform: (Cart) -> Cart) {
        if (_sendState.value != SendState.Idle) return
        val next = transform(_cart.value)
        _cart.value = next
        drafts.save(tableId, next)
    }

    /**
     * Sends the cart. "Mutfağa gönder" ([toKitchen]) first runs an undo countdown, since a ticket reaching the kitchen
     * is hard to take back; "Adisyona ekle" goes straight onto the bill without one.
     */
    fun send(toKitchen: Boolean) {
        val cart = _cart.value
        if (cart.isEmpty || _sendState.value != SendState.Idle) return
        val firstVisit = state.value.data?.firstVisit == true
        val request = WaiterCreateOrderRequest(
            tableId = tableId,
            items = cart.lines.map { it.toRequest() },
            note = cart.note.trim().ifBlank { null },
            coverCount = cart.coverCount.takeIf { firstVisit },
            sendToKitchen = toKitchen,
        )
        _lastSendToKitchen = toKitchen
        sendJob = appScope.launch {
            if (toKitchen) {
                for (left in UNDO_SECONDS downTo 1) {
                    _sendState.value = SendState.Countdown(left)
                    delay(1_000)
                }
            }
            _sendState.value = SendState.Sending
            safeCall { repository.createOrder(request) }
                .onSuccess {
                    drafts.clear(tableId)
                    _cart.value = Cart()
                    _sendState.value = SendState.Sent
                }
                .onFailure { error ->
                    // The cart stays as it was, so the waiter can simply try again.
                    _sendState.value = SendState.Idle
                    setActionError(error.userMessage())
                }
        }
    }

    fun undo() {
        if (_sendState.value is SendState.Countdown) {
            sendJob?.cancel()
            _sendState.value = SendState.Idle
        }
    }
}
