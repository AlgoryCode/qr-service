package com.algoryqr.staff.data

import com.algoryqr.shared.model.CartItemRequest
import com.algoryqr.shared.model.CatalogProduct
import com.algoryqr.shared.network.ApiJson
import com.algoryqr.staff.LocalStore
import kotlinx.serialization.Serializable

/**
 * One line of the waiter's cart. The same product appears on separate lines when the options or the note
 * differ ("Ayran" and "Ayran, buzsuz"), because the kitchen has to see them apart.
 */
@Serializable
data class CartLine(
    val id: Long,
    val productId: Long,
    val name: String,
    /** Price of one portion including the chosen options. */
    val unitPrice: Double,
    val quantity: Int,
    val optionIds: List<Long> = emptyList(),
    val optionLabels: List<String> = emptyList(),
    val note: String = "",
    /** Product skips the kitchen; shown so the waiter knows to bring it straight away. */
    val skipKitchen: Boolean = false,
) {
    val total: Double get() = unitPrice * quantity
    val isPlain: Boolean get() = optionIds.isEmpty() && note.isBlank()
    val details: String get() = (optionLabels + listOfNotNull(note.takeIf { it.isNotBlank() })).joinToString(" · ")

    fun toRequest() = CartItemRequest(productId, quantity, note.trim().ifBlank { null }, optionIds)
}

@Serializable
data class Cart(
    val lines: List<CartLine> = emptyList(),
    val note: String = "",
    /** Asked only on a table's first order of the visit; qr-service keeps it on the bill. */
    val coverCount: Int? = null,
) {
    val itemCount: Int get() = lines.sumOf { it.quantity }
    val total: Double get() = lines.sumOf { it.total }
    val isEmpty: Boolean get() = lines.isEmpty()

    fun quantityOf(productId: Long): Int = lines.filter { it.productId == productId }.sumOf { it.quantity }

    /** A tap on a product card: bump its plain line, or start one. */
    fun addPlain(product: CatalogProduct): Cart {
        val plain = lines.firstOrNull { it.productId == product.productId && it.isPlain }
        return if (plain != null) {
            withQuantity(plain.id, plain.quantity + 1)
        } else {
            add(CartLine(nextId(), product.productId, product.name, product.price, 1, skipKitchen = product.skipKitchen))
        }
    }

    /** Adds a configured line, merging it into an identical one so the kitchen ticket stays short. */
    fun add(line: CartLine): Cart {
        val same = lines.firstOrNull {
            it.productId == line.productId && it.optionIds.sorted() == line.optionIds.sorted() && it.note.trim() == line.note.trim()
        }
        return if (same != null) withQuantity(same.id, same.quantity + line.quantity) else copy(lines = lines + line.copy(id = nextId()))
    }

    /** Taking a product off from its card removes from the newest line first, which is usually the mistake. */
    fun removeOne(productId: Long): Cart {
        val last = lines.lastOrNull { it.productId == productId } ?: return this
        return withQuantity(last.id, last.quantity - 1)
    }

    /** The "✕" on a product card: every line of that product leaves the cart. */
    fun removeProduct(productId: Long): Cart = copy(lines = lines.filterNot { it.productId == productId })

    /** A quantity typed on the card: the difference goes to (or comes off) the newest line of the product. */
    fun withProductQuantity(productId: Long, quantity: Int): Cart {
        if (quantity <= 0) return removeProduct(productId)
        val last = lines.lastOrNull { it.productId == productId } ?: return this
        var cart = withQuantity(last.id, last.quantity + quantity - quantityOf(productId))
        while (cart.quantityOf(productId) > quantity) cart = cart.removeOne(productId)
        return cart
    }

    fun withQuantity(lineId: Long, quantity: Int): Cart = copy(
        lines = if (quantity <= 0) lines.filterNot { it.id == lineId } else lines.map { if (it.id == lineId) it.copy(quantity = quantity) else it },
    )

    fun withLineNote(lineId: Long, note: String): Cart = copy(lines = lines.map { if (it.id == lineId) it.copy(note = note) else it })

    private fun nextId(): Long = (lines.maxOfOrNull { it.id } ?: 0) + 1
}

/** Unsent carts per table, kept on the device so switching tables or an app restart loses nothing. */
class CartDrafts(private val store: LocalStore) {

    fun load(tableId: Long): Cart = store.get(key(tableId))
        ?.let { runCatching { ApiJson.decodeFromString<Cart>(it) }.getOrNull() }
        ?: Cart()

    fun save(tableId: Long, cart: Cart) {
        store.put(key(tableId), if (cart.isEmpty && cart.note.isBlank()) null else ApiJson.encodeToString(cart))
    }

    fun clear(tableId: Long) = store.put(key(tableId), null)

    private fun key(tableId: Long) = "cart_draft_$tableId"
}
