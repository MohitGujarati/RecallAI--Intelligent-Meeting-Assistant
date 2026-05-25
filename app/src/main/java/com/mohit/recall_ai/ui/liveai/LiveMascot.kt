package com.mohit.recall_ai.ui.liveai

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
import com.mohit.recall_ai.service.LiveAiState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// --- Mascot Colors ---
val BobBodyColors = Color(0xFF3F51B5)      // Indigo Blue
val BobBodyErrorColor = Color(0xFFD32F2F)  // Muted Red
val BobBodyActionColor = Color(0xFFFF8F00) // Amber — "working on it"
val BobOrbitDotColor = Color(0xFFFFB74D)   // Light amber for orbiting dots
val BobEyeColors = Color(0xFF2D2D2D)       // Soft black

@Composable
fun LiveMascot(
    state: LiveAiState,
    modifier: Modifier = Modifier,
    numEyes: Int = 2,
    numTentacles: Int = 4
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bobAnimations")

    // --- STATE-DRIVEN COLORS ---
    // Shifts to red on error, amber on action, pulses brighter when speaking/acting
    val isActing = state is LiveAiState.PerformingAction
    val colorPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state is LiveAiState.Speaking || isActing) 0.8f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActing) 300 else 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colorPulse"
    )

    val bodyColor by animateColorAsState(
        targetValue = when {
            state is LiveAiState.Error -> BobBodyErrorColor
            isActing -> BobBodyActionColor
            else -> BobBodyColors
        },
        animationSpec = tween(500),
        label = "bodyColor"
    )
    val finalBodyColor = bodyColor.copy(alpha = colorPulse)

    // --- STATE-DRIVEN FLOATING ---
    // Bobbing stops when listening (focused), speeds up when speaking
    val floatDuration = when (state) {
        is LiveAiState.Speaking -> 1000
        is LiveAiState.PerformingAction -> 600  // Quick, contained bobbing
        is LiveAiState.Listening -> 4000        // Very slow
        else -> 2500
    }
    val floatTarget = when (state) {
        is LiveAiState.Listening -> 2f           // Barely moves
        is LiveAiState.Speaking -> 15f           // Energetic
        is LiveAiState.PerformingAction -> 5f    // Tight, focused
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
        targetValue = when (state) {
            is LiveAiState.Speaking -> 1.08f
            is LiveAiState.PerformingAction -> 1.05f
            else -> 1.02f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (state) {
                    is LiveAiState.Speaking -> 600
                    is LiveAiState.PerformingAction -> 400
                    else -> 1800
                },
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // --- STATE-DRIVEN POSTURES (TENTACLES) ---
    val tentacleLift by animateFloatAsState(
        targetValue = when (state) {
            is LiveAiState.Listening -> -24f        // Tentacles high up
            is LiveAiState.PerformingAction -> -12f  // Slightly raised, alert
            is LiveAiState.Error -> 15f              // Dropped completely flat
            is LiveAiState.Connecting -> 5f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tentacleLift"
    )

    // --- STATE-DRIVEN EYES ---
    val targetEyeScale = when (state) {
        is LiveAiState.Listening -> 1.3f        // Wide awake/attentive
        is LiveAiState.PerformingAction -> 0.5f  // Squinting — concentration
        is LiveAiState.Connecting -> 0.4f        // Thinking/loading
        is LiveAiState.Error -> 0.1f             // Slits/Sad
        else -> 1.0f                             // Normal
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

    // --- PERFORMING-ACTION: Horizontal wobble ---
    val wobbleAmplitude by animateFloatAsState(
        targetValue = if (isActing) 8f else 0f,
        animationSpec = tween(300),
        label = "wobbleAmp"
    )
    val wobbleRaw by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(250, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble"
    )

    // --- PERFORMING-ACTION: Orbiting dots ---
    val orbitPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val orbitAlpha by animateFloatAsState(
        targetValue = if (isActing) 0.9f else 0f,
        animationSpec = tween(400),
        label = "orbitAlpha"
    )

    Canvas(modifier = modifier) {
        val centerX = (size.width / 2) + (wobbleRaw * wobbleAmplitude)
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

        // --- PERFORMING-ACTION: Orbiting dots ---
        if (orbitAlpha > 0.01f) {
            val orbitRadiusX = bodyW * 0.58f
            val orbitRadiusY = bodyH * 0.58f
            val numDots = 3
            for (i in 0 until numDots) {
                val angle = orbitPhase + (i * 2f * Math.PI.toFloat() / numDots)
                val dotX = centerX + orbitRadiusX * cos(angle)
                val dotY = centerY + orbitRadiusY * sin(angle)
                // Dots get smaller as they go "behind" (sin < 0 = top = far side)
                val depthScale = 0.6f + 0.4f * ((sin(angle) + 1f) / 2f)
                val dotRadius = unit * 0.22f * depthScale
                drawCircle(
                    color = BobOrbitDotColor.copy(alpha = orbitAlpha * depthScale),
                    radius = dotRadius,
                    center = Offset(dotX, dotY)
                )
            }
        }
    }
}