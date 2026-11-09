package com.algoryqr.shared.network

import com.algoryqr.shared.session.SessionEnd
import com.algoryqr.shared.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val REFRESH_PATH = "api/v1/auth/tokens/refresh"

/** Endpoints that work without a session; everything else gets the access token. */
private val PUBLIC_AUTH_ENDPOINTS = setOf(
    "/api/v1/auth/login",
    "/api/v1/auth/staff/login",
    "/api/v1/auth/demo/login",
    "/$REFRESH_PATH",
    "/api/v1/auth/tokens/revoke",
)

@Serializable
data class TokenPair(val accessToken: String, val refreshToken: String)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

fun createHttpClient(baseUrl: String, sessionManager: SessionManager, json: Json = ApiJson): HttpClient {
    val client = HttpClient {
        expectSuccess = false

        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }

        defaultRequest {
            url(baseUrl.trimEnd('/') + "/")
            header(HttpHeaders.AcceptLanguage, "tr")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }

        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.isSuccess()) return@validateResponse
                // A 401 here is never a sign-out on its own: the session ends only when qr-service refuses the
                // refresh token (see installTokenRefresh), so one endpoint answering 401 cannot log anyone out.
                throw response.toApiException()
            }
        }
    }
    client.installTokenRefresh(sessionManager)
    return client
}

/**
 * Attaches the access token and, when it has expired, swaps the refresh token for a new pair once and retries.
 * A mutex keeps parallel requests from refreshing the same token twice: qr-service rotates refresh tokens, so
 * a second refresh with the old one would fail and sign the user out.
 */
private fun HttpClient.installTokenRefresh(sessionManager: SessionManager) {
    val refreshMutex = Mutex()

    plugin(HttpSend).intercept { request ->
        // Relative request paths have no leading slash yet ("api/v1/..."); without it the refresh call itself
        // would wait on the mutex its caller holds and every later request would hang behind it.
        val isAuthCall = "/" + request.url.encodedPath.trimStart('/') in PUBLIC_AUTH_ENDPOINTS
        val sentToken = sessionManager.accessToken
        if (!isAuthCall && sentToken != null) {
            request.headers[HttpHeaders.Authorization] = "Bearer $sentToken"
        }
        val call = execute(request)
        if (isAuthCall || sentToken == null || call.response.status != HttpStatusCode.Unauthorized) {
            return@intercept call
        }

        val outcome = refreshMutex.withLock {
            sessionManager.accessToken?.takeIf { it != sentToken }?.let(RefreshOutcome::Fresh) ?: refreshTokens(sessionManager)
        }
        when (outcome) {
            is RefreshOutcome.Fresh -> {
                request.headers[HttpHeaders.Authorization] = "Bearer ${outcome.accessToken}"
                execute(request)
            }
            // The server refused the refresh token: the session is really over.
            RefreshOutcome.Rejected -> call
            // Network trouble or a restarting server is not a sign-out; fail this request and keep the session.
            is RefreshOutcome.Unreachable -> throw outcome.cause
        }
    }
}

private sealed interface RefreshOutcome {
    data class Fresh(val accessToken: String) : RefreshOutcome
    data object Rejected : RefreshOutcome
    data class Unreachable(val cause: Throwable) : RefreshOutcome
}

private suspend fun HttpClient.refreshTokens(sessionManager: SessionManager): RefreshOutcome {
    val refreshToken = sessionManager.refreshToken ?: return RefreshOutcome.Rejected
    return safeCall {
        post(REFRESH_PATH) { jsonBody(RefreshTokenRequest(refreshToken)) }.body<TokenPair>()
    }.fold(
        onSuccess = { tokens ->
            sessionManager.updateTokens(tokens.accessToken, tokens.refreshToken)
            RefreshOutcome.Fresh(tokens.accessToken)
        },
        onFailure = { error ->
            val refused = error is ApiException && error.status in 400..499
            if (refused) {
                // The only place a session ends without the user asking: qr-service no longer accepts it.
                sessionManager.expire(SessionEnd.fromCode((error as ApiException).code))
                RefreshOutcome.Rejected
            } else {
                RefreshOutcome.Unreachable(error)
            }
        },
    )
}

private suspend fun HttpResponse.toApiException(): ApiException {
    val error = runCatching { body<ApiErrorDto>() }.getOrNull()
    return ApiException(
        status = status.value,
        code = error?.code,
        serverMessage = error?.message?.takeIf { it.isNotBlank() },
    )
}

inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
