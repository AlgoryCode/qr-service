package com.algoryqr.shared.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Who is signed in, as `GET /api/v1/auth/access` describes it; ids depend on the role. */
@Serializable
data class AccessProfile(
    val role: String,
    val merchantId: Long? = null,
    val staffId: Long? = null,
    val branchId: Long? = null,
    val displayName: String? = null,
    val username: String? = null,
    val email: String? = null,
)

@Serializable
data class Session(val accessToken: String, val refreshToken: String, val profile: AccessProfile)

/** Persists the serialised session; platform implementations keep it encrypted at rest. */
interface SessionStorage {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

/** Why a session ended without the user signing out; the login screen tells them. Codes come from qr-service. */
enum class SessionEnd(val message: String) {
    EXPIRED("Oturumunuzun süresi doldu. Lütfen tekrar giriş yapın."),
    REVOKED("Bu cihazdaki oturum başka bir cihazdan kapatıldı."),
    REUSED("Güvenliğiniz için oturumunuz kapatıldı. Lütfen tekrar giriş yapın."),
    ;

    companion object {
        fun fromCode(code: String?): SessionEnd = when (code) {
            "SESSION_REVOKED" -> REVOKED
            "SESSION_REUSED" -> REUSED
            else -> EXPIRED
        }
    }
}

class SessionManager(
    private val storage: SessionStorage,
    private val json: Json,
) {
    private val _session = MutableStateFlow(restore())
    val session: StateFlow<Session?> = _session.asStateFlow()

    val accessToken: String? get() = _session.value?.accessToken
    val refreshToken: String? get() = _session.value?.refreshToken

    fun start(session: Session) {
        storage.write(json.encodeToString(session))
        _session.value = session
        _ended.value = null
    }

    /** Swaps in a rotated token pair and keeps the profile. */
    fun updateTokens(accessToken: String, refreshToken: String) {
        _session.value?.let { start(it.copy(accessToken = accessToken, refreshToken = refreshToken)) }
    }

    /** Why the last session ended on its own; `null` after a sign-out the user chose, or once shown. */
    private val _ended = MutableStateFlow<SessionEnd?>(null)
    val ended: StateFlow<SessionEnd?> = _ended.asStateFlow()

    /** The user signed out. */
    fun clear() {
        storage.clear()
        _session.value = null
    }

    /** qr-service no longer accepts the session; the login screen says why. */
    fun expire(reason: SessionEnd) {
        if (_session.value == null) return
        _ended.value = reason
        clear()
    }

    fun dismissEnded() {
        _ended.value = null
    }

    private fun restore(): Session? = storage.read()?.let { stored ->
        runCatching { json.decodeFromString<Session>(stored) }.getOrNull()
    }
}
