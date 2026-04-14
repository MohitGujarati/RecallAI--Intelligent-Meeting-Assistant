package com.example.recall_ai.ui.meetingdetail

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recall_ai.R
import com.example.recall_ai.data.local.entity.ChatMessage
import com.example.recall_ai.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════════
//  Brand Palette - Navy & Indigo Mascot
// ═══════════════════════════════════════════════════════════════════════════════

// Mascot Palette (Indigo-Violet)
val ColorMascot          = Color(0xFF585EB7)
val ColorMascotMid       = Color(0xFF7A7FD1)   // Lighter for gradients
val ColorMascotSubtle    = Color(0xFFEDEEF8)   // Very light tint for inline code backgrounds

// App Theme (Navy Blue)
val ColorNavy            = Color(0xFF0F172A)   // Deep slate navy for primary text, user icons, structure
val ColorNavyMuted       = Color(0xFF475569)   // Muted slate for secondary text
val ColorNavyBorder      = Color(0xFFCBD5E1)   // Light slate for borders

// Canvas / Surfaces (Crisp cool tones)
val ColorCanvas          = Color(0xFFF8FAFC)   // Global app background (cool icy white)
val ColorAiRowBg         = Color(0xFFF1F5F9)   // AI rows (slightly deeper cool slate)
val ColorUserRowBg       = Color(0xFFFFFFFF)   // User rows (pure white)

// Typography
val ColorTextPrimary     = ColorNavy
val ColorTextSecondary   = ColorNavyMuted
val ColorTextMuted       = Color(0xFF94A3B8)   // Placeholders

// Code surfaces
val ColorCodeBg          = ColorMascotSubtle
val ColorCodeBlockBg     = ColorNavy           // Deep navy for code blocks
val ColorCodeBlockText   = Color(0xFFE2E8F0)
val ColorCodeLabel       = Color(0xFF94A3B8)
val ColorDividerTone     = Color(0xFFE2E8F0)

// ═══════════════════════════════════════════════════════════════════════════════
//  Markdown Block Model
// ═══════════════════════════════════════════════════════════════════════════════

private sealed class Md {
    data class Heading(val level: Int, val text: String) : Md()
    data class Paragraph(val text: String) : Md()
    data class BulletList(val items: List<String>) : Md()
    data class NumberedList(val items: List<String>) : Md()
    data class CodeBlock(val language: String, val code: String) : Md()
    data class Blockquote(val text: String) : Md()
    object Rule : Md()
    object Gap : Md()
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Markdown Block Parser
// ═══════════════════════════════════════════════════════════════════════════════

private val reBullet    = Regex("""^[-*•]\s+(.+)$""")
private val reNumbered  = Regex("""^\d+\.\s+(.+)$""")
private val reHeading   = Regex("""^(#{1,3})\s+(.+)$""")
private val reRule      = Regex("""^[-*_]{3,}\s*$""")
private val reCodeFence = Regex("""^```(.*)$""")
private val reBlockquote= Regex("""^>\s?(.*)$""")

private fun parseMarkdownBlocks(raw: String): List<Md> {
    val out   = mutableListOf<Md>()
    val lines = raw.lines()
    var i     = 0

    fun last() = out.lastOrNull()

    while (i < lines.size) {
        val line = lines[i]

        // ── Fenced code block ────────────────────────────────────────────────
        val fenceMatch = reCodeFence.matchEntire(line.trimStart())
        if (fenceMatch != null) {
            val lang      = fenceMatch.groupValues[1].trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !reCodeFence.containsMatchIn(lines[i].trimStart())) {
                codeLines.add(lines[i])
                i++
            }
            i++ // closing ```
            out += Md.CodeBlock(lang, codeLines.joinToString("\n"))
            continue
        }

        // ── Heading ──────────────────────────────────────────────────────────
        val headMatch = reHeading.matchEntire(line)
        if (headMatch != null) {
            out += Md.Heading(headMatch.groupValues[1].length, headMatch.groupValues[2])
            i++
            continue
        }

        // ── Horizontal rule ──────────────────────────────────────────────────
        if (reRule.matches(line.trim())) {
            out += Md.Rule
            i++
            continue
        }

        // ── Blockquote ───────────────────────────────────────────────────────
        if (reBlockquote.containsMatchIn(line)) {
            val qLines = mutableListOf<String>()
            while (i < lines.size && reBlockquote.containsMatchIn(lines[i])) {
                qLines += reBlockquote.find(lines[i])!!.groupValues[1]
                i++
            }
            out += Md.Blockquote(qLines.joinToString("\n"))
            continue
        }

        // ── Bullet list ──────────────────────────────────────────────────────
        if (reBullet.containsMatchIn(line)) {
            val items = mutableListOf<String>()
            while (i < lines.size && reBullet.containsMatchIn(lines[i])) {
                items += reBullet.find(lines[i])!!.groupValues[1]
                i++
            }
            out += Md.BulletList(items)
            continue
        }

        // ── Numbered list ────────────────────────────────────────────────────
        if (reNumbered.containsMatchIn(line)) {
            val items = mutableListOf<String>()
            while (i < lines.size && reNumbered.containsMatchIn(lines[i])) {
                items += reNumbered.find(lines[i])!!.groupValues[1]
                i++
            }
            out += Md.NumberedList(items)
            continue
        }

        // ── Blank line ───────────────────────────────────────────────────────
        if (line.isBlank()) {
            if (last() !is Md.Gap) out += Md.Gap
            i++
            continue
        }

        // ── Paragraph (greedy: collect consecutive "plain" lines) ────────────
        val paraLines = mutableListOf<String>()
        while (i < lines.size) {
            val l = lines[i]
            if (l.isBlank()
                || reCodeFence.containsMatchIn(l.trimStart())
                || reHeading.containsMatchIn(l)
                || reRule.matches(l.trim())
                || reBullet.containsMatchIn(l)
                || reNumbered.containsMatchIn(l)
                || reBlockquote.containsMatchIn(l)) break
            paraLines += l
            i++
        }
        if (paraLines.isNotEmpty()) out += Md.Paragraph(paraLines.joinToString(" "))
    }

    // Strip trailing gap
    return out.dropLastWhile { it is Md.Gap }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Inline Markdown → AnnotatedString
// ═══════════════════════════════════════════════════════════════════════════════

private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // **bold** / __bold__
            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val m = text.substring(i, i + 2)
                val e = text.indexOf(m, i + 2)
                if (e != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(i + 2, e))
                    }
                    i = e + 2
                } else { append(text[i]); i++ }
            }
            // ~~strikethrough~~
            text.startsWith("~~", i) -> {
                val e = text.indexOf("~~", i + 2)
                if (e != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough,
                        color = ColorTextSecondary)) {
                        append(text.substring(i + 2, e))
                    }
                    i = e + 2
                } else { append(text[i]); i++ }
            }
            // *italic* / _italic_  (not preceded by another * or _)
            (text[i] == '*' || text[i] == '_') &&
                    !text.startsWith("**", i) && !text.startsWith("__", i) -> {
                val m = text[i]
                val e = text.indexOf(m, i + 1)
                if (e != -1 && !text.startsWith("$m$m", e)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, e))
                    }
                    i = e + 1
                } else { append(text[i]); i++ }
            }
            // `inline code`
            text[i] == '`' -> {
                val e = text.indexOf('`', i + 1)
                if (e != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = ColorCodeBg,
                            color      = ColorMascot,
                            fontSize   = 13.sp
                        )
                    ) { append("\u202F${text.substring(i + 1, e)}\u202F") } // narrow-space padding
                    i = e + 1
                } else { append(text[i]); i++ }
            }
            // [link text](url) — render label only, styled as link
            text[i] == '[' -> {
                val cb = text.indexOf(']', i + 1)
                if (cb != -1 && cb + 1 < text.length && text[cb + 1] == '(') {
                    val cp = text.indexOf(')', cb + 2)
                    if (cp != -1) {
                        withStyle(SpanStyle(color = ColorMascot,
                            textDecoration = TextDecoration.Underline)) {
                            append(text.substring(i + 1, cb))
                        }
                        i = cp + 1
                    } else { append(text[i]); i++ }
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Markdown Composables
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MarkdownContent(raw: String) {
    val blocks = remember(raw) { parseMarkdownBlocks(raw) }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Md.Heading      -> MdHeading(block)
                is Md.Paragraph    -> MdParagraph(block)
                is Md.BulletList   -> MdBulletList(block)
                is Md.NumberedList -> MdNumberedList(block)
                is Md.CodeBlock    -> MdCodeBlock(block)
                is Md.Blockquote   -> MdBlockquote(block)
                is Md.Rule         -> { Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = ColorDividerTone)
                    Spacer(Modifier.height(6.dp)) }
                is Md.Gap          -> Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun MdHeading(block: Md.Heading) {
    val (sz, fw) = when (block.level) {
        1    -> 19.sp to FontWeight.Bold
        2    -> 17.sp to FontWeight.Bold
        else -> 15.sp to FontWeight.SemiBold
    }
    Spacer(Modifier.height(if (block.level == 1) 8.dp else 4.dp))
    Text(
        text  = parseInline(block.text),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize   = sz,
            fontWeight = fw,
            lineHeight = (sz.value * 1.35).sp
        ),
        color = ColorTextPrimary
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun MdParagraph(block: Md.Paragraph) {
    Text(
        text  = parseInline(block.text),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize   = 15.sp,
            lineHeight = 24.sp
        ),
        color = ColorTextPrimary
    )
}

@Composable
private fun MdBulletList(block: Md.BulletList) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.items.forEach { item ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier          = Modifier.fillMaxWidth()
            ) {
                // Branded bullet dot
                Box(
                    modifier = Modifier
                        .padding(top = 9.dp, end = 10.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(ColorMascot)
                )
                Text(
                    text     = parseInline(item),
                    style    = MaterialTheme.typography.bodyMedium.copy(
                        fontSize   = 15.sp,
                        lineHeight = 24.sp
                    ),
                    color    = ColorTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MdNumberedList(block: Md.NumberedList) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.items.forEachIndexed { idx, item ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier          = Modifier.fillMaxWidth()
            ) {
                Text(
                    text  = "${idx + 1}.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 24.sp
                    ),
                    color    = ColorMascot,
                    modifier = Modifier
                        .widthIn(min = 26.dp)
                        .padding(end = 6.dp)
                )
                Text(
                    text     = parseInline(item),
                    style    = MaterialTheme.typography.bodyMedium.copy(
                        fontSize   = 15.sp,
                        lineHeight = 24.sp
                    ),
                    color    = ColorTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MdCodeBlock(block: Md.CodeBlock) {
    Surface(
        shape  = RoundedCornerShape(10.dp),
        color  = ColorCodeBlockBg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            if (block.language.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text  = block.language.lowercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily    = FontFamily.Monospace,
                            fontSize      = 11.sp,
                            letterSpacing = 0.4.sp
                        ),
                        color = ColorCodeLabel
                    )
                    // subtle mascot accent line
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(ColorMascot.copy(alpha = 0.5f))
                    )
                }
                HorizontalDivider(
                    color     = Color.White.copy(alpha = 0.06f),
                    thickness = 0.5.dp
                )
            }
            Text(
                text  = block.code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 13.sp,
                    lineHeight = 20.sp
                ),
                color    = ColorCodeBlockText,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun MdBlockquote(block: Md.Blockquote) {
    val barColor = ColorNavy.copy(alpha = 0.45f)
    Text(
        text  = parseInline(block.text),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontStyle  = FontStyle.Italic,
            fontSize   = 15.sp,
            lineHeight = 24.sp
        ),
        color    = ColorTextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color   = barColor,
                    topLeft = Offset.Zero,
                    size    = Size(3.dp.toPx(), size.height)
                )
            }
            .padding(start = 14.dp, top = 2.dp, bottom = 2.dp)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Root Composable
// ═══════════════════════════════════════════════════════════════════════════════

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
        val target = if (isAiTyping) messages.size else (messages.size - 1).coerceAtLeast(0)
        if (messages.isNotEmpty() || isAiTyping) listState.animateScrollToItem(target)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ColorCanvas,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = ColorDividerTone, thickness = 1.dp)
                ChatInputBar(
                    text          = text,
                    onTextChange  = { text = it },
                    onSend        = { if (text.isNotBlank()) { onSend(text); text = "" } },
                    onLiveAiClick = onLiveAiClick
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state               = listState,
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding      = PaddingValues(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.Top
        ) {
            items(messages, key = { it.id }) { message ->
                if (message.text.isNotBlank()) MessageRow(message)
            }
            if (isAiTyping) item { TypingRow() }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Message Row
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MessageRow(message: ChatMessage) {
    val isUser = message.isUser
    val rowBg  = if (isUser) ColorUserRowBg else ColorAiRowBg

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            if (isUser) UserAvatar() else AiAvatar()

            Spacer(Modifier.width(13.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                // Sender label
                Text(
                    text  = if (isUser) "You" else "Super Bob",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 11.sp,
                        letterSpacing = 0.4.sp
                    ),
                    color = if (isUser) ColorNavy else ColorMascot
                )

                Spacer(Modifier.height(7.dp))

                if (isUser) {
                    Text(
                        text  = parseInline(message.text),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize   = 15.sp,
                            lineHeight = 24.sp
                        ),
                        color = ColorTextPrimary
                    )
                } else {
                    MarkdownContent(raw = message.text)
                }
            }
        }

        HorizontalDivider(color = ColorDividerTone, thickness = 0.5.dp)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Avatars
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AiAvatar() {
    Box(
        modifier         = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(ColorMascot, ColorMascotMid))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter            = painterResource(id = R.drawable.ic_bob_icon),
            contentDescription = "AI",
            tint               = Color.Unspecified,
            modifier           = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun UserAvatar() {
    Box(
        modifier         = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(ColorNavy.copy(alpha = 0.08f))
            .border(1.5.dp, ColorNavyBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = Icons.Default.Person,
            contentDescription = "You",
            tint               = ColorNavy,
            modifier           = Modifier.size(18.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Typing Indicator
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TypingRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorAiRowBg)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top
        ) {
            AiAvatar()
            Spacer(Modifier.width(13.dp))
            Column {
                Text(
                    text  = "Super Bob",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 11.sp,
                        letterSpacing = 0.4.sp
                    ),
                    color = ColorMascot
                )
                Spacer(Modifier.height(10.dp))
                BouncingDots()
            }
        }
        HorizontalDivider(color = ColorDividerTone, thickness = 0.5.dp)
    }
}

@Composable
private fun BouncingDots() {
    val inf = rememberInfiniteTransition(label = "typing")

    @Composable
    fun dot(delay: Int): Float {
        val v by inf.animateFloat(
            initialValue  = 0f,
            targetValue   = -7f,
            animationSpec = infiniteRepeatable(
                animation  = tween(380, delayMillis = delay, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "d$delay"
        )
        return v
    }

    val y1 = dot(0); val y2 = dot(130); val y3 = dot(260)

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        listOf(y1 to ColorMascot, y2 to ColorMascotMid, y3 to ColorNavy).forEach { (yOff, col) ->
            Box(
                modifier = Modifier
                    .offset(y = yOff.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(col.copy(alpha = 0.7f))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Input Bar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatInputBar(
    text:          String,
    onTextChange:  (String) -> Unit,
    onSend:        () -> Unit,
    onLiveAiClick: () -> Unit
) {
    Surface(
        color           = ColorCanvas,
        shadowElevation = 0.dp,
        modifier        = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sendEnabled = text.isNotBlank()

            OutlinedTextField(
                value         = text,
                onValueChange = onTextChange,
                placeholder   = {
                    Text(
                        "Ask regarding this memory…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = ColorTextMuted
                    )
                },
                modifier      = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp, max = 120.dp),
                shape         = RoundedCornerShape(22.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = ColorMascot,
                    unfocusedBorderColor    = ColorNavyBorder,
                    focusedContainerColor   = ColorCanvas,
                    unfocusedContainerColor = ColorCanvas,
                    cursorColor             = ColorMascot
                ),
                textStyle     = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    color    = ColorTextPrimary
                ),
                maxLines        = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            Spacer(Modifier.width(9.dp))

            // Send — mascot gradient when active
            IconButton(
                onClick  = onSend,
                enabled  = sendEnabled,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (sendEnabled)
                            Brush.linearGradient(listOf(ColorMascot, ColorMascotMid))
                        else
                            Brush.linearGradient(listOf(
                                ColorNavyBorder, ColorNavyBorder))
                    )
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint               = if (sendEnabled) Color.White else ColorTextMuted,
                    modifier           = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(7.dp))

            // Mic — structural navy tint
            IconButton(
                onClick  = onLiveAiClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ColorNavy.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = "Live AI voice",
                    tint               = ColorNavy,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }
    }
}