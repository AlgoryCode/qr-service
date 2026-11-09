package com.algoryqr.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Shows the spinner, the error card or the content of a [LoadState], keeping old data visible during reloads. */
@Composable
fun <T> LoadContent(state: LoadState<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    val data = state.data
    when {
        data != null -> content(data)
        state.error != null -> Box(Modifier.padding(ScreenPadding)) { ErrorState(state.error, onRetry) }
        else -> LoadingContent()
    }
}

/** Red banner for a failed action (e.g. "Masa kapatılamadı"), dismissed with the close icon. */
@Composable
fun ActionErrorBanner(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Red.container)
            .padding(start = 14.dp),
    ) {
        Text(message, color = AppColors.Red.content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) {
            Icon(AppIcons.Close, contentDescription = "Kapat", tint = AppColors.Red.content, modifier = Modifier.size(18.dp))
        }
    }
}

/** Sign-in layout shared by both apps: brand row, headline, subtitle and the form card. */
@Composable
fun AuthScaffold(
    brand: String,
    brandIcon: ImageVector,
    title: String,
    subtitle: String,
    /** Why the last session ended on its own ("Oturumunuzun süresi doldu…"), shown above the form. */
    notice: String? = null,
    form: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(brandIcon, AppColors.Red)
            Spacer(Modifier.width(10.dp))
            Text(brand, style = MaterialTheme.typography.labelMedium, color = AppColors.AccentText)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = AppColors.Muted)
        Spacer(Modifier.height(28.dp))
        notice?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.Amber.container)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(AppIcons.Lock, contentDescription = null, tint = AppColors.Amber.content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(it, color = AppColors.Amber.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { form() }
        }
    }
}
