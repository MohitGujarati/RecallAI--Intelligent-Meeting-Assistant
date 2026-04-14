package com.example.recall_ai.ui.liveai

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recall_ai.service.LiveAiState
import kotlin.math.cos
import kotlin.math.sin

// --- Light Theme Colors ---
val LightBackground = Color(0xFFF8F9FA) // Soft Material off-white
val TextPrimary = Color(0xFF1F1F1F)
val TextSecondary = Color(0xFF5E5E5E)

// Softer, pastel variants for the light theme aura
val WaveBlueLight = Color(0xFF7BAAF7)
val WavePurpleLight = Color(0xFFBA8DFA)
val WaveCyanLight = Color(0xFF70DDE6)

// Warm tones for PerformingAction aura
val WaveAmberLight = Color(0xFFFFA726)
val WaveGoldLight = Color(0xFFFFD54F)
val WaveOrangeLight = Color(0xFFFF8A65)

val ButtonLightSurface = Color(0xFFFFFFFF)
val ButtonBorderGray = Color(0xFFE0E0E0)
val ButtonRedLight = Color(0xFFEA4335) // Google standard red

@Composable
fun LiveAiScreen(
    meetingId: Long,
    onNavigateBack: () -> Unit,
    viewModel: LiveAiViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(meetingId) {
        if (uiState is LiveAiState.Idle) {
            viewModel.startSession(meetingId)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is LiveAiState.Stopped) {
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LightBackground,
        contentWindowInsets = WindowInsets.systemBars
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // --- TOP SECTION ---
            TopStatusBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                state = uiState
            )

            // --- CENTER/BOTTOM AURA ---
            FluidLightAuraIndicator(
                state = uiState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(450.dp)
                    .padding(bottom = 120.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(450.dp)
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                // Faint background aura to keep the premium feel
                FluidLightAuraIndicator(
                    state = uiState,
                    modifier = Modifier.fillMaxSize(),
                )

                // The extracted dynamic mascot component
                LiveMascot(
                    state = uiState,
                    modifier = Modifier
                        .size(250.dp)
                        .align(Alignment.Center) // Explicitly pins Bob to the dead center of the Box
                )
            }

            // --- BOTTOM CONTROLS ---
            val isSpeaking = uiState is LiveAiState.Speaking

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interrupt / Hold Button — stops AI speech and switches to listening
                PillButton(
                    icon = if (isSpeaking) Icons.Rounded.Mic else Icons.Rounded.Pause,
                    label = if (isSpeaking) "Interrupt" else "Hold",
                    backgroundColor = Color(0xFF1B2A4A),
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.interruptAi() }
                )

                // End Button
                PillButton(
                    icon = Icons.Rounded.Close,
                    label = "End",
                    backgroundColor = ButtonRedLight,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.stopSession()
                        onNavigateBack()
                    }
                )
            }
        }
    }
}

@Composable
fun TopStatusBar(modifier: Modifier = Modifier, state: LiveAiState) {
    val infiniteTransition = rememberInfiniteTransition(label = "liveDot")

    // Pulse animation for the "Live" dot
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = Ease),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Live Status Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(64.dp) // Fixed width to keep center text balanced
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ButtonRedLight.copy(alpha = if (state is LiveAiState.Error) 0f else dotAlpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "LIVE",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Center: Title & Status Text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Recall AI",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when (state) {
                    is LiveAiState.Connecting -> "Connecting..."
                    is LiveAiState.Listening -> "Listening..."
                    is LiveAiState.Speaking -> "Speaking..."
                    is LiveAiState.PerformingAction -> "Working..."
                    is LiveAiState.Error -> "Connection Lost"
                    else -> "Initializing..."
                },
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }

        // Right: Context Menu / Info
        Box(
            modifier = Modifier.width(64.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = "Session Info",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun FluidLightAuraIndicator(state: LiveAiState, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluidAura")

    val isActing = state is LiveAiState.PerformingAction
    val durationMillis = when (state) {
        is LiveAiState.PerformingAction -> 1200  // Fastest — urgent energy
        is LiveAiState.Speaking -> 2000
        is LiveAiState.Listening -> 4000
        else -> 6000
    }

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val baseAlpha by animateFloatAsState(
        targetValue = if (state is LiveAiState.Connecting) 0.4f else 0.85f,
        animationSpec = tween(800),
        label = "baseAlpha"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = w * 0.75f // Slightly larger radius for softer blending in light mode

        val center1 = Offset(
            x = w * 0.5f + sin(phase) * (w * 0.3f),
            y = h * 0.6f + cos(phase * 0.8f) * (h * 0.2f)
        )

        val center2 = Offset(
            x = w * 0.4f + cos(phase * 1.2f) * (w * 0.4f),
            y = h * 0.7f + sin(phase * 1.1f) * (h * 0.2f)
        )

        val center3 = Offset(
            x = w * 0.6f + sin(phase * 0.9f) * (w * 0.3f),
            y = h * 0.8f + cos(phase * 1.3f) * (h * 0.15f)
        )

        // For light mode, we use standard alpha blending (SrcOver) instead of Screen blending
        // to prevent the colors from blowing out to pure white.
        // Swap to warm amber/gold/orange tones when performing an action
        val color1 = if (isActing) WaveAmberLight else WaveBlueLight
        val color2 = if (isActing) WaveGoldLight else WavePurpleLight
        val color3 = if (isActing) WaveOrangeLight else WaveCyanLight

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color1.copy(alpha = 0.6f * baseAlpha), Color.Transparent),
                center = center1,
                radius = radius
            ),
            center = center1,
            radius = radius
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color2.copy(alpha = 0.5f * baseAlpha), Color.Transparent),
                center = center2,
                radius = radius * 0.9f
            ),
            center = center2,
            radius = radius * 0.9f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color3.copy(alpha = 0.5f * baseAlpha), Color.Transparent),
                center = center3,
                radius = radius * 0.8f
            ),
            center = center3,
            radius = radius * 0.8f
        )
    }
}

@Composable
fun PillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        shadowElevation = 4.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}