package com.algoryqr.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardShape = RoundedCornerShape(16.dp)
private val ControlShape = RoundedCornerShape(14.dp)

/** White card with a thin grey outline and no shadow. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Line, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun AppScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = if (onBack == null) 20.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, contentDescription = "Geri")
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
        content()
    }
}

/** Toolbar action that reloads the screen, shown on every live list. */
@Composable
fun RefreshAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(AppIcons.Refresh, contentDescription = "Yenile", tint = AppColors.Muted)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Accent,
            contentColor = Color.White,
            disabledContainerColor = AppColors.Red.container,
            disabledContentColor = AppColors.Muted,
        ),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            ButtonLabel(text, icon)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Red.container,
            contentColor = AppColors.AccentText,
            disabledContainerColor = AppColors.Subtle,
            disabledContentColor = AppColors.Muted,
        ),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        ButtonLabel(text, icon)
    }
}

/** Quiet outlined button for the less likely choice next to a primary one, e.g. "Reddet" beside "Onayla". */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        ButtonLabel(text, icon)
    }
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.AccentText,
            unfocusedBorderColor = AppColors.Line,
            focusedLabelColor = AppColors.AccentText,
            unfocusedLabelColor = AppColors.Muted,
            focusedContainerColor = AppColors.Surface,
            unfocusedContainerColor = AppColors.Surface,
            cursorColor = AppColors.AccentText,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        // Locale-aware so Turkish "i" becomes "İ" rather than "I".
        text = text.toUpperCase(Locale.current),
        style = MaterialTheme.typography.labelMedium,
        color = AppColors.Muted,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(message, color = AppColors.Danger, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
    }
}

@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppColors.AccentText)
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** Optional button under the text, e.g. "Adisyon aç" on a free table. */
    action: (@Composable () -> Unit)? = null,
) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(24.dp)) {
        if (icon != null) {
            IconTile(icon, AppColors.Red, modifier = Modifier.align(Alignment.CenterHorizontally), size = 48.dp)
            Spacer(Modifier.height(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            color = AppColors.Muted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { action() }
        }
    }
}

/** Error card with a retry button, used when a whole screen failed to load. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(24.dp)) {
        IconTile(AppIcons.Reject, AppColors.Red, modifier = Modifier.align(Alignment.CenterHorizontally), size = 48.dp)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        SecondaryButton("Tekrar dene", onClick = onRetry, icon = AppIcons.Refresh)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, color = AppColors.Muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = AppColors.Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç", color = AppColors.Ink) }
        },
    )
}

val ScreenPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)

val ItemSpacing = Arrangement.spacedBy(12.dp)

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    icon: ImageVector? = null,
    tone: Tone = AppColors.Red,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        icon?.let {
            IconTile(it, tone)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            description?.let {
                Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppColors.Accent,
                uncheckedTrackColor = AppColors.Subtle,
                uncheckedBorderColor = AppColors.Line,
            ),
        )
    }
}

/** Small soft-filled fact, e.g. "Hazırlanıyor"; [tone] colours it like the icon tiles, `null` keeps it neutral. */
@Composable
fun StatusChip(text: String, tone: Tone? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
        color = tone?.content ?: AppColors.Muted,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tone?.container ?: AppColors.Subtle)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * Line icon on a soft square, like the reference category icons: the corner radius is a third of the size and
 * the background is a pale version of the icon colour.
 */
@Composable
fun IconTile(icon: ImageVector, tone: Tone, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(RoundedCornerShape(size / 3)).background(tone.container),
    ) {
        Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(size * 0.55f))
    }
}

/** Single-choice chip: soft red when selected, outlined otherwise. */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AppColors.Red.container,
            selectedLabelColor = AppColors.AccentText,
            containerColor = AppColors.Surface,
            labelColor = AppColors.Ink,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = AppColors.Line,
            selectedBorderColor = AppColors.Accent,
        ),
    )
}

/** Tappable row with an icon tile, used for menus and navigation inside cards. */
@Composable
fun IconListRow(
    icon: ImageVector,
    tone: Tone,
    title: String,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
    ) {
        IconTile(icon, tone)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium) }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(AppIcons.Next, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** KPI card: icon tile, small label and a large value, laid out two per row on the dashboards. */
@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    tone: Tone,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    AppCard(modifier = modifier) {
        IconTile(icon, tone, size = 36.dp)
        Spacer(Modifier.height(12.dp))
        Text(label, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        caption?.let { Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
    }
}

/** Two or more equal options in an outlined track, like the AlgoryCar login/register switch. */
@Composable
fun <T> SegmentedSwitch(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.Line, shape)
            .background(AppColors.Surface, shape)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isSelected) AppColors.Red.container else AppColors.Surface)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) AppColors.AccentText else AppColors.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One tab of [AppBottomBar]. */
data class BottomTab<T>(val key: T, val label: String, val icon: ImageVector, val badge: Int = 0)

/** Flat bar matching the cards: white with a thin grey top line and no shadow. */
@Composable
fun <T> AppBottomBar(tabs: List<BottomTab<T>>, current: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppColors.Line)
        NavigationBar(containerColor = AppColors.Surface, tonalElevation = 0.dp) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab.key == current,
                    onClick = { onSelect(tab.key) },
                    icon = {
                        BadgedBox(badge = {
                            if (tab.badge > 0) {
                                Badge(containerColor = AppColors.Accent, contentColor = Color.White) { Text(tab.badge.toString()) }
                            }
                        }) {
                            Icon(tab.icon, contentDescription = null)
                        }
                    },
                    label = { Text(tab.label, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.AccentText,
                        selectedTextColor = AppColors.AccentText,
                        indicatorColor = AppColors.Red.container,
                        unselectedIconColor = AppColors.Muted,
                        unselectedTextColor = AppColors.Muted,
                    ),
                )
            }
        }
    }
}

/** Round −/+ stepper used when building an order. */
@Composable
fun QuantityStepper(quantity: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton(AppIcons.Remove, "Azalt", onDecrease, filled = false)
        Text(
            quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        StepperButton(AppIcons.Add, "Artır", onIncrease, filled = true)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit, filled: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) AppColors.Accent else AppColors.Red.container)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = if (filled) Color.White else AppColors.AccentText, modifier = Modifier.size(16.dp))
    }
}
