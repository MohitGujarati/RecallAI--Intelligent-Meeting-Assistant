package com.example.recall_ai.ui.meetingdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recall_ai.data.local.entity.ChatMessage
import com.example.recall_ai.ui.theme.*

@Composable
fun ChatTabContent(
    messages:            List<ChatMessage>,
    isAiTyping:          Boolean,
    onSend:              (String) -> Unit,
    onLiveAiClick:       () -> Unit,
    isAiSpeaking:        Boolean,
    isRecordingActive:   Boolean,
    onStopSpeakingClick: () -> Unit,
    modifier:            Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isAiTyping) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    Column(modifier = modifier.fillMaxSize().background(ColorSurfaceVariant)) {
        LazyColumn(
            state               = listState,
            modifier            = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding      = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message -> MessageBubble(message) }
            if (isAiTyping) item { TypingIndicator() }
        }

        Box(modifier = Modifier.fillMaxWidth().background(ColorSurface).padding(top = 1.dp)) {
            ChatInputBar(
                text         = text,
                onTextChange = { text = it },
                onSend       = {
                    if (text.isNotBlank()) { onSend(text); text = "" }
                },
                onLiveAiClick = onLiveAiClick
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser    = message.isUser
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor   = if (isUser) ColorNavy else ColorSurface
    val textColor = if (isUser) Color.White else ColorTextSlate900
    val shape     = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    else        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .shadow(if (isUser) 2.dp else 1.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f))
                .clip(shape)
                .background(bgColor)
                .border(
                    width = if (isUser) 0.dp else 0.5.dp,
                    color = if (isUser) Color.Transparent else ColorBorder,
                    shape = shape
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(message.text, color = textColor, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun TypingIndicator() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(ColorSurface)
                .border(0.5.dp, ColorBorder, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("AI is typing...", color = ColorOnSurfaceDim, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FIX B2: The original ChatInputBar had TWO OutlinedTextFields and TWO send
// IconButtons. The second copy was inadvertently left after the mic button was
// inserted. This caused the bottom bar to render two text fields side-by-side
// and broke the layout entirely.
//
// Correct layout: [ OutlinedTextField (weight=1) ] [ Send ] [ MicLiveAI ]
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChatInputBar(
    text:          String,
    onTextChange:  (String) -> Unit,
    onSend:        () -> Unit,
    onLiveAiClick: () -> Unit
) {
    Surface(color = ColorSurface, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Single text field (the duplicate has been removed)
            OutlinedTextField(
                value           = text,
                onValueChange   = onTextChange,
                placeholder     = {
                    Text(
                        "Ask regarding this memory…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorTextSlate400
                    )
                },
                modifier        = Modifier.weight(1f).heightIn(min = 48.dp, max = 120.dp),
                shape           = RoundedCornerShape(24.dp),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = ColorNavy.copy(alpha = 0.5f),
                    unfocusedBorderColor    = ColorBorder,
                    focusedContainerColor   = ColorSurface,
                    unfocusedContainerColor = ColorSurface,
                    cursorColor             = ColorNavy
                ),
                maxLines        = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            Spacer(Modifier.width(8.dp))

            // Send button
            IconButton(
                onClick  = onSend,
                enabled  = text.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (text.isNotBlank()) ColorNavy else ColorSurfaceVariant)
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint               = if (text.isNotBlank()) Color.White else ColorTextSlate400,
                    modifier           = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // Live AI mic button — navigates to the Live AI voice session screen
            IconButton(
                onClick  = onLiveAiClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(ColorSurfaceVariant)
            ) {
                Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = "Start Live AI Voice Session",
                    tint               = ColorNavy
                )
            }
        }
    }
}