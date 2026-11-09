package com.algoryqr.shared.model

import kotlinx.serialization.Serializable

/** Mirrors qr-service `MenuOrderStatus`. */
@Serializable
enum class OrderStatus {
    DRAFT, SUBMITTED, CONFIRMED, PREPARING, READY, SERVED, PAID, REJECTED, CANCELLED;

    val label: String
        get() = when (this) {
            DRAFT -> "Taslak"
            SUBMITTED -> "Onay bekliyor"
            CONFIRMED -> "Onaylandı"
            PREPARING -> "Hazırlanıyor"
            READY -> "Hazır"
            SERVED -> "Servis edildi"
            PAID -> "Ödendi"
            REJECTED -> "Reddedildi"
            CANCELLED -> "İptal"
        }

    val isOpen: Boolean get() = this in setOf(SUBMITTED, CONFIRMED, PREPARING, READY)
}

/** Mirrors qr-service `CancelReason`. */
@Serializable
enum class CancelReason(val label: String) {
    CUSTOMER_REQUEST("Müşteri isteği"),
    OUT_OF_STOCK("Stokta yok"),
    WRONG_ORDER("Yanlış sipariş"),
    LONG_WAIT("Uzun bekleme"),
    PAYMENT_ISSUE("Ödeme sorunu"),
    OTHER("Diğer"),
}

@Serializable
data class SelectedOption(
    val groupName: String? = null,
    val optionName: String? = null,
    val priceDelta: Double? = null,
)

@Serializable
data class OrderItem(
    val id: Long? = null,
    val productId: Long? = null,
    val productName: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Int = 0,
    val note: String? = null,
    val lineTotal: Double = 0.0,
    val selectedOptions: List<SelectedOption> = emptyList(),
)

/** `MenuOrderDtos.OrderResponse`: a table/QR order as waiters, kitchen and owners see it. */
@Serializable
data class Order(
    val id: Long,
    val menuId: Long? = null,
    val tableId: Long? = null,
    val tableName: String? = null,
    val customerName: String? = null,
    val status: OrderStatus = OrderStatus.SUBMITTED,
    val orderSource: String? = null,
    val totalAmount: Double = 0.0,
    val currency: String? = null,
    val note: String? = null,
    val waiterName: String? = null,
    val waiterNote: String? = null,
    val kitchenNote: String? = null,
    val billId: Long? = null,
    val items: List<OrderItem> = emptyList(),
    val submittedAt: String? = null,
    /** Set when the kitchen starts it; empty on an order that went straight onto the bill. */
    val preparedAt: String? = null,
    val readyAt: String? = null,
    val cancelReason: CancelReason? = null,
    val createdAt: String? = null,
) {
    val title: String get() = tableName?.takeIf { it.isNotBlank() } ?: "Masasız sipariş"
    val itemCount: Int get() = items.sumOf { it.quantity }
    val placedAt: String? get() = submittedAt ?: createdAt
}

@Serializable
data class CancelOrderRequest(val reason: CancelReason? = null, val reasonNote: String? = null)

@Serializable
data class CartItemRequest(
    val productId: Long,
    val quantity: Int,
    val note: String? = null,
    val selectedOptionIds: List<Long> = emptyList(),
)

@Serializable
data class WaiterCreateOrderRequest(
    val tableId: Long? = null,
    val menuId: Long? = null,
    val items: List<CartItemRequest>,
    val note: String? = null,
    val coverCount: Int? = null,
    /** `true` = "Mutfağa gönder", `false` = "Adisyona ekle" (straight onto the bill, already served). */
    val sendToKitchen: Boolean? = null,
)

@Serializable
data class NoteRequest(val note: String?)

/** `MenuDtos.MenuProductOptionResponse`. */
@Serializable
data class ProductOption(
    val optionId: Long,
    val name: String = "",
    val priceDelta: Double = 0.0,
    val available: Boolean = true,
    val sortOrder: Int = 0,
)

/** `MenuDtos.MenuProductOptionGroupResponse`; qr-service rejects an order outside [minSelect]..[maxSelect]. */
@Serializable
data class ProductOptionGroup(
    val groupId: Long,
    val name: String = "",
    val minSelect: Int = 0,
    val maxSelect: Int = 1,
    val sortOrder: Int = 0,
    val options: List<ProductOption> = emptyList(),
) {
    val required: Boolean get() = minSelect > 0
    val single: Boolean get() = maxSelect <= 1
}

/** `MenuWaiterDtos.CatalogProduct`. */
@Serializable
data class CatalogProduct(
    val productId: Long,
    val name: String = "",
    val description: String? = null,
    val price: Double = 0.0,
    val currency: String? = null,
    val imageUrl: String? = null,
    val available: Boolean = true,
    val mainCategoryName: String? = null,
    val subCategoryName: String? = null,
    val optionGroups: List<ProductOptionGroup> = emptyList(),
    /** Category is marked "Mutfağa gitmez": goes straight onto the bill instead of the kitchen ticket. */
    val skipKitchen: Boolean = false,
) {
    /** A plain tap can't add this product: the waiter has to pick at least one option first. */
    val needsChoice: Boolean get() = optionGroups.any { it.required }
}

@Serializable
data class CatalogResponse(val products: List<CatalogProduct> = emptyList())

/** Mirrors qr-service `TableBillStatus`. */
@Serializable
enum class TableBillStatus { EMPTY, OPEN, CLOSED, CANCELLED }

/** `MenuWaiterDtos.TableOrderSummary`: one table tile on the floor plan. */
@Serializable
data class TableSummary(
    val tableId: Long,
    val menuId: Long? = null,
    val menuName: String? = null,
    val tableName: String? = null,
    val tableNumber: Int? = null,
    val areaName: String? = null,
    val active: Boolean = true,
    val pendingOrderCount: Int = 0,
    val kitchenOrderCount: Int = 0,
    val readyOrderCount: Int = 0,
    val billStatus: TableBillStatus? = null,
    val openBillId: Long? = null,
    val openBillTotal: Double? = null,
    val openBillItemCount: Int = 0,
) {
    val title: String get() = tableName?.takeIf { it.isNotBlank() } ?: "Masa ${tableNumber ?: tableId}"
    val isOccupied: Boolean get() = billStatus == TableBillStatus.OPEN || pendingOrderCount + kitchenOrderCount + readyOrderCount > 0
}

/** Mirrors qr-service `TableBillPaymentMethod`. */
@Serializable
enum class PaymentMethod(val label: String) { CASH("Nakit"), CARD("Kart") }

@Serializable
data class BillItem(
    val id: Long,
    val productId: Long? = null,
    val productName: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Int = 0,
    val paidQuantity: Int = 0,
    val unpaidQuantity: Int = 0,
    val lineTotal: Double = 0.0,
    val note: String? = null,
    /** The order this line came from; used to tell whether it has been through the kitchen. */
    val sourceOrderId: Long? = null,
)

@Serializable
data class BillPayment(
    val id: Long,
    val amount: Double = 0.0,
    val paymentMethod: PaymentMethod? = null,
    val paidAt: String? = null,
    val tip: Boolean = false,
    /** Set on "kişi başı" payments: which share of how many people. */
    val splitShareNumber: Int? = null,
    val splitPersonCount: Int? = null,
    /** qr-service's label for the payment: "Ayran x2", "Pay 1/3" or "Tutar ödemesi". */
    val itemSummary: String? = null,
)

/** `TableBillDtos.BillResponse`. */
@Serializable
data class Bill(
    val id: Long,
    val tableId: Long? = null,
    val tableName: String? = null,
    val status: TableBillStatus = TableBillStatus.OPEN,
    val totalAmount: Double = 0.0,
    val paidTotal: Double = 0.0,
    val remainingTotal: Double = 0.0,
    val currency: String? = null,
    val itemCount: Int = 0,
    val items: List<BillItem> = emptyList(),
    val payments: List<BillPayment> = emptyList(),
    val openedAt: String? = null,
    val closedAt: String? = null,
    val paymentMethod: PaymentMethod? = null,
    /** Person count of a split in progress; fixed once the first share is paid. */
    val splitPersonCount: Int? = null,
)

@Serializable
data class CloseBillRequest(val paymentMethod: PaymentMethod, val tipReceived: Boolean? = null, val tipAmount: Double? = null)

@Serializable
data class PayItemLine(val itemId: Long, val quantityToPay: Int)

/** "Ürün ürün" partial payment; closes the bill when nothing is left unpaid. */
@Serializable
data class PayItemsRequest(
    val paymentMethod: PaymentMethod,
    val items: List<PayItemLine>,
    val tipReceived: Boolean? = null,
    val tipAmount: Double? = null,
)

/** A tip handed over after the bill closed; qr-service records it as a tip payment of today. */
@Serializable
data class AddTipRequest(val amount: Double, val paymentMethod: PaymentMethod)

/** New quantity of one bill line; 0 deletes it. qr-service refuses to go below what has been paid. */
@Serializable
data class UpdateBillItemQuantityRequest(val quantity: Int)

/** "Kısmi öde · Tutar": a typed amount off the bill; the payment that empties it closes the bill. */
@Serializable
data class PayAmountRequest(
    val amount: Double,
    val paymentMethod: PaymentMethod,
    val tipReceived: Boolean? = null,
    val tipAmount: Double? = null,
)

/** Corrects the method of payments taken today. */
@Serializable
data class ChangePaymentMethodRequest(val paymentIds: List<Long>, val paymentMethod: PaymentMethod)

/** "Kişi başı" payment of one equal share; the last share closes the bill. */
@Serializable
data class PayShareRequest(
    val personCount: Int,
    val shareNumber: Int,
    val paymentMethod: PaymentMethod,
    val tipReceived: Boolean? = null,
    val tipAmount: Double? = null,
)

@Serializable
data class SplitShare(val shareNumber: Int, val amount: Double = 0.0, val paid: Boolean = false)

@Serializable
data class SplitPreview(val personCount: Int = 2, val remainingTotal: Double = 0.0, val shares: List<SplitShare> = emptyList())

/** Mirrors qr-service `StoreOrderStatus` for delivery orders. */
@Serializable
enum class StoreOrderStatus {
    PENDING, CONFIRMED, PREPARING, READY, ON_THE_WAY, DELIVERED, REJECTED, CANCELLED,
    KITCHEN_PREPARING, KITCHEN_PREPARED, KITCHEN_DELIVERED_TO_WAITER, KITCHEN_DELIVERED_TO_COURIER,
    UNKNOWN;

    val label: String
        get() = when (this) {
            PENDING -> "Bekliyor"
            CONFIRMED -> "Onaylandı"
            PREPARING, KITCHEN_PREPARING -> "Hazırlanıyor"
            READY, KITCHEN_PREPARED -> "Hazır"
            KITCHEN_DELIVERED_TO_WAITER, KITCHEN_DELIVERED_TO_COURIER -> "Kuryeye verildi"
            ON_THE_WAY -> "Yolda"
            DELIVERED -> "Teslim edildi"
            REJECTED -> "Reddedildi"
            CANCELLED -> "İptal"
            UNKNOWN -> "—"
        }
}

@Serializable
data class DeliveryAddress(
    val addressText: String? = null,
    val city: String? = null,
    val district: String? = null,
    val buildingNo: String? = null,
    val floorNo: String? = null,
    val doorNo: String? = null,
    val directions: String? = null,
) {
    val oneLine: String
        get() = listOfNotNull(
            addressText,
            buildingNo?.let { "No: $it" },
            floorNo?.let { "Kat: $it" },
            doorNo?.let { "Daire: $it" },
            listOfNotNull(district, city).joinToString(" / ").takeIf { it.isNotBlank() },
        ).joinToString(", ")
}

@Serializable
data class StoreOrderItem(
    val productName: String = "",
    val quantity: Int = 0,
    val note: String? = null,
    val lineTotal: Double = 0.0,
)

/** `StoreOrderDtos.OrderDetail`: a delivery order in the courier queue. */
@Serializable
data class DeliveryOrder(
    val id: Long,
    val orderNo: String? = null,
    val status: StoreOrderStatus = StoreOrderStatus.UNKNOWN,
    val paymentMethod: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val address: DeliveryAddress? = null,
    val note: String? = null,
    val totalAmount: Double = 0.0,
    val currency: String? = null,
    val items: List<StoreOrderItem> = emptyList(),
    val createdAt: String? = null,
    val readyAt: String? = null,
    val dispatchedAt: String? = null,
)
