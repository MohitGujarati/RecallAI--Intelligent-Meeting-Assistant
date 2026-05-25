package com.mohit.recall_ai.ui.login

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.recall_ai.Auth.AuthUiState
import com.mohit.recall_ai.Auth.AuthViewModel
import com.mohit.recall_ai.R
import com.mohit.recall_ai.ui.theme.ColorBackground
import com.mohit.recall_ai.ui.theme.ColorBorder
import com.mohit.recall_ai.ui.theme.ColorNavy
import com.mohit.recall_ai.ui.theme.ColorOnBackground
import com.mohit.recall_ai.ui.theme.ColorOnSurfaceDim
import com.mohit.recall_ai.ui.theme.ColorSurface
import com.mohit.recall_ai.ui.theme.ColorTextSlate400
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onGuestClick:   () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context     = LocalContext.current
    val scrollState = rememberScrollState()

    // ── Staggered entrance animations ─────────────────────────────────────
    var heroVisible    by remember { mutableStateOf(false) }
    var cardVisible    by remember { mutableStateOf(false) }
    var footerVisible  by remember { mutableStateOf(false) }

    // Mascot scale-in bounce
    val mascotScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(100)
        heroVisible = true
        mascotScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) { delay(300); cardVisible = true }
    LaunchedEffect(Unit) { delay(550); footerVisible = true }

    // ── Observe ViewModel state ────────────────────────────────────────────
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = uiState is AuthUiState.Loading

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.Success -> onLoginSuccess()
            is AuthUiState.Error   -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
            else -> Unit
        }
    }

    // ── Google Sign-In launcher ────────────────────────────────────────────
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    // ══════════════════════════════════════════════════════════════════════
    // UI
    // ══════════════════════════════════════════════════════════════════════
    Surface(modifier = Modifier.fillMaxSize(), color = ColorBackground) {

        // Subtle top gradient for depth
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ColorNavy.copy(alpha = 0.06f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(72.dp))

                // ── Hero: Mascot + Branding ──────────────────────────────
                AnimatedVisibility(
                    visible = heroVisible,
                    enter   = fadeIn(tween(500)) + slideInVertically(tween(500)) { -40 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Mascot with glow ring
                        Box(
                            modifier = Modifier.scale(mascotScale.value),
                            contentAlignment = Alignment.Center
                        ) {
                            // Outer glow ring
                            Box(
                                modifier = Modifier
                                    .size(112.dp)
                                    .background(
                                        ColorNavy.copy(alpha = 0.08f),
                                        CircleShape
                                    )
                            )
                            // Inner mascot circle
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .shadow(
                                        12.dp, CircleShape,
                                        ambientColor = ColorNavy.copy(alpha = 0.25f),
                                        spotColor = ColorNavy.copy(alpha = 0.15f)
                                    )
                                    .clip(CircleShape)
                                    .background(Color(0x2CE0DFE4)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_bob_icon),
                                    contentDescription = "Recall mascot",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .padding(2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            "RECALL",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight    = FontWeight.ExtraBold,
                                letterSpacing = 5.sp,
                                fontSize      = 32.sp
                            ),
                            color = ColorNavy
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Your AI meeting assistant",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ColorOnSurfaceDim
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))

                // ── Sign-in card ─────────────────────────────────────────
                AnimatedVisibility(
                    visible = cardVisible,
                    enter   = fadeIn(tween(450, 50)) + slideInVertically(tween(450, 50)) { 50 }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                8.dp, RoundedCornerShape(24.dp),
                                ambientColor = Color.Black.copy(alpha = 0.06f),
                                spotColor    = Color.Black.copy(alpha = 0.04f)
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .background(ColorSurface)
                            .padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        Text(
                            "Get Started",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize   = 24.sp
                            ),
                            color = ColorOnBackground
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Sign in to access your recordings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ColorOnSurfaceDim
                        )

                        Spacer(Modifier.height(28.dp))

                        // ── Google Sign-In (standard white branding) ─────
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    4.dp, RoundedCornerShape(14.dp),
                                    ambientColor = Color.Black.copy(alpha = 0.08f)
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .border(1.dp, ColorBorder, RoundedCornerShape(14.dp))
                                .clickable(enabled = !isLoading) {
                                    googleSignInLauncher.launch(viewModel.googleSignInIntent)
                                }
                                .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color       = ColorNavy,
                                    modifier    = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Image(
                                        painter            = painterResource(id = R.drawable.ic_google_icon),
                                        contentDescription = "Google logo",
                                        modifier           = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Continue with Google",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize   = 15.sp
                                        ),
                                        color = ColorOnBackground
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // ── "or" divider ─────────────────────────────────
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(
                                modifier  = Modifier.weight(1f),
                                color     = ColorBorder,
                                thickness = 0.5.dp
                            )
                            Text(
                                "  or  ",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorTextSlate400
                            )
                            HorizontalDivider(
                                modifier  = Modifier.weight(1f),
                                color     = ColorBorder,
                                thickness = 0.5.dp
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // ── Guest — lightweight text button ──────────────
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(
                                    enabled            = !isLoading,
                                    interactionSource  = remember { MutableInteractionSource() },
                                    indication         = null,
                                    onClick            = onGuestClick
                                )
                                .border(
                                    1.dp,
                                    ColorBorder.copy(alpha = 0.6f),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Continue as guest",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize   = 14.sp
                                ),
                                color = ColorOnSurfaceDim
                            )
                        }
                    }
                }

                Spacer(Modifier.height(36.dp))

                // ── Feature highlights ────────────────────────────────────
                AnimatedVisibility(
                    visible = footerVisible,
                    enter   = fadeIn(tween(400))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            FeaturePill("AI transcription")
                            Spacer(Modifier.width(8.dp))
                            FeaturePill("Smart summaries")
                            Spacer(Modifier.width(8.dp))
                            FeaturePill("Chat with notes")
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            "Your recordings stay private on your device",
                            style     = MaterialTheme.typography.labelSmall,
                            color     = ColorTextSlate400,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
