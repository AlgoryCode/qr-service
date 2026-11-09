package com.algoryqr.business.data

import com.algoryqr.shared.network.ApiException
import com.algoryqr.shared.network.jsonBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import kotlinx.serialization.Serializable

/**
 * Delivery platforms the business can connect. Uber bought Trendyol Go, which now runs as "Uber Eats Trendyol Go"
 * on the same API (api.tgoapis.com); qr-service's "ubereats" integration is that one platform, not a separate
 * Uber Eats.
 */
enum class Platform(val title: String, val apiPath: String) {
    UBER_EATS("Uber Eats Trendyol Go", "api/v1/integrations/ubereats"),
    YEMEKSEPETI("Yemeksepeti", "api/v1/integrations/yemek-sepeti"),
}

/** Mirrors qr-service `UberEatsConnectionStatus` / `YemekSepetiConnectionStatus`. */
@Serializable
enum class ConnectionStatus { DISCONNECTED, PENDING_RESTAURANT, CONNECTED, ERROR }

/** Both platforms' `ConnectionResponse`; Uber Eats fills the seller/restaurant fields, Yemeksepeti the chain/vendor ones. */
@Serializable
data class PlatformConnection(
    val id: Long? = null,
    val sellerId: String? = null,
    val apiKeyMasked: String? = null,
    val restaurantId: String? = null,
    val restaurantName: String? = null,
    val chainId: String? = null,
    val clientIdMasked: String? = null,
    val webhookSecretMasked: String? = null,
    val vendorId: String? = null,
    val vendorName: String? = null,
    val branchId: Long? = null,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val lastError: String? = null,
    val lastSyncedAt: String? = null,
) {
    val storeName: String? get() = (restaurantName ?: vendorName)?.takeIf { it.isNotBlank() } ?: (restaurantId ?: vendorId)
    val isLive: Boolean get() = status == ConnectionStatus.CONNECTED
}

@Serializable
data class PlatformRestaurant(val id: String, val name: String? = null, val address: String? = null)

@Serializable
data class PlatformOrderItem(
    val productId: String? = null,
    val productName: String = "",
    val quantity: Int = 1,
    val unitPrice: Double? = null,
    val options: String? = null,
    val detail: String? = null,
)

/** An order as the platform sent it; [packageStatus] is the platform's own status text. */
@Serializable
data class PlatformOrder(
    val id: Long,
    val externalOrderId: String? = null,
    val orderNumber: String? = null,
    val deliveryType: String? = null,
    val paymentMethod: String? = null,
    val packageStatus: String? = null,
    val totalAmount: Double? = null,
    val currency: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val deliveryAddress: String? = null,
    val note: String? = null,
    val packageCreatedAt: String? = null,
    val updatedAt: String? = null,
    val items: List<PlatformOrderItem> = emptyList(),
)

@Serializable
data class PlatformOrderPage(val content: List<PlatformOrder> = emptyList())

@Serializable
data class SyncResult(val upserted: Int = 0)

@Serializable
private data class UberEatsConnectRequest(
    val sellerId: String,
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val restaurantId: String? = null,
)

@Serializable
private data class YemeksepetiConnectRequest(
    val chainId: String,
    val vendorId: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val webhookSecret: String? = null,
)

@Serializable
private data class ConnectionBranchRequest(val branchId: Long?)

/** What the owner typed into a platform's connect form; blank secrets keep the stored ones. */
data class ConnectForm(
    val accountId: String = "",
    val storeId: String = "",
    val key: String = "",
    val secret: String = "",
    val webhookSecret: String = "",
)

/** Order actions qr-service forwards to the platform. */
enum class PlatformOrderAction(val path: String) { ACCEPT("accept"), REJECT("reject"), CANCEL("cancel"), READY("ready") }

class IntegrationRepository(private val client: HttpClient) {

    /** The merchant's connection, or null when the platform was never connected. */
    suspend fun connection(platform: Platform): PlatformConnection? {
        val path = platform.apiPath
        return try {
            client.get("$path/connections/me").body()
        } catch (error: ApiException) {
            if (error.status == 404) null else throw error
        }
    }

    suspend fun connect(platform: Platform, form: ConnectForm): PlatformConnection {
        val path = platform.apiPath
        fun String.orNull() = trim().takeIf { it.isNotEmpty() }
        return client.put("$path/connections") {
            when (platform) {
                Platform.YEMEKSEPETI -> jsonBody(
                    YemeksepetiConnectRequest(
                        chainId = form.accountId.trim(),
                        vendorId = form.storeId.orNull(),
                        clientId = form.key.orNull(),
                        clientSecret = form.secret.orNull(),
                        webhookSecret = form.webhookSecret.orNull(),
                    ),
                )
                else -> jsonBody(
                    UberEatsConnectRequest(
                        sellerId = form.accountId.trim(),
                        apiKey = form.key.orNull(),
                        apiSecret = form.secret.orNull(),
                        restaurantId = form.storeId.orNull(),
                    ),
                )
            }
        }.body()
    }

    suspend fun restaurants(platform: Platform): List<PlatformRestaurant> =
        client.get("${platform.apiPath}/restaurants").body()

    /** null shows the platform's orders in every branch's kitchen. */
    suspend fun assignBranch(platform: Platform, branchId: Long?): PlatformConnection =
        client.put("${platform.apiPath}/connections/me/branch") { jsonBody(ConnectionBranchRequest(branchId)) }.body()

    suspend fun disconnect(platform: Platform): PlatformConnection =
        client.delete("${platform.apiPath}/connections/me").body()

    suspend fun orders(platform: Platform, size: Int = 40): List<PlatformOrder> =
        client.get("${platform.apiPath}/orders") { parameter("size", size) }.body<PlatformOrderPage>().content

    /** Pulls the last days' orders from the platform now instead of waiting for the webhook or the background sync. */
    suspend fun sync(platform: Platform): SyncResult = client.post("${platform.apiPath}/orders/sync").body()

    suspend fun orderAction(platform: Platform, orderId: Long, action: PlatformOrderAction): PlatformOrder =
        client.post("${platform.apiPath}/orders/$orderId/${action.path}").body()
}
