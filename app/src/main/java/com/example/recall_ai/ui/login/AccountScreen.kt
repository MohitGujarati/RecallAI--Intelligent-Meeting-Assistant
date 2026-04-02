package com.example.recall_ai.ui.login

import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recall_ai.Auth.AuthUiState
import com.example.recall_ai.Auth.AuthViewModel
import com.example.recall_ai.ui.theme.ColorBackground
import com.example.recall_ai.ui.theme.ColorBorder
import com.example.recall_ai.ui.theme.ColorError
import com.example.recall_ai.ui.theme.ColorNavy
import com.example.recall_ai.ui.theme.ColorOnBackground
import com.example.recall_ai.ui.theme.ColorOnSurfaceDim
import com.example.recall_ai.ui.theme.ColorSurface
import com.example.recall_ai.ui.theme.ColorSurfaceVariant
import com.example.recall_ai.ui.theme.ColorTextSlate400
import com.example.recall_ai.ui.theme.ColorTextSlate900
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════════
// ACCOUNT SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AccountScreen(
    onNavigateBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val isLoading = uiState is AuthUiState.Loading

    // Confirmation dialogs
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60); visible = true }

    // React to auth state → when user becomes signed-out, navigate to login
    LaunchedEffect(isSignedIn) {
        if (!isSignedIn) {
            onSignedOut()
        }
    }

    // Show errors
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Error) {
            Toast.makeText(context, (uiState as AuthUiState.Error).message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // User info
    val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Recall User"
    val email = currentUser?.email ?: "Guest account"
    val initials = displayName
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifEmpty { "R" }
    val isGuest = currentUser == null

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ColorBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top bar ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ColorNavy
                    )
                }
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = ColorNavy,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                // Invisible spacer so title stays centered
                Spacer(modifier = Modifier.size(48.dp))
            }

            // ── Profile avatar + name card ───────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -30 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar circle
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .shadow(
                                10.dp, CircleShape,
                                ambientColor = ColorNavy.copy(alpha = 0.2f)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ColorNavy, ColorNavy.copy(alpha = 0.7f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold, fontSize = 30.sp
                            ),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = ColorOnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorOnSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Account info card ────────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, 100)) + slideInVertically(tween(500, 100)) { 30 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ColorSurface)
                        .border(1.dp, ColorBorder, RoundedCornerShape(16.dp))
                        .padding(vertical = 8.dp)
                ) {
                    // Display Name
                    AccountInfoRow(
                        icon = Icons.Default.Person,
                        label = "Name",
                        value = displayName
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 56.dp),
                        color = ColorBorder,
                        thickness = 0.5.dp
                    )
                    // Email
                    AccountInfoRow(
                        icon = Icons.Default.Email,
                        label = "Email",
                        value = email
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Action buttons card ──────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, 200)) + slideInVertically(tween(600, 200)) { 30 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ColorSurface)
                        .border(1.dp, ColorBorder, RoundedCornerShape(16.dp))
                        .padding(vertical = 4.dp)
                ) {
                    // ── Log Out ──────────────────────────────────────────
                    AccountActionRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = "Log Out",
                        iconTint = ColorNavy,
                        labelColor = ColorOnBackground,
                        enabled = !isLoading && !isGuest,
                        onClick = { showLogoutDialog = true }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 56.dp),
                        color = ColorBorder,
                        thickness = 0.5.dp
                    )

                    // ── Delete Account ───────────────────────────────────
                    AccountActionRow(
                        icon = Icons.Default.DeleteForever,
                        label = "Delete Account",
                        iconTint = ColorError,
                        labelColor = ColorError,
                        enabled = !isLoading && !isGuest,
                        onClick = { showDeleteDialog = true }
                    )
                }
            }

            // Guest hint
            if (isGuest) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sign in to manage your account",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSlate400,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // Loading indicator
            if (isLoading) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterHorizontally),
                    color = ColorNavy,
                    strokeWidth = 2.5.dp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Footer ──────────────────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(700, 300))
            ) {
                Text(
                    text = "Recall AI • v1.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSlate400,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOGOUT CONFIRMATION DIALOG
    // ═══════════════════════════════════════════════════════════════════════

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = ColorSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Log Out",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ColorOnBackground
                )
            },
            text = {
                Text(
                    "Are you sure you want to log out? You'll need to sign in again to access your Recalls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorOnSurfaceDim
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.signOut()
                }) {
                    Text(
                        "Log Out",
                        fontWeight = FontWeight.SemiBold,
                        color = ColorError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        "Cancel",
                        fontWeight = FontWeight.Medium,
                        color = ColorOnSurfaceDim
                    )
                }
            }
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DELETE ACCOUNT CONFIRMATION DIALOG
    // ═══════════════════════════════════════════════════════════════════════

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = ColorSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Delete Account",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ColorError
                )
            },
            text = {
                Text(
                    "This action is permanent and cannot be undone. All your data, Recalls, and summaries will be permanently deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorOnSurfaceDim
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteAccount()
                }) {
                    Text(
                        "Delete Permanently",
                        fontWeight = FontWeight.Bold,
                        color = ColorError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        "Cancel",
                        fontWeight = FontWeight.Medium,
                        color = ColorOnSurfaceDim
                    )
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ACCOUNT INFO ROW
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AccountInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ColorSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = ColorNavy,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ColorTextSlate400
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = ColorTextSlate900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ACCOUNT ACTION ROW
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AccountActionRow(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    labelColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) labelColor else labelColor.copy(alpha = 0.4f),
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (enabled) ColorTextSlate400 else ColorTextSlate400.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}
