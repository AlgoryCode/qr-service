package com.algoryqr.business.feature.reservations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoryqr.business.data.BusinessRepository
import com.algoryqr.business.data.Reservation
import com.algoryqr.business.data.ReservationStatus
import com.algoryqr.shared.format.formatDateTime
import com.algoryqr.shared.ui.ActionErrorBanner
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.EmptyState
import com.algoryqr.shared.ui.IconTile
import com.algoryqr.shared.ui.ItemSpacing
import com.algoryqr.shared.ui.LoadContent
import com.algoryqr.shared.ui.LoadViewModel
import com.algoryqr.shared.ui.OutlineButton
import com.algoryqr.shared.ui.PrimaryButton
import com.algoryqr.shared.ui.RefreshAction
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.StatusChip

class ReservationsViewModel(
    private val repository: BusinessRepository,
    private val menuId: Long?,
) : LoadViewModel<List<Reservation>>() {

    init {
        start()
    }

    override suspend fun fetch(): List<Reservation> = menuId?.let { repository.reservations(it) }.orEmpty()

    fun setStatus(reservation: Reservation, status: ReservationStatus) {
        val id = menuId ?: return
        runAction { repository.updateReservation(id, reservation.id, status) }
    }
}

@Composable
fun ReservationsScreen(viewModel: ReservationsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScreen(title = "Rezervasyonlar", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        ActionErrorBanner(state.actionError, viewModel::dismissActionError)
        LoadContent(state, onRetry = viewModel::refresh) { reservations ->
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = ItemSpacing) {
                if (reservations.isEmpty()) {
                    item {
                        EmptyState(
                            "Rezervasyon yok",
                            "Müşteriler QR menüden rezervasyon bıraktığında burada görünür.",
                            icon = AppIcons.Reservation,
                        )
                    }
                }
                items(reservations, key = { it.id }) { reservation ->
                    ReservationCard(
                        reservation,
                        busy = state.busy,
                        onApprove = { viewModel.setStatus(reservation, ReservationStatus.ACTIVE) },
                        onCancel = { viewModel.setStatus(reservation, ReservationStatus.CANCELED) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReservationCard(reservation: Reservation, busy: Boolean, onApprove: () -> Unit, onCancel: () -> Unit) {
    val tone = when (reservation.status) {
        ReservationStatus.PENDING -> AppColors.Red
        ReservationStatus.ACTIVE -> AppColors.Green
        ReservationStatus.CANCELED -> AppColors.Slate
    }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(AppIcons.Reservation, tone)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(reservation.customerName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatDateTime(reservation.reservationAt)} · ${reservation.partySize} kişi",
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            StatusChip(reservation.status.label, tone)
        }
        reservation.phone?.let {
            Spacer(Modifier.height(8.dp))
            Text("Telefon: $it", style = MaterialTheme.typography.bodyMedium)
        }
        reservation.note?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            StatusChip("Not: $it", AppColors.Amber)
        }
        if (reservation.status != ReservationStatus.CANCELED) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("İptal et", onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f))
                if (reservation.status == ReservationStatus.PENDING) {
                    PrimaryButton("Onayla", onClick = onApprove, enabled = !busy, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
