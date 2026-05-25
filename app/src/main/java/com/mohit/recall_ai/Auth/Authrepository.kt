package com.mohit.recall_ai.Auth


import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

// ── Auth result sealed class ────────────────────────────────────────────────

sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String, val cause: Exception? = null) : AuthResult()
    object Loading : AuthResult()
}

// ── Repository ──────────────────────────────────────────────────────────────

/**
 * Single source of truth for all Firebase Auth operations.
 *
 * Handles:
 *   - Google Sign-In (primary flow)
 *   - Email + Password Sign-In (secondary flow)
 *   - Email + Password Sign-Up
 *   - Sign-Out
 *   - Auth state observation
 *
 * The [googleSignInClient] is used to build the Google Sign-In Intent that is
 * launched from the UI via ActivityResultContracts. The result token is handed
 * back here via [signInWithGoogleToken].
 *
 * ── Web Client ID ───────────────────────────────────────────────────────────
 * The WEB_CLIENT_ID must match the OAuth 2.0 Web Client ID in your Firebase
 * project → Authentication → Sign-In method → Google → Web SDK configuration.
 * It is also found in your google-services.json under
 * client[0].oauth_client[].client_id where client_type == 3.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth = Firebase.auth
) {


    // Replace this with your actual Web Client ID from google-services.json
    // Look for: client[0].oauth_client[].client_id where client_type == 3
    private val webClientId: String by lazy {
        // Reads from string resource — set it in res/values/strings.xml:
        // <string name="default_web_client_id">YOUR_WEB_CLIENT_ID</string>
        // Firebase gradle plugin auto-generates this from google-services.json
        context.getString(
            context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
        )
    }

    /** Pre-configured GoogleSignInClient — call [getGoogleSignInIntent] to get the Intent */
    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)   // required for Firebase
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // ── Current user ────────────────────────────────────────────────────────

    /** Null when signed out, non-null when a session is active */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /** True if a user is currently signed in */
    val isSignedIn: Boolean
        get() = firebaseAuth.currentUser != null

    // ── Auth state stream ───────────────────────────────────────────────────

    /**
     * Cold flow that emits the current [FirebaseUser] whenever auth state changes.
     * Emits null when the user signs out. Never throws — errors are swallowed and
     * logged because an auth state listener failure is not recoverable.
     *
     * Collect this in a ViewModel tied to the app's lifecycle so it stays active
     * across screen transitions.
     */
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
            Log.d(TAG, "Auth state changed: user=${auth.currentUser?.email ?: "null"}")
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    // ── Google Sign-In ──────────────────────────────────────────────────────

    /**
     * Returns the Intent to launch with ActivityResultContracts.StartActivityForResult.
     * On result, extract the account and pass the ID token to [signInWithGoogleToken].
     *
     * Usage in Compose:
     *   val launcher = rememberLauncherForActivityResult(
     *       ActivityResultContracts.StartActivityForResult()
     *   ) { result ->
     *       val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(...)
     *       viewModel.handleGoogleResult(account.idToken)
     *   }
     *   launcher.launch(repository.googleSignInClient.signInIntent)
     */
    fun getGoogleSignInIntent() = googleSignInClient.signInIntent

    /**
     * Exchanges a Google ID token for a Firebase credential and signs in.
     * Call this after a successful Google Sign-In Activity result.
     *
     * @param idToken  The ID token from GoogleSignInAccount.idToken
     */
    suspend fun signInWithGoogleToken(idToken: String): AuthResult {
        return try {
            Log.d(TAG, "Signing in with Google token…")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user
                ?: return AuthResult.Error("Google sign-in returned no user")

            Log.i(TAG, "Google sign-in success: uid=${user.uid} email=${user.email}")
            AuthResult.Success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed: ${e.message}", e)
            AuthResult.Error(
                message = friendlyErrorMessage(e),
                cause   = e
            )
        }
    }

    // ── Email / Password ────────────────────────────────────────────────────

    /**
     * Signs in with email and password.
     * Emits Loading → Success or Error.
     */
    fun signInWithEmail(email: String, password: String): Flow<AuthResult> = flow {
        emit(AuthResult.Loading)
        try {
            Log.d(TAG, "Signing in with email: $email")
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
                ?: throw Exception("Sign-in returned no user")

            Log.i(TAG, "Email sign-in success: uid=${user.uid}")
            emit(AuthResult.Success(user))
        } catch (e: Exception) {
            Log.e(TAG, "Email sign-in failed: ${e.message}", e)
            emit(AuthResult.Error(friendlyErrorMessage(e), e))
        }
    }

    /**
     * Creates a new account with email and password.
     * Emits Loading → Success or Error.
     */
    fun createAccountWithEmail(email: String, password: String): Flow<AuthResult> = flow {
        emit(AuthResult.Loading)
        try {
            Log.d(TAG, "Creating account for: $email")
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
                ?: throw Exception("Account creation returned no user")

            Log.i(TAG, "Account created: uid=${user.uid}")
            emit(AuthResult.Success(user))
        } catch (e: Exception) {
            Log.e(TAG, "Account creation failed: ${e.message}", e)
            emit(AuthResult.Error(friendlyErrorMessage(e), e))
        }
    }

    // ── Sign-Out ────────────────────────────────────────────────────────────

    /**
     * Signs out from both Firebase and Google (clears the Google account
     * selection so the picker is shown again on next sign-in).
     */
    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
            firebaseAuth.signOut()
            Log.i(TAG, "Signed out successfully")
        } catch (e: Exception) {
            // Sign-out errors are non-fatal — user is still signed out of Firebase
            Log.w(TAG, "Google sign-out error (non-fatal): ${e.message}")
            firebaseAuth.signOut()  // ensure Firebase is signed out regardless
        }
    }

    // ── Delete Account ──────────────────────────────────────────────────────

    /**
     * Permanently deletes the current Firebase user account.
     *
     * This is a destructive, irreversible operation. Firebase requires a
     * "recent" sign-in; if the token is stale the call throws
     * FirebaseAuthRecentLoginRequiredException and the user must
     * re-authenticate first. The caller should handle that error.
     */
    suspend fun deleteAccount(): AuthResult {
        return try {
            val user = firebaseAuth.currentUser
                ?: return AuthResult.Error("No user is currently signed in.")

            Log.d(TAG, "Deleting account: uid=${user.uid}")
            user.delete().await()

            // Clear Google session so the picker appears on next sign-in
            try { googleSignInClient.signOut().await() } catch (_: Exception) {}

            Log.i(TAG, "Account deleted successfully")
            AuthResult.Success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Account deletion failed: ${e.message}", e)
            AuthResult.Error(
                message = friendlyErrorMessage(e),
                cause   = e
            )
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Converts Firebase exceptions into human-readable strings.
     * Firebase error codes: https://firebase.google.com/docs/auth/admin/errors
     */
    private fun friendlyErrorMessage(e: Exception): String {
        val msg = e.message ?: return "An unexpected error occurred."
        return when {
            "INVALID_EMAIL"                in msg -> "Invalid email address."
            "EMAIL_NOT_FOUND"              in msg -> "No account found with this email."
            "WRONG_PASSWORD"              in msg -> "Incorrect password. Try again."
            "WEAK_PASSWORD"               in msg -> "Password must be at least 6 characters."
            "EMAIL_EXISTS"                in msg -> "An account already exists with this email."
            "TOO_MANY_ATTEMPTS_TRY_LATER" in msg -> "Too many attempts. Please try again later."
            "NETWORK_ERROR"               in msg -> "No internet connection. Please check your network."
            "USER_DISABLED"               in msg -> "This account has been disabled."
            "sign_in_failed"              in msg -> "Google sign-in was cancelled or failed."
            "12501"                       in msg -> "Google sign-in was cancelled."
            "7:"                          in msg -> "Network error. Please check your connection."
            else -> "Authentication failed. Please try again."
        }
    }
}