package com.example.recall_ai.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recall_ai.ui.theme.ColorBackground
import com.example.recall_ai.ui.theme.ColorBorder
import com.example.recall_ai.ui.theme.ColorNavy
import com.example.recall_ai.ui.theme.ColorOnBackground
import com.example.recall_ai.ui.theme.ColorOnSurfaceDim
import com.example.recall_ai.ui.theme.ColorSurface
import com.example.recall_ai.ui.theme.ColorSurfaceVariant
import com.example.recall_ai.ui.theme.ColorTextSlate400
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// SignupScreen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sign-up screen — lets a new user create an account.
 *
 * UI only: no ViewModel, no real auth. When the user taps "Create account",
 * [onSignUpSuccess] is called and the nav graph navigates to Dashboard,
 * clearing the back stack so the user cannot return here with Back.
 *
 * Fields:
 *   - Full name        (text,     required)
 *   - Email            (email,    required, must contain @)
 *   - Password         (password, required, min 6 chars, show/hide toggle)
 *   - Confirm password (password, required, must match password)
 *
 * @param onSignUpSuccess  Called when all fields are valid and user taps "Create account".
 * @param onLoginClick     Called when user taps "Sign in" — navigate back to LoginScreen.
 * @param onGuestClick     Called when user taps "Continue as guest" — skip auth entirely.
 */
@Composable
fun SignupScreen(
    onSignUpSuccess: () -> Unit,
    onLoginClick:    () -> Unit = {},
    onGuestClick:    () -> Unit = {}
) {
    // ── State ─────────────────────────────────────────────────────────────────
    var fullName            by remember { mutableStateOf("") }
    var email               by remember { mutableStateOf("") }
    var password            by remember { mutableStateOf("") }
    var confirmPassword     by remember { mutableStateOf("") }

    var passwordShown       by remember { mutableStateOf(false) }
    var confirmPasswordShown by remember { mutableStateOf(false) }

    var fullNameError       by remember { mutableStateOf("") }
    var emailError          by remember { mutableStateOf("") }
    var passwordError       by remember { mutableStateOf("") }
    var confirmPasswordError by remember { mutableStateOf("") }

    // Staggered entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        visible = true
    }

    val focusManager = LocalFocusManager.current
    val scrollState  = rememberScrollState()

    // ── Validation ─────────────────────────────────────────────────────────────
    fun validate(): Boolean {
        fullNameError = when {
            fullName.isBlank()  -> "Full name is required"
            fullName.length < 2 -> "Name must be at least 2 characters"
            else                -> ""
        }
        emailError = when {
            email.isBlank()          -> "Email is required"
            !email.contains("@")     -> "Enter a valid email address"
            !email.contains(".")     -> "Enter a valid email address"
            else                     -> ""
        }
        passwordError = when {
            password.isBlank()   -> "Password is required"
            password.length < 6  -> "Password must be at least 6 characters"
            else                 -> ""
        }
        confirmPasswordError = when {
            confirmPassword.isBlank()      -> "Please confirm your password"
            confirmPassword != password    -> "Passwords do not match"
            else                           -> ""
        }
        return fullNameError.isEmpty() && emailError.isEmpty() &&
                passwordError.isEmpty() && confirmPasswordError.isEmpty()
    }

    // ── Root surface ───────────────────────────────────────────────────────────
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = ColorBackground
    ) {
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

            Spacer(modifier = Modifier.height(40.dp))

            // ═════════════════════════════════════════════════════════════════
            // LOGO + BRANDING
            // ═════════════════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(400)) + slideInVertically(tween(400)) { -30 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(
                                elevation    = 8.dp,
                                shape        = CircleShape,
                                ambientColor = ColorNavy.copy(alpha = 0.2f)
                            )
                            .clip(CircleShape)
                            .background(ColorNavy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Mic,
                            contentDescription = "Recall logo",
                            tint               = Color.White,
                            modifier           = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text  = "RECALL",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            fontSize      = 28.sp
                        ),
                        color = ColorNavy
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text  = "Your AI meeting assistant",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorOnSurfaceDim
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ═════════════════════════════════════════════════════════════════
            // SIGNUP CARD
            // ═════════════════════════════════════════════════════════════════
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

                    // ── Card heading ──────────────────────────────────────────
                    // BUG FIX: was "Welcome back" (copy-pasted from LoginScreen)
                    Text(
                        text  = "Create your account",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = 22.sp
                        ),
                        color = ColorOnBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // BUG FIX: was "Sign in to access your recordings" (wrong screen)
                    Text(
                        text  = "Start recording smarter today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorOnSurfaceDim
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Full name ─────────────────────────────────────────────
                    FieldLabel(text = "Full name")
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = fullName,
                        onValueChange = { fullName = it; fullNameError = "" },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = {
                            Text(
                                "Your full name",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ColorTextSlate400
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint     = if (fullNameError.isEmpty()) ColorOnSurfaceDim
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        isError = fullNameError.isNotEmpty(),
                        supportingText = {
                            if (fullNameError.isNotEmpty())
                                Text(
                                    fullNameError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            keyboardType   = KeyboardType.Text,
                            imeAction      = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        singleLine = true,
                        shape      = RoundedCornerShape(12.dp),
                        colors     = recallTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Email ─────────────────────────────────────────────────
                    FieldLabel(text = "Email address")
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = email,
                        onValueChange = { email = it; emailError = "" },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = {
                            Text(
                                "you@example.com",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ColorTextSlate400
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Mail,
                                contentDescription = null,
                                tint     = if (emailError.isEmpty()) ColorOnSurfaceDim
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        isError = emailError.isNotEmpty(),
                        supportingText = {
                            if (emailError.isNotEmpty())
                                Text(
                                    emailError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction    = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        singleLine = true,
                        shape      = RoundedCornerShape(12.dp),
                        colors     = recallTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Password ──────────────────────────────────────────────
                    FieldLabel(text = "Password")
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it; passwordError = "" },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = {
                            Text(
                                "Min. 6 characters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ColorTextSlate400
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint     = if (passwordError.isEmpty()) ColorOnSurfaceDim
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordShown = !passwordShown }) {
                                Icon(
                                    imageVector        = if (passwordShown) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = if (passwordShown) "Hide password"
                                    else "Show password",
                                    tint               = ColorOnSurfaceDim,
                                    modifier           = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordShown) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        isError = passwordError.isNotEmpty(),
                        supportingText = {
                            if (passwordError.isNotEmpty())
                                Text(
                                    passwordError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        singleLine = true,
                        shape      = RoundedCornerShape(12.dp),
                        colors     = recallTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Confirm password ──────────────────────────────────────
                    FieldLabel(text = "Confirm password")
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = confirmPassword,
                        onValueChange = { confirmPassword = it; confirmPasswordError = "" },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = {
                            Text(
                                "Re-enter your password",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ColorTextSlate400
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint     = if (confirmPasswordError.isEmpty()) ColorOnSurfaceDim
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordShown = !confirmPasswordShown }) {
                                Icon(
                                    imageVector        = if (confirmPasswordShown) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = if (confirmPasswordShown) "Hide password"
                                    else "Show password",
                                    tint               = ColorOnSurfaceDim,
                                    modifier           = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordShown) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        isError = confirmPasswordError.isNotEmpty(),
                        supportingText = {
                            if (confirmPasswordError.isNotEmpty())
                                Text(
                                    confirmPasswordError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (validate()) onSignUpSuccess()
                            }
                        ),
                        singleLine = true,
                        shape      = RoundedCornerShape(12.dp),
                        colors     = recallTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Create account button ─────────────────────────────────
                    // BUG FIX: button text was "Sign in" — wrong action for a signup screen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation    = 6.dp,
                                shape        = RoundedCornerShape(12.dp),
                                ambientColor = ColorNavy.copy(alpha = 0.3f)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(ColorNavy)
                            .clickable {
                                focusManager.clearFocus()
                                if (validate()) onSignUpSuccess()
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "Create account",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 15.sp
                            ),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Continue as guest ─────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ColorSurfaceVariant)
                            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                            .clickable { onGuestClick() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "Continue as guest",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize   = 14.sp
                            ),
                            color = ColorOnBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ═════════════════════════════════════════════════════════════════
            // LOGIN PROMPT
            // BUG FIX: was "Don't have an account? Sign up" — wrong for a signup screen.
            // Corrected to "Already have an account? Sign in"
            // ═════════════════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(600, 200))
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "Already have an account?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorOnSurfaceDim
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text     = "Login in",
                        style    = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color    = ColorNavy,
                        modifier = Modifier.clickable { onLoginClick() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ═════════════════════════════════════════════════════════════════
            // FEATURE PILLS
            // ═════════════════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(700, 300))
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    FeaturePill(text = "AI transcription")
                    Spacer(modifier = Modifier.width(8.dp))
                    FeaturePill(text = "Smart summaries")
                    Spacer(modifier = Modifier.width(8.dp))
                    FeaturePill(text = "Chat with notes")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}