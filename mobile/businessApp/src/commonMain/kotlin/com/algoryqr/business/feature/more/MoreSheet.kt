package com.algoryqr.business.feature.more

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.algoryqr.shared.session.Session
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.IconListRow
import com.algoryqr.shared.ui.IconTile
import com.algoryqr.shared.ui.Tone

/** Destinations of the "Daha fazla" sheet. */
enum class MoreDestination { STAFF, PRODUCTS, RESERVATIONS, INTEGRATIONS, FLOWS, KITCHEN_ROUTING, ACCOUNT }

private class MoreItem(val destination: MoreDestination, val label: String, val icon: ImageVector, val tone: @Composable () -> Tone)

private val manageItems = listOf(
    MoreItem(MoreDestination.STAFF, "Personel", AppIcons.Staff) { AppColors.Blue },
    MoreItem(MoreDestination.PRODUCTS, "Ürünler", AppIcons.Product) { AppColors.Green },
    MoreItem(MoreDestination.RESERVATIONS, "Rezervasyon", AppIcons.Reservation) { AppColors.Red },
    MoreItem(MoreDestination.INTEGRATIONS, "Entegrasyonlar", AppIcons.Delivery) { AppColors.Amber },
)

private val settingItems = listOf(
    MoreItem(MoreDestination.FLOWS, "Sipariş akışı", AppIcons.Orders) { AppColors.Rose },
    MoreItem(MoreDestination.KITCHEN_ROUTING, "Mutfak", AppIcons.Kitchen) { AppColors.Amber },
)

/**
 * Everything that doesn't earn a place in the bottom bar, one tap away: large labelled tiles in two groups,
 * with the owner's account on top so "where is sign out" never needs a search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(session: Session, onOpen: (MoreDestination) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Text("Daha fazla", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            IconListRow(
                icon = AppIcons.Account,
                tone = AppColors.Slate,
                title = session.profile.displayName ?: "Hesabım",
                subtitle = "Hesap, şube bilgisi ve çıkış",
                onClick = { onOpen(MoreDestination.ACCOUNT) },
            )
            Group("Yönetim", manageItems, onOpen)
            Group("Ayarlar", settingItems, onOpen)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Group(title: String, items: List<MoreItem>, onOpen: (MoreDestination) -> Unit) {
    Text(
        title,
        color = AppColors.Muted,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    // Three per row keeps every label readable on small phones; a short row is padded with empty space.
    items.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            row.forEach { item -> MoreTile(item, onOpen, Modifier.weight(1f)) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MoreTile(item: MoreItem, onOpen: (MoreDestination) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .border(1.dp, AppColors.Line, shape)
            .clickable(role = Role.Button) { onOpen(item.destination) }
            .padding(vertical = 14.dp, horizontal = 6.dp),
    ) {
        IconTile(item.icon, item.tone())
        Spacer(Modifier.height(10.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
