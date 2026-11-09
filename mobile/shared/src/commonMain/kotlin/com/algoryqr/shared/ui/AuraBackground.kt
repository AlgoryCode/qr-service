package com.algoryqr.shared.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** White canvas with a soft red aura that slowly drifts; drawn once at the app root so every screen shares it. */
@Composable
fun AuraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val drift by rememberInfiniteTransition(label = "aura").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aura-drift",
    )

    val palette = AppColors.palette

    // White stays dominant: two soft glows sit above the top edge and fade out within the first third of the screen.
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(palette.base)
                glow(palette.auraPrimary, alpha = palette.auraAlpha, x = 0.05f + 0.15f * drift, y = -0.08f, radius = 0.55f)
                glow(palette.auraSecondary, alpha = palette.auraAlpha * 1.4f, x = 0.95f - 0.15f * drift, y = -0.04f, radius = 0.45f)
            },
    ) {
        content()
    }
}

private fun DrawScope.glow(color: Color, alpha: Float, x: Float, y: Float, radius: Float) {
    val center = Offset(size.width * x, size.height * y)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = size.maxDimension * radius,
        ),
        radius = size.maxDimension * radius,
        center = center,
    )
}
