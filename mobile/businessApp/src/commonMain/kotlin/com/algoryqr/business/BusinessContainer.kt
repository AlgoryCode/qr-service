package com.algoryqr.business

import com.algoryqr.business.data.BusinessRepository
import com.algoryqr.business.data.IntegrationRepository
import com.algoryqr.business.data.OrderFlowRepository
import com.algoryqr.shared.auth.AuthRepository
import com.algoryqr.shared.network.ApiJson
import com.algoryqr.shared.network.createHttpClient
import com.algoryqr.shared.session.SessionManager
import com.algoryqr.shared.session.SessionStorage

/** Application-wide object graph, created once per process by the platform entry point. */
class BusinessContainer(sessionStorage: SessionStorage, apiBaseUrl: String) {
    val sessionManager = SessionManager(sessionStorage, ApiJson)
    private val httpClient = createHttpClient(apiBaseUrl, sessionManager)

    val authRepository = AuthRepository(httpClient, sessionManager)
    val repository = BusinessRepository(httpClient)
    val orderFlowRepository = OrderFlowRepository(httpClient)
    val integrationRepository = IntegrationRepository(httpClient)
}
