package com.example.recall_ai.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recall_ai.data.local.entity.Meeting
import com.example.recall_ai.ui.theme.ColorBackground
import com.example.recall_ai.ui.theme.ColorBorder
import com.example.recall_ai.ui.theme.ColorNavy
import com.example.recall_ai.ui.theme.ColorOnBackground
import com.example.recall_ai.ui.theme.ColorOnSurfaceDim
import com.example.recall_ai.ui.theme.ColorSurface
import com.example.recall_ai.ui.theme.ColorTextSlate400
import com.example.recall_ai.ui.theme.ColorTextSlate900

/**
 * All Recalls screen — full list of every meeting, matching the Stitch design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRecallsScreen(
    onNavigateBack:      () -> Unit,
    onNavigateToMeeting: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val meetings by viewModel.meetings.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var editMeeting by remember { mutableStateOf<Meeting?>(null) }

    val filteredMeetings by remember(meetings, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) meetings
            else meetings.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = ColorBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Top Bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
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
                    text  = "All Recalls",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = ColorOnBackground
                )
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = ColorNavy
                    )
                }
            }

            // ── Search Bar ───────────────────────────────────────────
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder   = {
                    Text(
                        "Search past recordings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorTextSlate400
                    )
                },
                leadingIcon   = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = ColorTextSlate400
                    )
                },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ColorNavy,
                    unfocusedBorderColor = ColorBorder,
                    focusedContainerColor   = ColorSurface,
                    unfocusedContainerColor = ColorSurface
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Meeting List ─────────────────────────────────────────
            LazyColumn(
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = filteredMeetings,
                    key   = { _, m -> m.id }
                ) { index, meeting ->

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteMeeting(meeting)
                                true
                            } else false
                        }
                    )

                    AnimatedVisibility(
                        visible = dismissState.currentValue != SwipeToDismissBoxValue.EndToStart,
                        enter   = fadeIn(tween(200)) + slideInVertically(tween(200 + index * 20)),
                        exit    = fadeOut(tween(150))
                    ) {
                        SwipeToDismissBox(
                            state                       = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = { SwipeDeleteBg(progress = dismissState.progress) },
                            content = {
                                AllRecallsRow(
                                    meeting = meeting,
                                    onClick = { onNavigateToMeeting(meeting.id) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Edit Dialog (shared same as Dashboard) ───────────────────────────
    editMeeting?.let { meeting ->
        // Uses the MeetingRow edit from DashboardScreen — reuse via public composable
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ALL RECALLS ROW — emoji + title + duration + date + chevron
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AllRecallsRow(meeting: Meeting, onClick: () -> Unit) {
    val (defaultEmoji, defaultBg) = defaultStyle(meeting.id)
    val emoji   = meeting.iconEmoji ?: defaultEmoji
    val bgColor = parseBgColor(meeting.iconColorHex ?: defaultBg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ColorSurface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emoji icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title + meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = meeting.title,
                style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color    = ColorTextSlate900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (meeting.durationSeconds > 0) {
                    Text(formatDuration(meeting.durationSeconds), style = MaterialTheme.typography.labelSmall, color = ColorTextSlate400)
                    Text("•", style = MaterialTheme.typography.labelSmall, color = ColorTextSlate400)
                }
                Text(formatDateFull(meeting.startTime), style = MaterialTheme.typography.labelSmall, color = ColorTextSlate400)
            }
        }

        // Chevron
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint     = ColorTextSlate400,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun defaultStyle(meetingId: Long): Pair<String, String> {
    return when ((meetingId % 3).toInt()) {
        0    -> "🎙" to "#D6EAF8"
        1    -> "📝" to "#D5F5E3"
        else -> "💬" to "#E8D5F5"
    }
}

private fun parseBgColor(hex: String?): Color {
    if (hex == null) return Color(0xFFE8EAF0)
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color(0xFFE8EAF0) }
}

@Composable
private fun SwipeDeleteBg(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE74C3C).copy(alpha = (progress * 0.8f).coerceIn(0f, 0.8f)))
            .padding(end = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint     = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text("DELETE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp), color = Color.White)
        }
    }
}

private val dateFormatFull = java.text.SimpleDateFormat("MMM d, yyyy • HH:mm", java.util.Locale.getDefault())
private fun formatDateFull(epochMs: Long): String = dateFormatFull.format(java.util.Date(epochMs))
