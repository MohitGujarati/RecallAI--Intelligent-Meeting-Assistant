package com.example.recall_ai.ui.login

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recall_ai.Auth.AuthUiState
import com.example.recall_ai.Auth.AuthViewModel
import com.example.recall_ai.R
import com.example.recall_ai.ui.theme.ColorBackground
import com.example.recall_ai.ui.theme.ColorBorder
import com.example.recall_ai.ui.theme.ColorNavy
import com.example.recall_ai.ui.theme.ColorOnBackground
import com.example.recall_ai.ui.theme.ColorOnSurfaceDim
import com.example.recall_ai.ui.theme.ColorSurface
import com.example.recall_ai.ui.theme.ColorSurfaceVariant
import com.example.recall_ai.ui.theme.ColorTextSlate400
import com.example.recall_ai.ui.theme.ColorTextSlate900
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onSignUpClick:  () -> Unit = {},
    onGuestClick:   () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState  = rememberScrollState()

    // ── Local field state ──────────────────────────────────────────────────
    var email         by remember { mutableStateOf("") }
    var password      by remember { mutableStateOf("") }
    var passwordShown by remember { mutableStateOf(false) }
    var emailError    by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    // ── Observe ViewModel state ────────────────────────────────────────────
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = uiState is AuthUiState.Loading

    // ── React to auth state changes ────────────────────────────────────────
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
        // Pass the entire result data to the ViewModel for token extraction
        viewModel.handleGoogleSignInResult(result.data)
    }

    // ── Validation ────────────────────────────────────────────────────────
    fun validate(): Boolean {
        emailError    = if (email.isBlank()) "Email is required"
        else if (!email.contains("@")) "Enter a valid email"
        else ""
        passwordError = if (password.isBlank()) "Password is required"
        else if (password.length < 6) "Password must be at least 6 characters"
        else ""
        return emailError.isEmpty() && passwordError.isEmpty()
    }

    // ══════════════════════════════════════════════════════════════════════
    // UI
    // ══════════════════════════════════════════════════════════════════════
    Surface(modifier = Modifier.fillMaxSize(), color = ColorBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            // ── Logo ─────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(400)) + slideInVertically(tween(400)) { -30 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(8.dp, CircleShape, ambientColor = ColorNavy.copy(alpha = 0.2f))
                            .clip(CircleShape)
                            .background(ColorNavy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "RECALL",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold, letterSpacing = 4.sp, fontSize = 28.sp
                        ),
                        color = ColorNavy
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Your AI meeting assistant", style = MaterialTheme.typography.bodyMedium, color = ColorOnSurfaceDim)
                }
            }

            Spacer(Modifier.height(44.dp))

            // ── Login card ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(500, 100)) + slideInVertically(tween(500, 100)) { 40 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(ColorSurface)
                        .border(1.dp, ColorBorder, RoundedCornerShape(20.dp))
                        .padding(24.dp)
                ) {
                    Text(
                        "Welcome back",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                        color = ColorOnBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Sign in to access your recordings", style = MaterialTheme.typography.bodyMedium, color = ColorOnSurfaceDim)

                    Spacer(Modifier.height(24.dp))

                    // ── Email ─────────────────────────────────────────────
                    FieldLabel("Email address")
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = email,
                        onValueChange = { email = it; emailError = "" },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = { Text("you@example.com", style = MaterialTheme.typography.bodyMedium, color = ColorTextSlate400) },
                        leadingIcon   = {
                            Icon(Icons.Default.Mail, null,
                                tint     = if (emailError.isEmpty()) ColorOnSurfaceDim else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp))
                        },
                        isError       = emailError.isNotEmpty(),
                        supportingText = { if (emailError.isNotEmpty()) Text(emailError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        singleLine = true,
                        shape      = RoundedCornerShape(12.dp),
                        colors     = recallTextFieldColors()
                    )

                    Spacer(Modifier.height(14.dp))

                    // ── Password ──────────────────────────────────────────
                    FieldLabel("Password")
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it; passwordError = "" },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = { Text("Enter your password", style = MaterialTheme.typography.bodyMedium, color = ColorTextSlate400) },
                        leadingIcon   = {
                            Icon(Icons.Default.Lock, null,
                                tint     = if (passwordError.isEmpty()) ColorOnSurfaceDim else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordShown = !passwordShown }) {
                                Icon(
                                    if (passwordShown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    if (passwordShown) "Hide password" else "Show password",
                                    tint = ColorOnSurfaceDim, modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordShown) VisualTransformation.None else PasswordVisualTransformation(),
                        isError       = passwordError.isNotEmpty(),
                        supportingText = { if (passwordError.isNotEmpty()) Text(passwordError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (validate()) viewModel.signInWithEmail(email, password)
                        }),
                        singleLine = true,
                        shape      = RoundedCornerShape(12.dp),
                        colors     = recallTextFieldColors()
                    )

                    // ── Forgot password ───────────────────────────────────
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "Forgot password?",
                            style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color    = ColorNavy,
                            modifier = Modifier.clickable { /* TODO: password reset */ }.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    // ── Sign-in button ────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(12.dp), ambientColor = ColorNavy.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isLoading) ColorNavy.copy(alpha = 0.7f) else ColorNavy)
                            .clickable(enabled = !isLoading) {
                                focusManager.clearFocus()
                                if (validate()) viewModel.signInWithEmail(email, password)
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Sign in", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = Color.White)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // ── Divider ───────────────────────────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = ColorBorder, thickness = 0.5.dp)
                        Text("  or continue with  ", style = MaterialTheme.typography.labelSmall, color = ColorTextSlate400)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = ColorBorder, thickness = 0.5.dp)
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Google Sign-In button ─────────────────────────────
                    SocialLoginButton(
                        text      = "Continue with Google",
                        iconResId = R.drawable.ic_launcher_foreground,  // replace with ic_google drawable
                        enabled   = !isLoading,
                        onClick   = {
                            // Launch the Google account picker
                            googleSignInLauncher.launch(viewModel.googleSignInIntent)
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    // ── Guest button ──────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ColorSurfaceVariant)
                            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                            .clickable(enabled = !isLoading, onClick = onGuestClick)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Continue as guest",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
                            color = ColorOnBackground
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Sign-up prompt ────────────────────────────────────────────
            AnimatedVisibility(visible = visible, enter = fadeIn(tween(600, 200))) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Don't have an account?", style = MaterialTheme.typography.bodyMedium, color = ColorOnSurfaceDim)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Sign up",
                        style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color    = ColorNavy,
                        modifier = Modifier.clickable(onClick = onSignUpClick)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Feature pills ─────────────────────────────────────────────
            AnimatedVisibility(visible = visible, enter = fadeIn(tween(700, 300))) {
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
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Social button (reusable) ─────────────────────────────────────────────────

@Composable
internal fun SocialLoginButton(
    text:      String,
    iconResId: Int,
    enabled:   Boolean = true,
    onClick:   () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter            = painterResource(id = iconResId),
            contentDescription = "$text icon",
            modifier           = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text  = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = if (enabled) ColorOnBackground else ColorOnSurfaceDim
        )
    }
}