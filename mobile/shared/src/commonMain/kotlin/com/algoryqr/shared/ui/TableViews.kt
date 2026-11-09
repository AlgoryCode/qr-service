package com.algoryqr.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.algoryqr.shared.format.formatMoney
import com.algoryqr.shared.model.TableBillStatus
import com.algoryqr.shared.model.TableSummary

/** Every tile has the same height, so the floor plan stays a calm, even grid however much is going on. */
private val TileHeight = 120.dp
private val TileShape = RoundedCornerShape(18.dp)

private data class TableState(val label: String, val tone: Tone, val urgent: Boolean)

/**
 * The one thing that matters most at this table right now. Free is green and taken is red, like a booking board;
 * the kitchen is orange, and food waiting at the pass is blue so it never reads as a free table.
 */
@Composable
private fun TableSummary.state(): TableState = when {
    readyOrderCount > 0 -> TableState(if (readyOrderCount > 1) "$readyOrderCount sipariş hazır" else "Sipariş hazır", AppColors.Blue, true)
    pendingOrderCount > 0 -> TableState(if (pendingOrderCount > 1) "$pendingOrderCount onay bekliyor" else "Onay bekliyor", AppColors.Red, true)
    kitchenOrderCount > 0 -> TableState("Mutfakta", AppColors.Amber, false)
    billStatus == TableBillStatus.OPEN -> TableState("Dolu", AppColors.Red, false)
    else -> TableState("Boş", AppColors.Green, false)
}

/**
 * Floor-plan tile: the table name, and at the bottom the state and the open amount. Each tile is tinted in its
 * state colour with a matching outline; tables that need the waiter (food ready, order to approve) get a thicker one.
 */
@Composable
fun TableTile(table: TableSummary, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val state = table.state()
    val tone = state.tone
    val occupied = table.isOccupied
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(TileHeight)
            .clip(TileShape)
            .background(tone.content.copy(alpha = 0.12f).compositeOver(AppColors.Surface))
            .border(
                if (state.urgent) 2.dp else 1.dp,
                if (state.urgent) tone.content else tone.content.copy(alpha = 0.4f),
                TileShape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        // The name already carries the number ("Masa 4"), so no separate number badge.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(table.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    table.areaName ?: if (occupied) "${table.openBillItemCount} ürün" else "Müsait",
                    color = AppColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tone.content))
            Spacer(Modifier.width(6.dp))
            Text(
                state.label,
                color = tone.content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            table.openBillTotal?.takeIf { it > 0 }?.let {
                Spacer(Modifier.width(8.dp))
                Text(formatMoney(it), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
    }
}

