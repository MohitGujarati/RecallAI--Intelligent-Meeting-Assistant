package com.example.recall_ai.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recall_ai.ui.theme.ColorBackground
import com.example.recall_ai.ui.theme.ColorBorder
import com.example.recall_ai.ui.theme.ColorDone
import com.example.recall_ai.ui.theme.ColorNavy
import com.example.recall_ai.ui.theme.ColorOnBackground
import com.example.recall_ai.ui.theme.ColorOnSurfaceDim
import com.example.recall_ai.ui.theme.ColorSurface
import com.example.recall_ai.ui.theme.ColorTextSlate400
import com.example.recall_ai.ui.theme.ColorTextSlate900

// ═══════════════════════════════════════════════════════════════════════════════
// ACTION ITEMS SCREEN — aggregated To-Do list from all meetings
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ActionItemsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ActionItemsViewModel = hiltViewModel()
) {
    val groups by viewModel.actionGroups.collectAsStateWithLifecycle()
    val checked by viewModel.checkedItems.collectAsStateWithLifecycle()

    val totalItems = remember(groups) { groups.sumOf { it.items.size } }
    val completedCount = remember(groups, checked) {
        groups.sumOf { group ->
            group.items.count { item ->
                checked.contains(ActionItemsViewModel.itemKey(item.meetingId, item.text))
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ColorBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Top Bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ColorNavy
                    )
                }
                Text(
                    text = "Action Items",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = ColorOnBackground
                )
                // Spacer to balance the layout
                Box(modifier = Modifier.size(48.dp))
            }

            // ── Progress summary chip ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorNavy.copy(alpha = 0.06f))
                    .border(1.dp, ColorNavy.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.TaskAlt,
                    contentDescription = null,
                    tint = ColorNavy,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (totalItems == 0) "No action items yet"
                        else "$completedCount of $totalItems completed",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = ColorOnBackground
                    )
                    if (totalItems > 0) {
                        Text(
                            text = "From ${groups.size} meeting${if (groups.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorOnSurfaceDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Grouped action items list ──────────────────────────────────
            if (groups.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ColorNavy.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = ColorNavy,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No action items",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = ColorOnBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Action items from your\nmeeting summaries will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorOnSurfaceDim,
                        lineHeight = 20.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groups.forEachIndexed { groupIndex, group ->
                        // ── Meeting header ─────────────────────────────────
                        item(key = "header_${group.meetingId}") {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(200 + groupIndex * 50)) +
                                        slideInVertically(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ),
                                            initialOffsetY = { it / 3 }
                                        )
                            ) {
                                MeetingGroupHeader(
                                    title = group.meetingTitle,
                                    emoji = group.meetingEmoji,
                                    colorHex = group.meetingColorHex,
                                    itemCount = group.items.size,
                                    checkedCount = group.items.count { item ->
                                        checked.contains(
                                            ActionItemsViewModel.itemKey(
                                                item.meetingId,
                                                item.text
                                            )
                                        )
                                    },
                                    isFirstGroup = groupIndex == 0
                                )
                            }
                        }

                        // ── Action item rows ───────────────────────────────
                        itemsIndexed(
                            items = group.items,
                            key = { _, item ->
                                ActionItemsViewModel.itemKey(item.meetingId, item.text)
                            }
                        ) { itemIndex, item ->
                            val key = ActionItemsViewModel.itemKey(item.meetingId, item.text)
                            val isChecked = checked.contains(key)

                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(250 + groupIndex * 50 + itemIndex * 30)) +
                                        slideInVertically(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ),
                                            initialOffsetY = { it / 3 }
                                        )
                            ) {
                                ActionItemRow(
                                    text = item.text,
                                    isChecked = isChecked,
                                    onToggle = { viewModel.toggleItem(key) },
                                    isLast = itemIndex == group.items.lastIndex
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MEETING GROUP HEADER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MeetingGroupHeader(
    title: String,
    emoji: String?,
    colorHex: String?,
    itemCount: Int,
    checkedCount: Int,
    isFirstGroup: Boolean
) {
    val bgColor = parseActionColor(colorHex)
    val displayEmoji = emoji ?: defaultActionEmoji(title)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isFirstGroup) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Meeting emoji badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(text = displayEmoji, fontSize = 14.sp)
            }

            // Meeting title
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = ColorOnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Item count badge
            Text(
                text = "$checkedCount/$itemCount",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = if (checkedCount == itemCount && itemCount > 0) ColorDone else ColorTextSlate400
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ACTION ITEM ROW — checkbox + text
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActionItemRow(
    text: String,
    isChecked: Boolean,
    onToggle: () -> Unit,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ColorSurface)
            .border(
                width = 1.dp,
                color = if (isChecked) ColorDone.copy(alpha = 0.25f) else ColorBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Checkbox icon
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = if (isChecked) Icons.Default.CheckCircle
                else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isChecked) "Completed" else "Mark complete",
                tint = if (isChecked) ColorDone else ColorTextSlate400,
                modifier = Modifier.size(22.dp)
            )
        }

        // Item text
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Medium
            ),
            color = if (isChecked) ColorTextSlate400 else ColorTextSlate900,
            modifier = Modifier.weight(1f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

private fun parseActionColor(hex: String?): Color {
    if (hex == null) return Color(0xFFE8EAF0)
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFFE8EAF0)
    }
}

private fun defaultActionEmoji(title: String): String {
    return when {
        title.contains("standup", true) || title.contains("daily", true) -> "📋"
        title.contains("review", true) -> "🔍"
        title.contains("plan", true) -> "📅"
        else -> "🎙"
    }
}
