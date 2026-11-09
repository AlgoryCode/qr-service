package com.algoryqr.business.feature.integrations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.business.data.Branch
import com.algoryqr.business.data.ConnectForm
import com.algoryqr.business.data.ConnectionStatus
import com.algoryqr.business.data.IntegrationRepository
import com.algoryqr.business.data.Platform
import com.algoryqr.business.data.PlatformConnection
import com.algoryqr.business.data.PlatformOrder
import com.algoryqr.business.data.PlatformOrderAction
import com.algoryqr.business.data.PlatformRestaurant
import com.algoryqr.shared.format.formatDateTime
import com.algoryqr.shared.format.formatElapsed
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.AppTextField
import com.algoryqr.shared.ui.ChoiceChip
import com.algoryqr.shared.ui.ConfirmDialog
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.KeyValueRow
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.OutlineButton
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SectionLabel
import com.algoryqr.shared.ui.StatusChip
import com.algoryqr.shared.ui.Tone
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** New platform orders must be answered within minutes, so the lists refresh more often than the rest of the app. */
private const val ORDER_POLL_MILLIS = 15_000L

// ---------------------------------------------------------------------------------------------------------------
// Order status

/** Platforms send their own status words; these are the steps the owner acts on. */
enum class OrderStage(val label: String) {
    NEW("Yeni"),
    PREPARING("Hazırlanıyor"),
    READY("Hazır"),
    ON_THE_WAY("Yolda"),
    DELIVERED("Teslim edildi"),
    CANCELLED("İptal"),
    UNKNOWN("Bilinmiyor"),
}

fun stageOf(rawStatus: String?): OrderStage {
    val status = rawStatus?.lowercase()?.filter { it.isLetter() } ?: return OrderStage.UNKNOWN
    return when {
        listOf("cancel", "reject", "unsupplied", "declin", "fail").any { it in status } -> OrderStage.CANCELLED
        status == "delivered" || status == "completed" || status == "picked_up" -> OrderStage.DELIVERED
        listOf("shipped", "pickedup", "ontheway", "dispatch", "delivering", "intransit", "courier").any { it in status } ->
            OrderStage.ON_THE_WAY
        // Uber Eats Trendyol Go: Created → Picking → Invoiced (prepared, waiting for the courier) → Shipped → Delivered.
        listOf("prepared", "ready", "invoiced").any { it in status } -> OrderStage.READY
        listOf("accept", "picking", "preparing", "inprogress", "confirm").any { it in status } -> OrderStage.PREPARING
        listOf("created", "new", "received", "pending", "placed", "waiting").any { it in status } -> OrderStage.NEW
        else -> OrderStage.UNKNOWN
    }
}

internal val OrderStage.isActive get() = this == OrderStage.NEW || this == OrderStage.PREPARING || this == OrderStage.READY

@Composable
private fun OrderStage.tone(): Tone = when (this) {
    OrderStage.NEW -> AppColors.Red
    OrderStage.PREPARING -> AppColors.Amber
    OrderStage.READY -> AppColors.Green
    OrderStage.ON_THE_WAY -> AppColors.Blue
    OrderStage.DELIVERED, OrderStage.UNKNOWN -> AppColors.Slate
    OrderStage.CANCELLED -> AppColors.Rose
}

/** What can be done with an order at its step; the first one is the main action. */
private fun OrderStage.actions(): List<PlatformOrderAction> = when (this) {
    OrderStage.NEW -> listOf(PlatformOrderAction.ACCEPT, PlatformOrderAction.REJECT)
    OrderStage.PREPARING -> listOf(PlatformOrderAction.READY, PlatformOrderAction.CANCEL)
    else -> emptyList()
}

private val PlatformOrderAction.label
    get() = when (this) {
        PlatformOrderAction.ACCEPT -> "Kabul et"
        PlatformOrderAction.REJECT -> "Reddet"
        PlatformOrderAction.READY -> "Hazır"
        PlatformOrderAction.CANCEL -> "İptal et"
    }

/** Rejecting or cancelling is final on the platform and the customer is told, so it asks first. */
private val PlatformOrderAction.needsConfirm get() = this == PlatformOrderAction.REJECT || this == PlatformOrderAction.CANCEL

/** One order of the combined feed with the platform it came from. */
data class FeedOrder(val platform: Platform, val order: PlatformOrder) {
    val stage = stageOf(order.packageStatus)
}

// ---------------------------------------------------------------------------------------------------------------
// Integrations hub

data class IntegrationsOverview(
    val connections: Map<Platform, PlatformConnection?>,
    val orders: List<FeedOrder>,
    /** Platforms whose orders could not be loaded this time; their cards say so instead of the whole page failing. */
    val failed: Set<Platform>,
)

class IntegrationsViewModel(private val repository: IntegrationRepository) : LoadViewModel<IntegrationsOverview>(ORDER_POLL_MILLIS) {
    private val _filter = MutableStateFlow(FeedFilter.ACTIVE)
    val filter: StateFlow<FeedFilter> = _filter.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): IntegrationsOverview = coroutineScope {
        val connections = Platform.entries.map { platform -> async { platform to repository.connection(platform) } }.awaitAll().toMap()
        val failed = mutableSetOf<Platform>()
        val orders = connections.filterValues { it?.isLive == true }.keys.map { platform ->
            async {
                runCatching { repository.orders(platform) }
                    .onFailure { failed += platform }
                    .getOrDefault(emptyList())
                    .map { FeedOrder(platform, it) }
            }
        }.awaitAll().flatten().sortedByDescending { it.order.packageCreatedAt ?: it.order.updatedAt ?: "" }
        IntegrationsOverview(connections, orders, failed)
    }

    fun setFilter(filter: FeedFilter) {
        _filter.value = filter
    }

    fun act(order: FeedOrder, action: PlatformOrderAction) {
        runAction { repository.orderAction(order.platform, order.order.id, action) }
    }
}

enum class FeedFilter(val label: String) { ACTIVE("Aktif"), ALL("Tümü"), DONE("Tamamlanan"), CANCELLED("İptal") }

private fun FeedFilter.matches(stage: OrderStage) = when (this) {
    FeedFilter.ACTIVE -> stage.isActive || stage == OrderStage.UNKNOWN
    FeedFilter.ALL -> true
    FeedFilter.DONE -> stage == OrderStage.ON_THE_WAY || stage == OrderStage.DELIVERED
    FeedFilter.CANCELLED -> stage == OrderStage.CANCELLED
}

@Composable
fun IntegrationsScreen(
    viewModel: IntegrationsViewModel,
    branches: List<Branch>,
    onOpenPlatform: (Platform) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<FeedOrder?>(null) }

    AppScreen(title = "Entegrasyonlar", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) { overview ->
            val connectedCount = overview.connections.values.count { it?.isLive == true }
            val newCount = overview.orders.count { it.stage == OrderStage.NEW }
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                item { SectionLabel("Platformlar") }
                items(Platform.entries) { platform ->
                    PlatformCard(
                        platform,
                        overview.connections[platform],
                        branchName = overview.connections[platform]?.branchId?.let { id -> branches.firstOrNull { it.id == id }?.name },
                        loadFailed = platform in overview.failed,
                        onClick = { onOpenPlatform(platform) },
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        SectionLabel("Gelen siparişler", Modifier.weight(1f))
                        if (newCount > 0) StatusChip("$newCount yeni", AppColors.Red)
                    }
                }
                if (connectedCount == 0) {
                    // qr-service only lists a platform's orders while its connection is healthy.
                    val troubled = overview.connections.values.any {
                        it?.status == ConnectionStatus.ERROR || it?.status == ConnectionStatus.PENDING_RESTAURANT
                    }
                    item {
                        EmptyState(
                            if (troubled) "Bağlantıda sorun var" else "Bağlı platform yok",
                            if (troubled) "Siparişleri görmek için yukarıdaki platform kartına dokunup bağlantıyı tamamlayın veya bilgileri güncelleyin."
                            else "Uber Eats Trendyol Go veya Yemeksepeti hesabınızı bağladığınızda siparişler burada canlı olarak görünür.",
                            icon = AppIcons.Delivery,
                        )
                    }
                } else {
                    item { LiveHint() }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        ) {
                            FeedFilter.entries.forEach { option ->
                                val count = overview.orders.count { option.matches(it.stage) }
                                ChoiceChip("${option.label} ($count)", selected = option == filter, onClick = { viewModel.setFilter(option) })
                            }
                        }
                    }
                    val shown = overview.orders.filter { filter.matches(it.stage) }
                    if (shown.isEmpty()) {
                        item {
                            EmptyState(
                                if (filter == FeedFilter.ACTIVE) "Bekleyen sipariş yok" else "Sipariş yok",
                                "Platformlardan yeni sipariş geldiğinde burada görünecek.",
                                icon = AppIcons.Orders,
                            )
                        }
                    }
                    items(shown, key = { "${it.platform}-${it.order.id}" }) { item ->
                        OrderRow(item, busy = state.busy, onOpen = { opened = item }, onAction = { viewModel.act(item, it) })
                    }
                }
            }
        }
    }

    opened?.let { order ->
        // Show the latest copy after an action or a poll, not the one that was tapped.
        val fresh = state.data?.orders?.firstOrNull { it.platform == order.platform && it.order.id == order.order.id } ?: order
        OrderSheet(fresh, busy = state.busy, onAction = { viewModel.act(fresh, it) }, onDismiss = { opened = null })
    }
}

/** "Canlı" with a dot, so the owner knows the list keeps itself up to date. */
@Composable
private fun LiveHint() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.Green.content))
        Spacer(Modifier.width(8.dp))
        Text("Canlı · liste 15 saniyede bir yenilenir", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Letter mark in the platform's colour; brand logos are not bundled with the app. */
@Composable
private fun PlatformMark(platform: Platform, size: Int = 44) {
    val (letters, color) = when (platform) {
        Platform.UBER_EATS -> "UE" to Color(0xFF06C167)
        Platform.YEMEKSEPETI -> "YS" to Color(0xFFEA004B)
    }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3.5).dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(letters, color = Color.White, fontWeight = FontWeight.Bold,
            style = if (size >= 40) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun connectionChip(connection: PlatformConnection?): Pair<String, Tone> = when {
    connection == null || connection.status == ConnectionStatus.DISCONNECTED -> "Bağlı değil" to AppColors.Slate
    connection.status == ConnectionStatus.PENDING_RESTAURANT -> "Restoran seçilmedi" to AppColors.Amber
    connection.status == ConnectionStatus.ERROR -> "Hata" to AppColors.Red
    else -> "Bağlı" to AppColors.Green
}

@Composable
private fun PlatformCard(
    platform: Platform,
    connection: PlatformConnection?,
    branchName: String?,
    loadFailed: Boolean,
    onClick: () -> Unit,
) {
    val (chip, tone) = connectionChip(connection)
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlatformMark(platform)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(platform.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        connection?.isLive == true -> listOfNotNull(connection.storeName, branchName ?: "Tüm şubeler").joinToString(" · ")
                        connection?.status == ConnectionStatus.PENDING_RESTAURANT -> "Bağlantıyı tamamlamak için restoran seçin"
                        connection?.status == ConnectionStatus.ERROR -> connection.lastError ?: "Bağlantı hata verdi"
                        else -> "Hesabınızı bağlayın, siparişler buraya düşsün"
                    },
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loadFailed) {
                    Text("Siparişler şu an alınamadı", color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(chip, tone)
        }
    }
}

@Composable
internal fun OrderRow(item: FeedOrder, busy: Boolean, onOpen: () -> Unit, onAction: (PlatformOrderAction) -> Unit) {
    val order = item.order
    val stage = item.stage
    val isNew = stage == OrderStage.NEW
    var confirm by remember { mutableStateOf<PlatformOrderAction?>(null) }
    AppCard(
        onClick = onOpen,
        modifier = if (isNew) Modifier.border(1.5.dp, AppColors.Accent, RoundedCornerShape(16.dp)) else Modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlatformMark(item.platform, size = 34)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "#${order.orderNumber ?: order.externalOrderId ?: order.id}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(order.customerName, formatElapsed(order.packageCreatedAt ?: order.updatedAt).takeIf { it != "—" })
                        .joinToString(" · "),
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(order.totalAmount, order.currency), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                StatusChip(stage.label, stage.tone())
            }
        }
        if (order.items.isNotEmpty()) {
            Text(
                order.items.joinToString(", ") { "${it.quantity}× ${it.productName}" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        val actions = stage.actions()
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(actions[1].label, onClick = { confirm = actions[1] }, enabled = !busy, modifier = Modifier.weight(1f))
                PrimaryButton(actions[0].label, onClick = { onAction(actions[0]) }, enabled = !busy, modifier = Modifier.weight(1f))
            }
        }
    }
    confirm?.let { action -> ConfirmAction(action, item.platform, onConfirm = { onAction(action) }, onDismiss = { confirm = null }) }
}

@Composable
private fun ConfirmAction(action: PlatformOrderAction, platform: Platform, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = if (action == PlatformOrderAction.REJECT) "Sipariş reddedilsin mi?" else "Sipariş iptal edilsin mi?",
        message = "${platform.title} müşteriye bildirir ve bu işlem geri alınamaz.",
        confirmLabel = action.label,
        onConfirm = {
            onDismiss()
            onConfirm()
        },
        onDismiss = onDismiss,
    )
}

/** Everything about one platform order, with its actions at the bottom. */
@Composable
internal fun OrderSheet(item: FeedOrder, busy: Boolean, onAction: (PlatformOrderAction) -> Unit, onDismiss: () -> Unit) {
    val order = item.order
    var confirm by remember { mutableStateOf<PlatformOrderAction?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformMark(item.platform)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("#${order.orderNumber ?: order.externalOrderId ?: order.id}", style = MaterialTheme.typography.titleLarge)
                    Text("${item.platform.title} · ${formatDateTime(order.packageCreatedAt ?: order.updatedAt)}",
                        color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                StatusChip(item.stage.label, item.stage.tone())
            }
            Spacer(Modifier.height(16.dp))
            order.customerName?.let { KeyValueRow("Müşteri", it) }
            order.customerPhone?.let { KeyValueRow("Telefon", it) }
            order.deliveryType?.let { KeyValueRow("Teslimat", it) }
            order.paymentMethod?.let { KeyValueRow("Ödeme", it) }
            order.deliveryAddress?.takeIf { it.isNotBlank() }?.let {
                Text("Adres", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            order.note?.takeIf { it.isNotBlank() }?.let {
                Row(Modifier.padding(top = 12.dp)) { StatusChip("Not: $it", AppColors.Amber) }
            }
            HorizontalDivider(color = AppColors.Line, modifier = Modifier.padding(vertical = 16.dp))
            order.items.forEach { line ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text("${line.quantity}×", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(line.productName, style = MaterialTheme.typography.bodyMedium)
                        listOfNotNull(line.options, line.detail).filter { it.isNotBlank() }.forEach {
                            Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    line.unitPrice?.let { Text(formatMoney(it * line.quantity, order.currency), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            HorizontalDivider(color = AppColors.Line, modifier = Modifier.padding(vertical = 12.dp))
            Row {
                Text("Toplam", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(formatMoney(order.totalAmount, order.currency), style = MaterialTheme.typography.titleMedium)
            }
            val actions = item.stage.actions()
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                PrimaryButton(actions[0].label, onClick = { onAction(actions[0]) }, enabled = !busy, loading = busy,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlineButton(actions[1].label, onClick = { confirm = actions[1] }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    confirm?.let { action -> ConfirmAction(action, item.platform, onConfirm = { onAction(action) }, onDismiss = { confirm = null }) }
}

// ---------------------------------------------------------------------------------------------------------------
// One platform

data class PlatformDetail(
    val connection: PlatformConnection?,
    val restaurants: List<PlatformRestaurant>,
    val orders: List<FeedOrder>,
)

class PlatformViewModel(private val repository: IntegrationRepository, val platform: Platform) :
    LoadViewModel<PlatformDetail>(ORDER_POLL_MILLIS) {

    private val _notice = MutableStateFlow<String?>(null)
    /** One-line result of the last sync, shown under the buttons. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        start()
    }

    override suspend fun fetch(): PlatformDetail {
        val connection = repository.connection(platform)
        val restaurants = if (connection?.status == ConnectionStatus.PENDING_RESTAURANT && platform == Platform.UBER_EATS) {
            runCatching { repository.restaurants(platform) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val orders = if (connection?.isLive == true) repository.orders(platform).map { FeedOrder(platform, it) } else emptyList()
        return PlatformDetail(connection, restaurants, orders)
    }

    fun connect(form: ConnectForm, onDone: () -> Unit) {
        runAction(onSuccess = onDone) { repository.connect(platform, form) }
    }

    /** Re-sends the stored account with the chosen restaurant; the secrets stay on the server. */
    fun chooseRestaurant(connection: PlatformConnection, restaurantId: String) {
        val account = connection.sellerId ?: connection.chainId ?: return
        runAction { repository.connect(platform, ConnectForm(accountId = account, storeId = restaurantId)) }
    }

    fun assignBranch(branchId: Long?) {
        runAction { repository.assignBranch(platform, branchId) }
    }

    fun disconnect() {
        runAction { repository.disconnect(platform) }
    }

    fun sync() {
        runAction {
            val result = repository.sync(platform)
            _notice.value = if (result.upserted == 0) "Yeni sipariş yok, liste güncel." else "${result.upserted} sipariş güncellendi."
        }
    }

    fun act(order: FeedOrder, action: PlatformOrderAction) {
        runAction { repository.orderAction(platform, order.order.id, action) }
    }
}

@Composable
fun PlatformScreen(viewModel: PlatformViewModel, branches: List<Branch>, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val platform = viewModel.platform
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<FeedOrder?>(null) }

    AppScreen(title = platform.title, onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) { detail ->
            val connection = detail.connection
            val needsForm = editing || connection == null || connection.status == ConnectionStatus.DISCONNECTED
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                item { ConnectionHeader(platform, connection) }
                when {
                    connection == null || needsForm -> item {
                        ConnectFormCard(platform, connection, busy = state.busy, onCancel = if (editing) ({ editing = false }) else null) {
                            viewModel.connect(it, onDone = { editing = false })
                        }
                    }
                    connection.status == ConnectionStatus.PENDING_RESTAURANT -> item {
                        RestaurantPicker(platform, detail.restaurants, busy = state.busy,
                            onPick = { viewModel.chooseRestaurant(connection, it) }, onEdit = { editing = true })
                    }
                    else -> {
                        item {
                            ConnectedCard(
                                connection, branches, busy = state.busy, notice = notice,
                                onBranch = viewModel::assignBranch,
                                onSync = viewModel::sync,
                                onEdit = { editing = true },
                                onDisconnect = { confirmDisconnect = true },
                            )
                        }
                        item { SectionLabel("Siparişler", Modifier.padding(top = 8.dp)) }
                        item { LiveHint() }
                        if (detail.orders.isEmpty()) {
                            item {
                                EmptyState("Henüz sipariş yok", "${platform.title} üzerinden gelen siparişler burada görünecek.", icon = AppIcons.Orders)
                            }
                        }
                        items(detail.orders, key = { it.order.id }) { item ->
                            OrderRow(item, busy = state.busy, onOpen = { opened = item }, onAction = { viewModel.act(item, it) })
                        }
                    }
                }
            }
        }
    }

    opened?.let { order ->
        val fresh = state.data?.orders?.firstOrNull { it.order.id == order.order.id } ?: order
        OrderSheet(fresh, busy = state.busy, onAction = { viewModel.act(fresh, it) }, onDismiss = { opened = null })
    }
    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Bağlantı kesilsin mi?",
            message = "${platform.title} siparişleri artık buraya ve mutfağa düşmez. Bilgileriniz saklanır, istediğiniz zaman yeniden bağlayabilirsiniz.",
            confirmLabel = "Bağlantıyı kes",
            onConfirm = {
                confirmDisconnect = false
                viewModel.disconnect()
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

@Composable
private fun ConnectionHeader(platform: Platform, connection: PlatformConnection?) {
    val (chip, tone) = connectionChip(connection)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlatformMark(platform, size = 52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(platform.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    connection?.storeName ?: "Siparişleri tek ekrandan yönetin",
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            StatusChip(chip, tone)
        }
        connection?.lastError?.takeIf { it.isNotBlank() && connection.status == ConnectionStatus.ERROR }?.let {
            Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Field labels and help per platform; Uber Eats shows them under Hesap Bilgileri › Entegrasyon Bilgileri. */
private data class FormSpec(
    val account: String,
    val store: String?,
    val key: String,
    val secret: String,
    val webhook: String?,
    val help: String,
)

private fun Platform.formSpec() = when (this) {
    Platform.YEMEKSEPETI -> FormSpec(
        account = "Chain ID",
        store = "Vendor ID (restoran)",
        key = "Client ID",
        secret = "Client Secret",
        webhook = "Webhook Secret",
        help = "Bu bilgileri Yemeksepeti Partner Portal'daki entegrasyon ayarlarından alabilirsiniz.",
    )
    else -> FormSpec(
        account = "Satıcı ID (Seller ID)",
        store = null,
        key = "API Key",
        secret = "API Secret",
        webhook = null,
        help = "Uber Eats Trendyol Go satıcı panelinde Hesap Bilgileri › Entegrasyon Bilgileri altında bulunur.",
    )
}

@Composable
private fun ConnectFormCard(
    platform: Platform,
    connection: PlatformConnection?,
    busy: Boolean,
    onCancel: (() -> Unit)?,
    onSubmit: (ConnectForm) -> Unit,
) {
    val spec = platform.formSpec()
    // Secrets are never sent back, so an existing connection keeps them unless new ones are typed.
    val hasSecrets = connection?.apiKeyMasked != null || connection?.clientIdMasked != null
    var account by rememberSaveable { mutableStateOf(connection?.sellerId ?: connection?.chainId ?: "") }
    var store by rememberSaveable { mutableStateOf(connection?.vendorId ?: "") }
    var key by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    var webhook by rememberSaveable { mutableStateOf("") }
    val secretsOk = hasSecrets || (key.isNotBlank() && secret.isNotBlank() && (spec.webhook == null || webhook.isNotBlank()))
    val canSubmit = account.isNotBlank() && secretsOk && !busy
    val secretHint = if (hasSecrets) " (değiştirmek için doldurun)" else ""

    AppCard {
        Text(if (connection == null) "Hesabınızı bağlayın" else "Bağlantı bilgileri", style = MaterialTheme.typography.titleMedium)
        Text(spec.help, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(account, { account = it }, spec.account, Modifier.fillMaxWidth())
            spec.store?.let { AppTextField(store, { store = it }, it, Modifier.fillMaxWidth()) }
            AppTextField(key, { key = it }, spec.key + secretHint, Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
            AppTextField(secret, { secret = it }, spec.secret + secretHint, Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
            spec.webhook?.let {
                AppTextField(webhook, { webhook = it }, it + secretHint, Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            if (connection == null || connection.status == ConnectionStatus.DISCONNECTED) "Bağlan" else "Kaydet",
            onClick = { onSubmit(ConnectForm(account, store, key, secret, webhook)) },
            enabled = canSubmit,
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
        onCancel?.let { OutlineButton("Vazgeç", onClick = it, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
        Text(
            "Anahtarlar şifrelenerek saklanır ve bir daha gösterilmez.",
            color = AppColors.Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun RestaurantPicker(
    platform: Platform,
    restaurants: List<PlatformRestaurant>,
    busy: Boolean,
    onPick: (String) -> Unit,
    onEdit: () -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    AppCard {
        Text("Restoranınızı seçin", style = MaterialTheme.typography.titleMedium)
        Text(
            if (platform == Platform.YEMEKSEPETI) "Bağlantıyı tamamlamak için Vendor ID'yi girin."
            else "Hesap doğrulandı. Siparişleri alınacak restoranı seçin.",
            color = AppColors.Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        if (restaurants.isEmpty()) {
            Text("Hesapta restoran bulunamadı.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        restaurants.forEach { restaurant ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) { chosen = restaurant.id }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = chosen == restaurant.id, onClick = { chosen = restaurant.id })
                Column(Modifier.weight(1f)) {
                    Text(restaurant.name ?: restaurant.id, style = MaterialTheme.typography.bodyLarge)
                    restaurant.address?.let { Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (restaurants.isNotEmpty()) {
            PrimaryButton("Bu restoranı bağla", onClick = { chosen?.let(onPick) }, enabled = chosen != null && !busy, loading = busy,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        OutlineButton("Bilgileri düzenle", onClick = onEdit, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConnectedCard(
    connection: PlatformConnection,
    branches: List<Branch>,
    busy: Boolean,
    notice: String?,
    onBranch: (Long?) -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onDisconnect: () -> Unit,
) {
    AppCard {
        KeyValueRow(if (connection.sellerId != null) "Satıcı ID" else "Chain ID", connection.sellerId ?: connection.chainId ?: "—")
        (connection.apiKeyMasked ?: connection.clientIdMasked)?.let { KeyValueRow("Anahtar", it) }
        KeyValueRow("Son eşitleme", connection.lastSyncedAt?.let(::formatDateTime) ?: "—")

        Text("Siparişler hangi şubeye düşsün?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            ChoiceChip("Tüm şubeler", selected = connection.branchId == null, onClick = { if (!busy) onBranch(null) })
            branches.forEach { branch ->
                ChoiceChip(branch.name, selected = connection.branchId == branch.id, onClick = { if (!busy) onBranch(branch.id) })
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Siparişleri şimdi eşitle", onClick = onSync, enabled = !busy, loading = busy, icon = AppIcons.Refresh,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Siparişleri platformdan şimdi çek" })
        notice?.let { Text(it, color = AppColors.Green.content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton("Bilgileri güncelle", onClick = onEdit, enabled = !busy, modifier = Modifier.weight(1f))
            OutlineButton("Bağlantıyı kes", onClick = onDisconnect, enabled = !busy, modifier = Modifier.weight(1f))
        }
    }
}
