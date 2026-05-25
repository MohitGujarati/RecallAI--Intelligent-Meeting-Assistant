package com.mohit.recall_ai.Auth


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AuthViewModel"

// ── UI state ────────────────────────────────────────────────────────────────

sealed class AuthUiState {
    /** No auth attempt in progress */
    object Idle : AuthUiState()

    /** Auth call in flight — show spinner */
    object Loading : AuthUiState()

    /** Successfully signed in */
    data class Success(val user: FirebaseUser) : AuthUiState()

    /** Something went wrong — show this message */
    data class Error(val message: String) : AuthUiState()
}

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    // ── Reactive auth state (drives navigation) ──────────────────────────────

    /**
     * Emits the current [FirebaseUser] in real time.
     * - Non-null  → user is signed in → navigate to Dashboard
     * - Null      → user is signed out → show Login screen
     *
     * Collected in AppNavigation to decide the start destination.
     */
    val currentUser: StateFlow<FirebaseUser?> = authRepository.authStateFlow
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = authRepository.currentUser
        )

    /** True if a user is currently logged in — used to skip the login screen */
    val isSignedIn: StateFlow<Boolean> = currentUser
        .map { it != null }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = authRepository.isSignedIn
        )

    // ── Per-action UI state (drives loading indicator & error toasts) ────────

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // ── Google Sign-In ───────────────────────────────────────────────────────

    /**
     * The Intent to launch for Google Sign-In.
     * Pass this to the ActivityResultLauncher in your Composable:
     *
     *   val launcher = rememberLauncherForActivityResult(
     *       ActivityResultContracts.StartActivityForResult()
     *   ) { result -> viewModel.handleGoogleSignInResult(result.data) }
     *
     *   launcher.launch(viewModel.googleSignInIntent)
     */
    val googleSignInIntent get() = authRepository.getGoogleSignInIntent()

    /**
     * Called with the Intent returned from the Google Sign-In Activity.
     * Extracts the ID token and exchanges it for a Firebase credential.
     */
    fun handleGoogleSignInResult(data: android.content.Intent?) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val task    = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                    ?: run {
                        _uiState.value = AuthUiState.Error("Google sign-in failed: no ID token received.")
                        return@launch
                    }

                when (val result = authRepository.signInWithGoogleToken(idToken)) {
                    is AuthResult.Success -> {
                        Log.i(TAG, "Google sign-in complete: ${result.user.email}")
                        _uiState.value = AuthUiState.Success(result.user)
                    }
                    is AuthResult.Error -> {
                        Log.e(TAG, "Google token exchange failed: ${result.message}")
                        _uiState.value = AuthUiState.Error(result.message)
                    }
                    else -> Unit
                }
            } catch (e: ApiException) {
                // Error code 12501 = user cancelled the picker — don't show an error toast
                if (e.statusCode == 12501) {
                    Log.d(TAG, "Google sign-in cancelled by user")
                    _uiState.value = AuthUiState.Idle
                } else {
                    Log.e(TAG, "Google sign-in ApiException: code=${e.statusCode}", e)
                    _uiState.value = AuthUiState.Error(
                        "Google sign-in failed (${e.statusCode}). Please try again."
                    )
                }
            }
        }
    }

    // ── Email / Password Sign-In ─────────────────────────────────────────────

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            authRepository.signInWithEmail(email, password)
                .collect { result ->
                    _uiState.value = when (result) {
                        is AuthResult.Loading -> AuthUiState.Loading
                        is AuthResult.Success -> AuthUiState.Success(result.user)
                        is AuthResult.Error   -> AuthUiState.Error(result.message)
                    }
                }
        }
    }

    // ── Email / Password Sign-Up ─────────────────────────────────────────────

    fun createAccount(email: String, password: String) {
        viewModelScope.launch {
            authRepository.createAccountWithEmail(email, password)
                .collect { result ->
                    _uiState.value = when (result) {
                        is AuthResult.Loading -> AuthUiState.Loading
                        is AuthResult.Success -> AuthUiState.Success(result.user)
                        is AuthResult.Error   -> AuthUiState.Error(result.message)
                    }
                }
        }
    }

    // ── Sign-Out ─────────────────────────────────────────────────────────────

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AuthUiState.Idle
            Log.i(TAG, "User signed out")
        }
    }

    // ── Delete Account ───────────────────────────────────────────────────────

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.deleteAccount()) {
                is AuthResult.Success -> {
                    Log.i(TAG, "Account deleted")
                    _uiState.value = AuthUiState.Idle
                }
                is AuthResult.Error -> {
                    Log.e(TAG, "Account deletion failed: ${result.message}")
                    _uiState.value = AuthUiState.Error(result.message)
                }
                else -> Unit
            }
        }
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    /** Clear the error state after it has been shown to the user */
    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }
}