package com.mohit.recall_ai.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mohit.recall_ai.ui.theme.ColorNavy

/**
 * Shared UI components used by auth screens.
 */

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
