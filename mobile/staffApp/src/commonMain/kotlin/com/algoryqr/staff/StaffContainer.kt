package com.algoryqr.staff

import com.algoryqr.shared.auth.AuthRepository
import com.algoryqr.shared.network.ApiJson
import com.algoryqr.shared.network.createHttpClient
import com.algoryqr.shared.session.SessionManager
import com.algoryqr.shared.session.SessionStorage
import com.algoryqr.staff.data.CartDrafts
import com.algoryqr.staff.data.StaffRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Small unencrypted key-value store for conveniences (last business number, cart drafts); never for secrets. */
interface LocalStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/** Application-wide object graph, created once per process by the platform entry point. */
class StaffContainer(sessionStorage: SessionStorage, val localStore: LocalStore, apiBaseUrl: String) {
    val sessionManager = SessionManager(sessionStorage, ApiJson)
    private val httpClient = createHttpClient(apiBaseUrl, sessionManager)

    val authRepository = AuthRepository(httpClient, sessionManager)
    val repository = StaffRepository(httpClient)
    val cartDrafts = CartDrafts(localStore)

    /** Work that must finish even if its screen closes, such as an order whose undo window is running. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
