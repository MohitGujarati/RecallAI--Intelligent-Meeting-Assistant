package com.example.recall_ai.ui.login


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.recall_ai.ui.theme.ColorBorder
import com.example.recall_ai.ui.theme.ColorNavy
import com.example.recall_ai.ui.theme.ColorOnSurfaceDim
import com.example.recall_ai.ui.theme.ColorSurface
import com.example.recall_ai.ui.theme.ColorTextSlate900

/**
 * Shared UI components used by both [LoginScreen] and [SignupScreen].
 *
 * Extracted here so the same composable is not copy-pasted into two files.
 * Any change to field labels, pill style, or text field colors automatically
 * applies to both auth screens.
 */

// ── Field label above each text field ─────────────────────────────────────────
@Composable
internal fun FieldLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = ColorTextSlate900
    )
}

// ── Feature pill shown at the bottom of both screens ─────────────────────────
@Composable
internal fun FeaturePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ColorNavy.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text      = text,
            style     = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color     = ColorNavy,
            textAlign = TextAlign.Center
        )
    }
}

// ── Consistent OutlinedTextField colors for all auth fields ──────────────────
@Composable
internal fun recallTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor        = ColorNavy,
    unfocusedBorderColor      = ColorBorder,
    focusedContainerColor     = ColorSurface,
    unfocusedContainerColor   = ColorSurface,
    cursorColor               = ColorNavy,
    focusedLeadingIconColor   = ColorNavy,
    unfocusedLeadingIconColor = ColorOnSurfaceDim,
    focusedLabelColor         = ColorNavy,
)