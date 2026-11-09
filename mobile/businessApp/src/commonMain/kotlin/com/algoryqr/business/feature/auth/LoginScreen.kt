package com.algoryqr.business.feature.auth

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.algoryqr.shared.auth.AuthRepository
import com.algoryqr.shared.session.SessionEnd
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.network.userMessage
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppTextField
import com.algoryqr.shared.ui.AuthScaffold
import com.algoryqr.shared.ui.ErrorText
import com.algoryqr.shared.ui.PrimaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val identifier: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = identifier.isNotBlank() && password.isNotBlank()
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()
    val sessionEnded: StateFlow<SessionEnd?> = authRepository.sessionEnded

    fun onIdentifierChange(value: String) = _state.update { it.copy(identifier = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit || current.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            // The session flow switches the app to the dashboard. This view model outlives the session, so success
            // must still end the spinner, or the login screen comes back after sign-out stuck on it.
            safeCall { authRepository.loginMerchant(current.identifier, current.password) }
                .onSuccess { _state.update { it.copy(submitting = false, password = "") } }
                .onFailure { error -> _state.update { it.copy(submitting = false, error = error.userMessage()) } }
        }
    }
}

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionEnded by viewModel.sessionEnded.collectAsStateWithLifecycle()

    AuthScaffold(
        brand = "ALGORYQR İŞLETME",
        brandIcon = AppIcons.Business,
        title = "İşletmenize giriş yapın",
        subtitle = "Siparişleri, masaları, personeli ve günlük cironuzu tek yerden takip edin.",
        notice = sessionEnded?.message,
    ) {
        AppTextField(
            value = state.identifier,
            onValueChange = viewModel::onIdentifierChange,
            label = "E-posta veya kullanıcı adı",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        AppTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Şifre",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        ErrorText(state.error)
        PrimaryButton(
            text = "Giriş yap",
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            loading = state.submitting,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
