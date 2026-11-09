package com.algoryqr.business.feature.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.algoryqr.business.data.Branch
import com.algoryqr.business.data.CreateFlowRequest
import com.algoryqr.business.data.FlowCatalog
import com.algoryqr.business.data.FlowRequest
import com.algoryqr.business.data.OrderFlow
import com.algoryqr.business.data.OrderFlowRepository
import com.algoryqr.business.data.SaveFlowRequest
import com.algoryqr.shared.model.BranchFlow
import com.algoryqr.shared.model.FlowChannel
import com.algoryqr.shared.model.FlowStep
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.network.userMessage
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.AppTextField
import com.algoryqr.shared.ui.ErrorText
import com.algoryqr.shared.ui.IconListRow
import com.algoryqr.shared.ui.IconTile
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.OutlineButton
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SecondaryButton
import com.algoryqr.shared.ui.SectionLabel
import com.algoryqr.shared.ui.StatusChip
import com.algoryqr.shared.ui.SwitchRow
import com.algoryqr.shared.ui.Tone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FlowOverview(
    val catalog: FlowCatalog,
    val flows: List<OrderFlow>,
    val current: BranchFlow,
    val requests: List<FlowRequest>,
)

class FlowsViewModel(
    private val repository: OrderFlowRepository,
    private val branch: Branch,
) : LoadViewModel<FlowOverview>() {

    /** Short confirmation after an assignment, e.g. "Restoran akışı Merkez şubesine atandı". */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): FlowOverview = coroutineScope {
        val catalog = async { repository.catalog() }
        val flows = async { repository.flows() }
        val current = async { repository.branchFlow(branch.id) }
        val requests = async { repository.requests() }
        FlowOverview(catalog.await(), flows.await(), current.await(), requests.await())
    }

    fun assign(flow: OrderFlow) = runAction(onSuccess = { _notice.value = "\"${flow.name}\" artık ${branch.name} şubesinde geçerli." }) {
        repository.assign(flow.id, branch.id)
    }

    fun delete(flow: OrderFlow) = runAction { repository.delete(flow.id) }

    fun sendRequest(title: String, description: String) = runAction(onSuccess = { _notice.value = "Talebiniz alındı. Ekibimiz inceleyip size dönecek." }) {
        repository.createRequest(CreateFlowRequest(null, title.trim(), description.trim().ifBlank { null }))
    }

    fun dismissNotice() {
        _notice.value = null
    }
}

@Composable
private fun roleTone(step: FlowStep): Tone = when (step) {
    FlowStep.KITCHEN_PREP, FlowStep.PASS_CHECK -> AppColors.Amber
    FlowStep.DELIVERY, FlowStep.PACKING -> AppColors.Blue
    else -> AppColors.Red
}

private fun roleIcon(step: FlowStep) = when (step) {
    FlowStep.KITCHEN_PREP -> AppIcons.Kitchen
    FlowStep.DELIVERY -> AppIcons.Courier
    FlowStep.PACKING -> AppIcons.Delivery
    FlowStep.PASS_CHECK -> AppIcons.Check
    FlowStep.ORDER_APPROVAL -> AppIcons.Approve
    FlowStep.TABLE_SERVICE -> AppIcons.Tables
}

/** "Mutfak hazırlığı → Masaya servis" as chips, one row per channel. */
@Composable
private fun FlowSteps(catalog: FlowCatalog, steps: Map<FlowChannel, List<FlowStep>>) {
    FlowChannel.entries.forEach { channel ->
        val list = steps[channel].orEmpty()
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(channel.label, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp).fillMaxWidth(0.18f))
            if (list.isEmpty()) {
                StatusChip("Kullanılmıyor")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEachIndexed { index, step ->
                        StatusChip(catalog.step(step)?.label ?: step.name, roleTone(step))
                        if (index < list.lastIndex) Text("→", color = AppColors.Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FlowsScreen(
    viewModel: FlowsViewModel,
    branch: Branch,
    onBack: () -> Unit,
    onCreate: (templateCode: String) -> Unit,
    onEdit: (OrderFlow) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var choosingTemplate by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<OrderFlow?>(null) }
    // Coming back from the editor should show the saved flow.
    LaunchedEffect(Unit) { viewModel.refresh() }

    AppScreen(title = "Sipariş akışı", onBack = onBack) {
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        notice?.let { message ->
            AppCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), onClick = viewModel::dismissNotice) {
                Text(message, color = AppColors.Green.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
        LoadContent(state, onRetry = viewModel::refresh) { overview ->
            val catalog = overview.catalog
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                item { SectionLabel("${branch.name} · şu anki akış") }
                item {
                    AppCard {
                        Text(overview.current.flowName ?: "Varsayılan (şube ayarlarından)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        FlowSteps(catalog, overview.current.steps)
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = AppColors.Line)
                        Spacer(Modifier.height(8.dp))
                        RolePreview(overview.current)
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Akışlarım", Modifier.weight(1f))
                        TextButton(onClick = { choosingTemplate = true }) { Text("+ Yeni akış", color = AppColors.AccentText) }
                    }
                }
                if (overview.flows.isEmpty()) {
                    item {
                        AppCard {
                            Text("Henüz akış oluşturmadınız.", style = MaterialTheme.typography.titleMedium)
                            Text("Hazır bir şablonla başlayıp adımları işletmenize göre açıp kapatabilirsiniz.", color = AppColors.Muted)
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton("Şablondan akış oluştur", onClick = { choosingTemplate = true }, icon = AppIcons.Add)
                        }
                    }
                }
                items(overview.flows, key = { it.id }) { flow ->
                    val activeHere = overview.current.flowId == flow.id
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(flow.name, style = MaterialTheme.typography.titleMedium)
                                val template = catalog.templates.firstOrNull { it.code == flow.templateCode }?.label
                                Text(listOfNotNull(template?.let { "Şablon: $it" }, "Sürüm ${flow.version}").joinToString(" · "), color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (activeHere) StatusChip("Bu şubede", AppColors.Green)
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowSteps(catalog, flow.steps)
                        if (flow.branches.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Şubeler: " + flow.branches.joinToString { it.name }, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlineButton("Düzenle", onClick = { onEdit(flow) }, enabled = !state.busy, modifier = Modifier.weight(1f))
                            if (!activeHere) {
                                PrimaryButton("Bu şubeye ata", onClick = { viewModel.assign(flow) }, enabled = !state.busy, modifier = Modifier.weight(1.2f))
                            } else if (flow.branches.isEmpty()) {
                                Spacer(Modifier.weight(1.2f))
                            }
                        }
                        if (flow.branches.isEmpty()) {
                            TextButton(onClick = { deleting = flow }) { Text("Akışı sil", color = AppColors.Danger) }
                        }
                    }
                }
                item { SectionLabel("Sipariş durumları (sabit)") }
                item {
                    AppCard {
                        Text("Akışınız ne olursa olsun siparişler bu durumlardan geçer; akış yalnızca hangi adımın kimde olduğunu belirler.", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            catalog.statuses.forEach { StatusChip(it.label) }
                        }
                    }
                }
                item { SectionLabel("Yeni adım talebi") }
                item {
                    AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                        IconListRow(
                            AppIcons.Note,
                            AppColors.Red,
                            "Ara adım talep et",
                            onClick = { requesting = true },
                            subtitle = "Listede olmayan bir adıma ihtiyacınız varsa bize yazın",
                        )
                        overview.requests.take(5).forEach { request ->
                            IconListRow(
                                AppIcons.Time,
                                AppColors.Slate,
                                request.title,
                                onClick = null,
                                trailing = { StatusChip(request.statusLabel, if (request.status == "DONE") AppColors.Green else null) },
                            )
                        }
                    }
                }
            }

            if (choosingTemplate) {
                TemplateDialog(
                    catalog = catalog,
                    onPick = { code ->
                        choosingTemplate = false
                        onCreate(code)
                    },
                    onDismiss = { choosingTemplate = false },
                )
            }
        }
    }

    if (requesting) {
        RequestDialog(
            onSend = { title, description ->
                viewModel.sendRequest(title, description)
                requesting = false
            },
            onDismiss = { requesting = false },
        )
    }
    deleting?.let { flow ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = AppColors.Surface,
            title = { Text("Akış silinsin mi?") },
            text = { Text("\"${flow.name}\" hiçbir şubede kullanılmıyor.", color = AppColors.Muted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(flow)
                    deleting = null
                }) { Text("Sil", color = AppColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç", color = AppColors.Ink) } },
        )
    }
}

/** What each role will do on this branch, in plain words. */
@Composable
private fun RolePreview(flow: BranchFlow) {
    val waiter = buildList {
        if (flow.tableApproval) add("QR siparişlerini onaylar")
        if (flow.steps[FlowChannel.TABLE].orEmpty().isNotEmpty()) add(if (flow.tableKitchen) "mutfak hazır deyince servis eder" else "siparişi kendisi servis eder")
    }
    val kitchen = if (flow.usesKitchen) "Siparişleri hazırlar, \"Hazır\" der" else "Mutfak ekranı kullanılmaz"
    val courier = if (flow.usesCourier) "Paketleri alır ve teslim eder" else "Kurye kullanılmaz"
    PreviewRow(AppIcons.Profile, AppColors.Red, "Garson", waiter.joinToString(", ").ifBlank { "Masa siparişi alınmaz" })
    PreviewRow(AppIcons.Kitchen, AppColors.Amber, "Mutfak", kitchen)
    PreviewRow(AppIcons.Courier, AppColors.Blue, "Kurye", courier)
}

@Composable
private fun PreviewRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tone: Tone, role: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        IconTile(icon, tone, size = 30.dp)
        Spacer(Modifier.padding(start = 10.dp))
        Column {
            Text(role, style = MaterialTheme.typography.labelLarge)
            Text(text, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TemplateDialog(catalog: FlowCatalog, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text("Şablon seçin", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                catalog.templates.forEach { template ->
                    AppCard(onClick = { onPick(template.code) }) {
                        Text(template.label, style = MaterialTheme.typography.titleMedium)
                        Text(template.description, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = AppColors.Muted) } },
    )
}

@Composable
private fun RequestDialog(onSend: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text("Yeni adım talebi", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Hangi adıma ihtiyacınız var? Örnek: \"Tatlılar ayrı bir tezgâhta hazırlanıyor\".", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                AppTextField(title, { title = it }, "Kısa başlık")
                AppTextField(description, { description = it }, "Açıklama", singleLine = false, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(title, description) }, enabled = title.isNotBlank()) { Text("Gönder", color = AppColors.AccentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = AppColors.Ink) } },
    )
}

data class FlowEditorState(
    val catalog: FlowCatalog? = null,
    val name: String = "",
    val templateCode: String? = null,
    val steps: Map<FlowChannel, List<FlowStep>> = emptyMap(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val assignAfterSave: Boolean = true,
    val error: String? = null,
)

/** Creates a flow from a template or edits an existing one; channels are switched on by their final step. */
class FlowEditorViewModel(
    private val repository: OrderFlowRepository,
    private val branchId: Long,
    private val flowId: Long?,
    private val templateCode: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(FlowEditorState())
    val state: StateFlow<FlowEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            safeCall {
                val catalog = repository.catalog()
                val existing = flowId?.let { id -> repository.flows().firstOrNull { it.id == id } }
                val template = catalog.templates.firstOrNull { it.code == (existing?.templateCode ?: templateCode) }
                _state.update {
                    it.copy(
                        catalog = catalog,
                        name = existing?.name ?: template?.label.orEmpty(),
                        templateCode = existing?.templateCode ?: template?.code,
                        steps = existing?.steps ?: template?.steps.orEmpty(),
                        loading = false,
                        assignAfterSave = existing == null,
                    )
                }
            }.onFailure { error -> _state.update { it.copy(loading = false, error = error.userMessage()) } }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }

    fun setAssignAfterSave(value: Boolean) = _state.update { it.copy(assignAfterSave = value) }

    /** Channels are on when their closing step is there; switching one on starts it with just that step. */
    fun setChannel(channel: FlowChannel, on: Boolean) = _state.update { state ->
        val end = if (channel == FlowChannel.TABLE) FlowStep.TABLE_SERVICE else FlowStep.DELIVERY
        state.copy(steps = state.steps + (channel to if (on) listOf(end) else emptyList()), error = null)
    }

    fun toggleStep(channel: FlowChannel, step: FlowStep, on: Boolean) = _state.update { state ->
        val current = state.steps[channel].orEmpty()
        val next = if (on) (current + step).distinct() else current - step
        // Keep the catalogue order so the preview reads like the real sequence.
        val order = state.catalog?.steps?.map { it.code }.orEmpty()
        state.copy(steps = state.steps + (channel to next.sortedBy { order.indexOf(it) }), error = null)
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            safeCall {
                val request = SaveFlowRequest(current.name.trim(), current.templateCode, current.steps)
                val saved = if (flowId == null) repository.create(request) else repository.update(flowId, request)
                if (current.assignAfterSave) repository.assign(saved.id, branchId)
            }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { error -> _state.update { it.copy(saving = false, error = error.userMessage()) } }
        }
    }
}

@Composable
fun FlowEditorScreen(viewModel: FlowEditorViewModel, isNew: Boolean, branchName: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    val catalog = state.catalog

    AppScreen(title = if (isNew) "Yeni akış" else "Akışı düzenle", onBack = onBack) {
        if (catalog == null) {
            if (state.loading) com.algoryqr.shared.ui.LoadingContent() else ErrorText(state.error, Modifier.padding(ScreenPadding))
            return@AppScreen
        }
        Column(
            verticalArrangement = ItemSpacing,
            modifier = Modifier.verticalScroll(rememberScrollState()).imePadding().padding(ScreenPadding),
        ) {
            AppTextField(state.name, viewModel::onNameChange, "Akış adı")
            FlowChannel.entries.forEach { channel ->
                ChannelEditor(catalog, channel, state.steps[channel].orEmpty(), viewModel)
            }
            SectionLabel("Önizleme")
            AppCard {
                FlowSteps(catalog, state.steps)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = AppColors.Line)
                Spacer(Modifier.height(8.dp))
                RolePreview(BranchFlow(steps = state.steps))
            }
            SwitchRow(
                title = "Kaydedince $branchName şubesine ata",
                checked = state.assignAfterSave,
                onCheckedChange = viewModel::setAssignAfterSave,
                description = "Atanınca personel uygulamaları bir dakika içinde yeni akışa geçer.",
            )
            ErrorText(state.error)
            PrimaryButton("Kaydet", onClick = viewModel::save, enabled = state.name.isNotBlank(), loading = state.saving)
        }
    }
}

@Composable
private fun ChannelEditor(catalog: FlowCatalog, channel: FlowChannel, steps: List<FlowStep>, viewModel: FlowEditorViewModel) {
    val end = if (channel == FlowChannel.TABLE) FlowStep.TABLE_SERVICE else FlowStep.DELIVERY
    val on = end in steps
    AppCard {
        SwitchRow(
            title = if (channel == FlowChannel.TABLE) "Masa siparişleri" else "Paket siparişleri (kurye)",
            checked = on,
            onCheckedChange = { viewModel.setChannel(channel, it) },
            description = if (channel == FlowChannel.TABLE) "Garson ve QR'dan gelen salon siparişleri" else "Online, platform ve telefonla gelen paket siparişleri",
            icon = if (channel == FlowChannel.TABLE) AppIcons.Tables else AppIcons.Courier,
            tone = if (channel == FlowChannel.TABLE) AppColors.Red else AppColors.Blue,
        )
        if (on) {
            HorizontalDivider(color = AppColors.Line)
            catalog.steps.filter { channel in it.channels && it.code != end }.forEach { info ->
                if (info.available) {
                    SwitchRow(
                        title = info.label,
                        checked = info.code in steps,
                        onCheckedChange = { viewModel.toggleStep(channel, info.code, it) },
                        description = "${info.role} · ${info.description}",
                        icon = roleIcon(info.code),
                        tone = roleTone(info.code),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        IconTile(roleIcon(info.code), AppColors.Slate)
                        Spacer(Modifier.padding(start = 14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info.label, style = MaterialTheme.typography.titleMedium, color = AppColors.Muted)
                            Text("${info.role} · ${info.description}", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        StatusChip("Yakında")
                    }
                }
            }
        }
    }
}
