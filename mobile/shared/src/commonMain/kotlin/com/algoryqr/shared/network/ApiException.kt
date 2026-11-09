package com.algoryqr.shared.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** @param serverMessage Turkish message from qr-service's error body, when it sent one. */
class ApiException(
    val status: Int,
    val code: String?,
    val serverMessage: String?,
) : Exception(serverMessage ?: code ?: "HTTP $status")

/** qr-service answers errors as `{ "message": ..., "code"?: ... }`. */
@Serializable
data class ApiErrorDto(
    val code: String? = null,
    val message: String? = null,
)

fun Throwable.userMessage(): String = when (this) {
    is ApiException -> serverMessage ?: when (status) {
        401 -> "Oturumunuz sona erdi. Lütfen tekrar giriş yapın."
        403 -> "Bu işlem için yetkiniz yok."
        404 -> "Kayıt bulunamadı."
        else -> "Bir şeyler ters gitti. Lütfen tekrar deneyin."
    }
    else -> "Sunucuya ulaşılamadı. İnternet bağlantınızı kontrol edin."
}

/** Runs a remote call and captures failures, while still letting coroutine cancellation propagate. */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    Result.failure(error)
}
