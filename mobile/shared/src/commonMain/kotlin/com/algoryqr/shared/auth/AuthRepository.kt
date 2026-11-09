package com.algoryqr.shared.auth

import com.algoryqr.shared.network.ApiException
import com.algoryqr.shared.network.RefreshTokenRequest
import com.algoryqr.shared.network.TokenPair
import com.algoryqr.shared.network.jsonBody
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.session.AccessProfile
import com.algoryqr.shared.session.Session
import com.algoryqr.shared.session.SessionEnd
import kotlinx.coroutines.flow.StateFlow
import com.algoryqr.shared.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

@Serializable
private data class MerchantLoginRequest(val identifier: String, val password: String)

@Serializable
private data class DemoLoginRequest(val username: String, val password: String)

@Serializable
private data class StaffLoginRequest(val merchantId: Long, val username: String, val password: String)

@Serializable
private data class RevokeOthersResponse(val revoked: Int = 0)

@Serializable
private data class AccessResponse(val role: String, val profile: AccessProfile? = null)

/** Roles each app accepts; a token for another role is revoked straight away instead of being stored. */
object Roles {
    const val MERCHANT = "MERCHANT"
    const val WAITER = "WAITER"
    const val KITCHEN = "KITCHEN"
    const val COURIER = "COURIER"
    val STAFF = setOf(WAITER, KITCHEN, COURIER)
}

private val DEMO_FALLBACK_STATUSES = setOf(400, 401, 404)

class AuthRepository(
    private val client: HttpClient,
    private val sessionManager: SessionManager,
) {
    /** Owner sign-in with e-mail or username (`POST /api/v1/auth/login`). */
    suspend fun loginMerchant(identifier: String, password: String) {
        val trimmed = identifier.trim()
        val tokens = try {
            client.post("api/v1/auth/login") { jsonBody(MerchantLoginRequest(trimmed, password)) }.body<TokenPair>()
        } catch (error: ApiException) {
            // Demo accounts (username only, no e-mail) sign in through their own endpoint.
            if ('@' in trimmed || error.status !in DEMO_FALLBACK_STATUSES) throw error
            client.post("api/v1/auth/demo/login") { jsonBody(DemoLoginRequest(trimmed, password)) }.body<TokenPair>()
        }
        startSession(tokens, allowedRoles = setOf(Roles.MERCHANT), wrongRoleMessage = "Bu hesap bir işletme hesabı değil. Personel girişi için AlgoryQR Personel uygulamasını kullanın.")
    }

    /** Staff sign-in scoped to one business (`POST /api/v1/auth/staff/login`). */
    suspend fun loginStaff(merchantId: Long, username: String, password: String) {
        val tokens = client.post("api/v1/auth/staff/login") {
            jsonBody(StaffLoginRequest(merchantId, username.trim(), password))
        }.body<TokenPair>()
        startSession(tokens, allowedRoles = Roles.STAFF, wrongRoleMessage = "Bu hesap bir personel hesabı değil.")
    }

    /** Why the previous session ended on its own, for the login screen; cleared by the next sign-in. */
    val sessionEnded: StateFlow<SessionEnd?> get() = sessionManager.ended

    /** Revokes the refresh token server-side; the local session ends even when that call fails. */
    suspend fun logout() {
        sessionManager.refreshToken?.let { refreshToken ->
            safeCall { client.post("api/v1/auth/tokens/revoke") { jsonBody(RefreshTokenRequest(refreshToken)) } }
        }
        sessionManager.clear()
    }

    /** Closes every other session of this account (lost phone, shared tablet); returns how many were closed. */
    suspend fun signOutOtherDevices(): Int =
        client.post("api/v1/auth/sessions/revoke-others").body<RevokeOthersResponse>().revoked

    private suspend fun startSession(tokens: TokenPair, allowedRoles: Set<String>, wrongRoleMessage: String) {
        val access = client.get("api/v1/auth/access") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }.body<AccessResponse>()
        val profile = (access.profile ?: AccessProfile(role = access.role)).copy(role = access.role)
        if (profile.role !in allowedRoles) {
            safeCall { client.post("api/v1/auth/tokens/revoke") { jsonBody(RefreshTokenRequest(tokens.refreshToken)) } }
            throw ApiException(status = 403, code = "WRONG_APP", serverMessage = wrongRoleMessage)
        }
        sessionManager.start(Session(tokens.accessToken, tokens.refreshToken, profile))
    }
}
