package com.aistudio.youthverse.rvzkmq.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aistudio.youthverse.rvzkmq.data.repository.AuthRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _authNotice = MutableStateFlow<String?>(null)
    val authNotice: StateFlow<String?> = _authNotice.asStateFlow()

    private val _authLanguage = MutableStateFlow(AuthLanguage.EN)
    val authLanguage: StateFlow<AuthLanguage> = _authLanguage.asStateFlow()

    private val _passwordResetState = MutableStateFlow<PasswordResetState>(PasswordResetState.Idle)
    val passwordResetState: StateFlow<PasswordResetState> = _passwordResetState.asStateFlow()

    private val authOperationMutex = Mutex()
    private var lastSyncedUid: String? = null
    private var autoSignInAttempted = false

    init {
        observeAuthenticationState()
    }

    private fun observeAuthenticationState() {
        viewModelScope.launch {
            authRepository.observeAuthUser().collect { user ->
                // If an explicit interactive operation (signIn / signUp / signOut) is running,
                // let that operation finalize profile sync and state transitions without racing.
                if (authOperationMutex.isLocked) return@collect

                if (user == null) {
                    lastSyncedUid = null
                    if (_authState.value !is AuthState.Error) {
                        _authState.value = AuthState.SignedOut
                    }
                    return@collect
                }

                val currentState = _authState.value
                if (currentState is AuthState.SignedIn && currentState.user.uid == user.uid && currentState.profile != null) {
                    return@collect
                }
                if (lastSyncedUid == user.uid && currentState is AuthState.SignedIn) {
                    return@collect
                }

                authOperationMutex.withLock {
                    val activeUser = authRepository.currentUser
                    if (activeUser == null) {
                        lastSyncedUid = null
                        _authState.value = AuthState.SignedOut
                        return@withLock
                    }

                    if (_authState.value !is AuthState.SignedIn) {
                        _authState.value = AuthState.Loading
                    }
                    val syncResult = authRepository.syncProfileForCurrentUser(_authLanguage.value.code)
                    syncResult.fold(
                        onSuccess = { (syncedUser, profile) ->
                            lastSyncedUid = syncedUser.uid
                            _authState.value = AuthState.SignedIn(syncedUser, profile)
                        },
                        onFailure = { error ->
                            Log.w(TAG, "Profile sync warning during session restore: ${error.message}")
                            lastSyncedUid = activeUser.uid
                            _authState.value = AuthState.SignedIn(activeUser, null)
                        }
                    )
                }
            }
        }
    }

    fun setAuthLanguage(language: AuthLanguage) {
        _authLanguage.value = language
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.SignedOut
        }
    }

    fun clearAuthNotice() {
        _authNotice.value = null
    }

    fun resetPasswordUiState() {
        _passwordResetState.value = PasswordResetState.Idle
    }

    fun attemptAutoSignIn(activity: Activity, credentialManager: CredentialManager) {
        if (autoSignInAttempted || authRepository.currentUser != null) return
        if (_authState.value is AuthState.SigningIn || _authState.value is AuthState.SigningUp) return
        autoSignInAttempted = true

        viewModelScope.launch {
            if (authOperationMutex.isLocked || authRepository.currentUser != null) return@launch
            val token = trySilentGoogleIdToken(activity, credentialManager)
            if (token != null) {
                completeGoogleIdTokenSignIn(token)
            }
        }
    }

    fun signInWithGoogle(activity: Activity?, credentialManager: CredentialManager) {
        if (activity == null) {
            _authState.value = AuthState.Error("Sign-in could not start. Please reopen the app and try again.")
            return
        }
        if (_authState.value is AuthState.SigningIn || _authState.value is AuthState.SigningUp) return

        _authNotice.value = null
        _authState.value = AuthState.SigningIn

        viewModelScope.launch {
            authOperationMutex.withLock {
                try {
                    when (val outcome = acquireGoogleIdToken(activity, credentialManager)) {
                        is GoogleCredentialOutcome.Token -> {
                            authRepository.signInWithGoogleIdToken(outcome.idToken, _authLanguage.value.code).fold(
                                onSuccess = { (user, profile) ->
                                    lastSyncedUid = user.uid
                                    _authState.value = AuthState.SignedIn(user, profile)
                                },
                                onFailure = { error ->
                                    _authState.value = AuthState.Error(authRepository.mapAuthException(error))
                                }
                            )
                        }
                        is GoogleCredentialOutcome.Cancelled -> {
                            _authState.value = AuthState.SignedOut
                        }
                        is GoogleCredentialOutcome.Failure -> {
                            _authState.value = AuthState.Error(outcome.message)
                        }
                    }
                } catch (e: CancellationException) {
                    if (_authState.value is AuthState.SigningIn) {
                        _authState.value = AuthState.SignedOut
                    }
                    throw e
                }
            }
        }
    }

    private suspend fun completeGoogleIdTokenSignIn(token: String) {
        authOperationMutex.withLock {
            if (authRepository.currentUser != null) return@withLock
            _authState.value = AuthState.SigningIn
            authRepository.signInWithGoogleIdToken(token, _authLanguage.value.code).fold(
                onSuccess = { (user, profile) ->
                    lastSyncedUid = user.uid
                    _authState.value = AuthState.SignedIn(user, profile)
                },
                onFailure = { error ->
                    _authState.value = AuthState.Error(authRepository.mapAuthException(error))
                }
            )
        }
    }

    fun signInWithEmail(email: String, password: String) {
        if (_authState.value is AuthState.SigningIn || _authState.value is AuthState.SigningUp) return
        _authNotice.value = null
        _authState.value = AuthState.SigningIn

        viewModelScope.launch {
            authOperationMutex.withLock {
                authRepository.signInWithEmail(email, password, _authLanguage.value.code).fold(
                    onSuccess = { (user, profile) ->
                        lastSyncedUid = user.uid
                        _authState.value = AuthState.SignedIn(user, profile)
                    },
                    onFailure = { error ->
                        _authState.value = AuthState.Error(authRepository.mapAuthException(error))
                    }
                )
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, confirmPassword: String) {
        if (_authState.value is AuthState.SigningIn || _authState.value is AuthState.SigningUp) return
        _authNotice.value = null
        _authState.value = AuthState.SigningUp

        viewModelScope.launch {
            authOperationMutex.withLock {
                authRepository.signUpWithEmail(email, password, confirmPassword, _authLanguage.value.code).fold(
                    onSuccess = { outcome ->
                        lastSyncedUid = outcome.user.uid
                        _authNotice.value = if (outcome.verificationEmailSent) {
                            "Account created! Verification email sent to ${outcome.user.email ?: email.trim()}."
                        } else {
                            "Account created successfully! Welcome to YouthVerse."
                        }
                        _authState.value = AuthState.SignedIn(outcome.user, outcome.profile)
                    },
                    onFailure = { error ->
                        _authState.value = AuthState.Error(authRepository.mapAuthException(error))
                    }
                )
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (_passwordResetState.value is PasswordResetState.Sending) return
        _passwordResetState.value = PasswordResetState.Sending

        viewModelScope.launch {
            authRepository.sendPasswordResetEmail(email).fold(
                onSuccess = {
                    _passwordResetState.value = PasswordResetState.Sent(email.trim())
                },
                onFailure = { error ->
                    _passwordResetState.value = PasswordResetState.Error(authRepository.mapAuthException(error))
                }
            )
        }
    }

    fun signOut(credentialManager: CredentialManager?) {
        autoSignInAttempted = true
        viewModelScope.launch {
            authOperationMutex.withLock {
                lastSyncedUid = null
                _authNotice.value = null
                authRepository.signOut(credentialManager)
                _authState.value = AuthState.SignedOut
            }
        }
    }

    companion object {
        private const val TAG = "YouthVerseAuth"

        fun resolveDefaultWebClientId(context: Context): String? {
            val resId = context.resources.getIdentifier(
                "default_web_client_id",
                "string",
                context.packageName
            )
            if (resId == 0) return null
            return runCatching { context.getString(resId) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                        return AuthViewModel(authRepository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
