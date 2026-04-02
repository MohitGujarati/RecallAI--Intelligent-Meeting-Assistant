package com.example.recall_ai.ui.liveai

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.recall_ai.service.LiveAiState
import kotlin.math.abs

// --- Mascot Colors ---
val BobBodyColors = Color(0xFF3F51B5) // Indigo Blue
val BobBodyErrorColor = Color(0xFFD32F2F) // Muted Red
val BobEyeColors = Color(0xFF2D2D2D)  // Soft black

@Composable
fun LiveMascot(
    state: LiveAiState,
    modifier: Modifier = Modifier,
    numEyes: Int = 2,
    numTentacles: Int = 4
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bobAnimations")

    // --- STATE-DRIVEN COLORS ---
    // Shifts to red on error, pulses brighter when speaking
    val colorPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state is LiveAiState.Speaking) 0.8f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colorPulse"
    )

    val bodyColor by animateColorAsState(
        targetValue = if (state is LiveAiState.Error) BobBodyErrorColor else BobBodyColors,
        animationSpec = tween(500),
        label = "bodyColor"
    )
    val finalBodyColor = bodyColor.copy(alpha = colorPulse)

    // --- STATE-DRIVEN FLOATING ---
    // Bobbing stops when listening (focused), speeds up when speaking
    val floatDuration = when (state) {
        is LiveAiState.Speaking -> 1000
        is LiveAiState.Listening -> 4000 // Very slow
        else -> 2500
    }
    val floatTarget = when (state) {
        is LiveAiState.Listening -> 2f  // Barely moves
        is LiveAiState.Speaking -> 15f  // Energetic
        else -> 12f
    }

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -floatTarget,
        targetValue = floatTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(floatDuration, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // --- STATE-DRIVEN BREATHING ---
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state is LiveAiState.Speaking) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state is LiveAiState.Speaking) 600 else 1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // --- STATE-DRIVEN POSTURES (TENTACLES) ---
    val tentacleLift by animateFloatAsState(
        targetValue = when (state) {
            is LiveAiState.Listening -> -24f // Tentacles high up
            is LiveAiState.Error -> 15f      // Dropped completely flat
            is LiveAiState.Connecting -> 5f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tentacleLift"
    )

    // --- STATE-DRIVEN EYES ---
    val targetEyeScale = when (state) {
        is LiveAiState.Listening -> 1.3f   // Wide awake/attentive
        is LiveAiState.Connecting -> 0.4f  // Thinking/loading
        is LiveAiState.Error -> 0.1f       // Slits/Sad
        else -> 1.0f                       // Normal
    }

    val eyeStateScale by animateFloatAsState(
        targetValue = targetEyeScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "eyeStateScale"
    )

    val blinkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                1f at 3800
                0.1f at 3900
                1f at 4000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = (size.height / 2) + floatOffset

        val unit = size.width * 0.1f
        val cr = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        val headCr = CornerRadius(16.dp.toPx(), 16.dp.toPx())

        val bodyW = unit * 5.5f * breathScale
        val bodyH = unit * 4f

        val tentacleW = unit * 0.9f * (4f / numTentacles.coerceAtLeast(1).toFloat()).coerceAtMost(1.2f)
        val tentacleH = unit * 2.2f
        val eyeSize = unit * 0.6f * (2f / numEyes.coerceAtLeast(1).toFloat()).coerceAtMost(1.5f)

        // --- DRAW TENTACLES ---
        if (numTentacles > 0) {
            val startTentacleX = if (numTentacles == 1) centerX else centerX - (bodyW * 0.4f)
            val tentacleSpacingX = if (numTentacles > 1) (bodyW * 0.8f) / (numTentacles - 1) else 0f

            for (i in 0 until numTentacles) {
                val normalizedPos = if (numTentacles > 1) (i.toFloat() / (numTentacles - 1)) * 2f - 1f else 0f

                // If in Error state, no curl, just flat. Otherwise, outer tentacles curl higher.
                val liftMultiplier = if (state is LiveAiState.Error) 1f else 0.8f + 0.7f * abs(normalizedPos)
                val currentLift = tentacleLift * liftMultiplier
                val xPos = startTentacleX + (i * tentacleSpacingX)

                drawRoundRect(
                    color = finalBodyColor,
                    topLeft = Offset(xPos - (tentacleW / 2), centerY + (bodyH / 4) + currentLift),
                    size = Size(tentacleW, tentacleH),
                    cornerRadius = cr
                )
            }
        }

        // --- DRAW HEAD ---
        drawRoundRect(
            color = finalBodyColor,
            topLeft = Offset(centerX - (bodyW / 2), centerY - (bodyH / 2)),
            size = Size(bodyW, bodyH),
            cornerRadius = headCr
        )

        // --- DRAW EYES ---
        if (numEyes > 0) {
            val eyeCenterY = centerY + (unit * 0.2f)

            // Only blink if in Idle or Speaking. Connecting/Error hold their specific eye shapes.
            val shouldBlink = state is LiveAiState.Idle || state is LiveAiState.Speaking
            val actualEyeScale = if (shouldBlink) eyeStateScale * blinkScale else eyeStateScale

            val startEyeX = if (numEyes == 1) centerX else centerX - (bodyW * 0.3f)
            val eyeSpacingX = if (numEyes > 1) (bodyW * 0.6f) / (numEyes - 1) else 0f

            for (i in 0 until numEyes) {
                val xPos = startEyeX + (i * eyeSpacingX)

                drawRoundRect(
                    color = BobEyeColors,
                    topLeft = Offset(xPos - (eyeSize / 2), eyeCenterY - (eyeSize / 2 * actualEyeScale)),
                    size = Size(eyeSize, eyeSize * actualEyeScale),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
    }
}