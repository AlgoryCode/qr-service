package com.algoryqr.business.feature.staff

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.algoryqr.business.data.Branch
import com.algoryqr.business.data.BusinessRepository
import com.algoryqr.business.data.CreateStaffRequest
import com.algoryqr.business.data.StaffMember
import com.algoryqr.business.data.StaffRole
import com.algoryqr.business.data.UpdateStaffRequest
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.network.userMessage
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.AppTextField
import com.algoryqr.shared.ui.ChoiceChip
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.ErrorText
import com.algoryqr.shared.ui.IconListRow
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SectionLabel
import com.algoryqr.shared.ui.SegmentedSwitch
import com.algoryqr.shared.ui.Tone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StaffViewModel(private val repository: BusinessRepository) : LoadViewModel<List<StaffMember>>() {

    private val _role = MutableStateFlow<StaffRole?>(null)
    val role: StateFlow<StaffRole?> = _role.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): List<StaffMember> =
        repository.staff().sortedWith(compareByDescending<StaffMember> { it.active }.thenBy { it.displayName.lowercase() })

    fun selectRole(role: StaffRole?) {
        _role.value = role
    }

    fun setActive(member: StaffMember, active: Boolean) =
        runAction { repository.updateStaff(member.id, UpdateStaffRequest(active = active)) }
}

@Composable
private fun StaffRole?.tone(): Tone = when (this) {
    StaffRole.KITCHEN -> AppColors.Amber
    StaffRole.COURIER -> AppColors.Blue
    else -> AppColors.Red
}

private val StaffRole?.icon get() = when (this) {
    StaffRole.KITCHEN -> AppIcons.Kitchen
    StaffRole.COURIER -> AppIcons.Courier
    else -> AppIcons.Profile
}

@Composable
fun StaffScreen(viewModel: StaffViewModel, branches: List<Branch>, onBack: () -> Unit, onAdd: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()
    // Coming back from the form should show the new person straight away.
    LaunchedEffect(Unit) { viewModel.refresh() }

    AppScreen(
        title = "Personel",
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) { Icon(AppIcons.Add, contentDescription = "Personel ekle", tint = AppColors.AccentText) }
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            ChoiceChip("Tümü", selected = role == null, onClick = { viewModel.selectRole(null) })
            StaffRole.entries.forEach { option ->
                ChoiceChip(option.label, selected = role == option, onClick = { viewModel.selectRole(option) })
            }
        }
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) { staff ->
            val visible = staff.filter { role == null || (it.staffRole ?: StaffRole.WAITER) == role }
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Personel yok",
                            description = "Sağ üstteki + ile garson, mutfak ya da kurye hesabı oluşturun.",
                            icon = AppIcons.Staff,
                        )
                    }
                }
                items(visible, key = { it.id }) { member ->
                    val branchName = branches.firstOrNull { it.id == member.branchId }?.name ?: "Şube atanmadı"
                    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                        IconListRow(
                            icon = member.staffRole.icon,
                            tone = if (member.active) member.staffRole.tone() else AppColors.Slate,
                            title = member.displayName.ifBlank { member.username },
                            subtitle = "${(member.staffRole ?: StaffRole.WAITER).label} · @${member.username} · $branchName",
                            onClick = null,
                            trailing = {
                                Switch(
                                    checked = member.active,
                                    onCheckedChange = { viewModel.setActive(member, it) },
                                    enabled = !state.busy,
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = AppColors.Accent,
                                        uncheckedTrackColor = AppColors.Subtle,
                                        uncheckedBorderColor = AppColors.Line,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

data class StaffFormState(
    val displayName: String = "",
    val username: String = "",
    val password: String = "",
    val role: StaffRole = StaffRole.WAITER,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = displayName.isNotBlank() && username.trim().length >= 3 && password.length >= 6
}

class StaffFormViewModel(private val repository: BusinessRepository, private val branchId: Long) : ViewModel() {
    private val _state = MutableStateFlow(StaffFormState())
    val state: StateFlow<StaffFormState> = _state.asStateFlow()

    fun update(transform: StaffFormState.() -> StaffFormState) = _state.update { it.transform().copy(error = null) }

    fun submit() {
        val form = _state.value
        if (!form.canSubmit || form.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            safeCall {
                val created = repository.createStaff(
                    CreateStaffRequest(form.username.trim(), form.password, form.displayName.trim(), form.role),
                )
                // New staff start unassigned; tie them to the branch the owner is looking at so they can sign in to it.
                if (created.branchId == null) repository.assignBranch(created.id, branchId)
            }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { error -> _state.update { it.copy(saving = false, error = error.userMessage()) } }
        }
    }
}

@Composable
fun StaffFormScreen(viewModel: StaffFormViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    AppScreen(title = "Yeni personel", onBack = onBack) {
        Column(
            verticalArrangement = ItemSpacing,
            modifier = Modifier.verticalScroll(rememberScrollState()).imePadding().padding(ScreenPadding),
        ) {
            SectionLabel("Görev")
            SegmentedSwitch(
                options = StaffRole.entries.map { it to it.label },
                selected = state.role,
                onSelect = { role -> viewModel.update { copy(role = role) } },
            )
            SectionLabel("Hesap bilgileri")
            AppTextField(state.displayName, { value -> viewModel.update { copy(displayName = value) } }, "Ad soyad")
            AppTextField(
                state.username,
                { value -> viewModel.update { copy(username = value) } },
                "Kullanıcı adı (en az 3 karakter)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            AppTextField(
                state.password,
                { value -> viewModel.update { copy(password = value) } },
                "Şifre (en az 6 karakter)",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            ErrorText(state.error)
            PrimaryButton("Personeli oluştur", onClick = viewModel::submit, enabled = state.canSubmit, loading = state.saving)
        }
    }
}
