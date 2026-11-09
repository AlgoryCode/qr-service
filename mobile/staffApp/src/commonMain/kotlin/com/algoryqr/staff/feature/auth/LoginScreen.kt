package com.algoryqr.staff.feature.auth

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import com.algoryqr.staff.LocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StaffLoginState(
    val merchantId: String = "",
    val username: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = merchantId.toLongOrNull() != null && username.isNotBlank() && password.isNotBlank()
}

/** Staff rarely work for more than one business, so the number is filled in for them next time. */
private const val LAST_MERCHANT_ID_KEY = "last_merchant_id"

class StaffLoginViewModel(
    private val authRepository: AuthRepository,
    private val store: LocalStore,
) : ViewModel() {
    private val _state = MutableStateFlow(StaffLoginState(merchantId = store.get(LAST_MERCHANT_ID_KEY).orEmpty()))
    val state: StateFlow<StaffLoginState> = _state.asStateFlow()
    val sessionEnded: StateFlow<SessionEnd?> = authRepository.sessionEnded

    fun onMerchantIdChange(value: String) = _state.update { it.copy(merchantId = value.filter(Char::isDigit), error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        val merchantId = current.merchantId.toLongOrNull()
        if (!current.canSubmit || current.submitting || merchantId == null) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            safeCall { authRepository.loginStaff(merchantId, current.username, current.password) }
                .onSuccess {
                    store.put(LAST_MERCHANT_ID_KEY, current.merchantId)
                    // This view model outlives the session; without this the screen is stuck loading after sign-out.
                    _state.update { it.copy(submitting = false, password = "") }
                }
                .onFailure { error -> _state.update { it.copy(submitting = false, error = error.userMessage()) } }
        }
    }
}

@Composable
fun StaffLoginScreen(viewModel: StaffLoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionEnded by viewModel.sessionEnded.collectAsStateWithLifecycle()

    AuthScaffold(
        brand = "ALGORYQR PERSONEL",
        brandIcon = AppIcons.Menu,
        title = "Vardiyaya giriş",
        subtitle = "İşletme numaranızı ve size verilen kullanıcı adıyla şifreyi girin. Numarayı işletme sahibi Hesap ekranında görür.",
        notice = sessionEnded?.message,
    ) {
        AppTextField(
            value = state.merchantId,
            onValueChange = viewModel::onMerchantIdChange,
            label = "İşletme No",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
        AppTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = "Kullanıcı adı",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
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
