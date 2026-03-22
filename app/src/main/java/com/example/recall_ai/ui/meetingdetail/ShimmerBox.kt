package com.example.recall_ai.ui.meetingdetail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.recall_ai.ui.theme.ColorBorder
import com.example.recall_ai.ui.theme.ColorSurface
import com.example.recall_ai.ui.theme.ColorSurfaceVariant

/**
 * Single shimmer line — an alpha-pulsing rounded rectangle.
 *
 * ── Alpha-pulse vs gradient sweep ────────────────────────────────────
 * The travelling-gradient "Facebook skeleton" style requires a custom
 * DrawModifier that measures the layout's pixel bounds and animates a
 * brush x-offset — roughly 50 lines of boilerplate with no external lib.
 *
 * Alpha-pulse delivers the same "content is loading" signal in ~10 lines.
 * It reads as clearly "placeholder" and matches the dark app aesthetic
 * (gradient sheen can look jarring on very dark surfaces).
 *
 * Swap only this composable if a richer shimmer is needed later —
 * all call sites stay unchanged.
 *
 * @param widthFraction  Width as fraction of parent (0.0–1.0). Vary between
 *                       lines to simulate realistic text paragraph widths.
 * @param height         Line height in dp. Use 13–14 dp for body text,
 *                       18–20 dp for headings.
 * @param cornerRadius   Pill radius of the placeholder bar.
 * @param modifier       Additional layout modifiers from call site.
 */
@Composable
fun ShimmerBox(
    widthFraction: Float   = 1f,
    height:        Dp      = 14.dp,
    cornerRadius:  Dp      = 6.dp,
    modifier:      Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.20f,
        targetValue   = 0.50f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .alpha(alpha)
            .background(ColorSurfaceVariant)
    )
}

/**
 * A full skeleton card for one summary section.
 *
 * Renders a section header stub + [lineCount] body lines at varying widths,
 * all wrapped inside a surface card that matches the real section card shape.
 * Drop this wherever a real [SectionCard] would appear.
 *
 * @param lineCount  Number of body shimmer lines. Use 3–4 for paragraph
 *                   sections, 2–3 for list sections (key points / action items).
 */
@Composable
fun ShimmerSectionCard(
    lineCount: Int      = 4,
    modifier:  Modifier = Modifier
) {
    // Widths cycle so adjacent lines don't look identical
    val lineWidths = listOf(1f, 0.88f, 0.94f, 0.62f, 0.80f, 0.72f, 0.55f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ColorSurface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section label stub (narrower, taller — mimics ALL-CAPS label)
        ShimmerBox(widthFraction = 0.28f, height = 10.dp, cornerRadius = 4.dp)

        Spacer(modifier = Modifier.height(4.dp))

        repeat(lineCount) { i ->
            ShimmerBox(
                widthFraction = lineWidths[i % lineWidths.size],
                height        = 13.dp
            )
        }
    }
}