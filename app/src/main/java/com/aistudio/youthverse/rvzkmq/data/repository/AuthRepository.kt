package com.aistudio.youthverse.rvzkmq.data.repository

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.aistudio.youthverse.rvzkmq.data.model.UserProfile
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AuthValidationResult(
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null
) {
    val isValid: Boolean
        get() = emailError == null && passwordError == null && confirmPasswordError == null

    val firstError: String?
        get() = emailError ?: passwordError ?: confirmPasswordError
}

data class SignUpOutcome(
    val user: FirebaseUser,
    val profile: UserProfile,
    val verificationEmailSent: Boolean
)

object AuthValidator {
    const val MIN_PASSWORD_LENGTH = 8
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> "Please enter your email address."
            trimmed.length > 200 -> "Email address is too long."
            !EMAIL_REGEX.matches(trimmed) -> "Please enter a valid email address."
            else -> null
        }
    }

    fun validateSignInInput(email: String, password: String): AuthValidationResult {
        val emailError = validateEmail(email)
        val passwordError = if (password.isEmpty()) "Please enter your password." else null
        return AuthValidationResult(emailError = emailError, passwordError = passwordError)
    }

    fun validateSignUpInput(
        email: String,
        password: String,
        confirmPassword: String
    ): AuthValidationResult {
        val emailError = validateEmail(email)
        val passwordError = when {
            password.isEmpty() -> "Please enter a password."
            password.length < MIN_PASSWORD_LENGTH ->
                "Password must be at least $MIN_PASSWORD_LENGTH characters long."
            else -> null
        }
        val confirmError = when {
            confirmPassword.isEmpty() -> "Please confirm your password."
            password != confirmPassword -> "Passwords do not match."
            else -> null
        }
        return AuthValidationResult(
            emailError = emailError,
            passwordError = passwordError,
            confirmPasswordError = confirmError
        )
    }
}

class AuthRepository(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository
) {
    constructor(userRepository: UserRepository) : this(
        auth = Firebase.auth,
        userRepository = userRepository
    )

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun observeAuthUser(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            Log.d(TAG, "stage=AUTH_STATE_LISTENER hasUser=${firebaseAuth.currentUser != null}")
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        preferredLanguage: String = "my"
    ): Result<Pair<FirebaseUser, UserProfile>> {
        if (idToken.isBlank()) {
            Log.e(TAG, "stage=ID_TOKEN_VALIDATION status=FAILED reason=BLANK_TOKEN")
            return Result.failure(IllegalArgumentException("Google sign-in did not return an ID token."))
        }
        val tokenSegments = idToken.count { it == '.' } + 1
        if (tokenSegments != 3) {
            Log.w(TAG, "stage=ID_TOKEN_VALIDATION status=UNEXPECTED_FORMAT segments=$tokenSegments")
        } else {
            Log.d(TAG, "stage=ID_TOKEN_VALIDATION status=VALID segments=3 length=${idToken.length}")
        }

        val user = try {
            Log.d(TAG, "stage=FIREBASE_AUTH_GOOGLE_EXCHANGE status=START")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            authResult.user
                ?: return Result.failure(IllegalStateException("Google Sign-In did not return an authenticated user."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? FirebaseAuthException)?.errorCode
            Log.e(TAG, "stage=FIREBASE_AUTH_GOOGLE_EXCHANGE status=FAILED category=FIREBASE_AUTH_FAILURE errorType=${e::class.simpleName} errorCode=$code")
            return Result.failure(e)
        }

        Log.d(TAG, "stage=FIREBASE_AUTH_GOOGLE_EXCHANGE status=SUCCESS")
        return syncProfileAfterAuth(user, preferredLanguage, signInMethod = "google")
    }

    suspend fun signInWithEmail(
        email: String,
        password: String,
        preferredLanguage: String = "my"
    ): Result<Pair<FirebaseUser, UserProfile>> {
        val validation = AuthValidator.validateSignInInput(email, password)
        if (!validation.isValid) {
            return Result.failure(IllegalArgumentException(validation.firstError))
        }
        val user = try {
            Log.d(TAG, "stage=FIREBASE_AUTH_EMAIL_SIGN_IN status=START")
            val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
            authResult.user
                ?: return Result.failure(IllegalStateException("Sign-in did not return an authenticated user."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? FirebaseAuthException)?.errorCode
            Log.e(TAG, "stage=FIREBASE_AUTH_EMAIL_SIGN_IN status=FAILED category=FIREBASE_AUTH_FAILURE errorType=${e::class.simpleName} errorCode=$code")
            return Result.failure(e)
        }

        Log.d(TAG, "stage=FIREBASE_AUTH_EMAIL_SIGN_IN status=SUCCESS")
        return syncProfileAfterAuth(user, preferredLanguage, signInMethod = "email")
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        confirmPassword: String,
        preferredLanguage: String = "my"
    ): Result<SignUpOutcome> {
        val validation = AuthValidator.validateSignUpInput(email, password, confirmPassword)
        if (!validation.isValid) {
            return Result.failure(IllegalArgumentException(validation.firstError))
        }
        val user = try {
            Log.d(TAG, "stage=FIREBASE_AUTH_EMAIL_SIGN_UP status=START")
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            authResult.user
                ?: return Result.failure(IllegalStateException("Registration did not return an authenticated user."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? FirebaseAuthException)?.errorCode
            Log.e(TAG, "stage=FIREBASE_AUTH_EMAIL_SIGN_UP status=FAILED category=FIREBASE_AUTH_FAILURE errorType=${e::class.simpleName} errorCode=$code")
            return Result.failure(e)
        }

        Log.d(TAG, "stage=FIREBASE_AUTH_EMAIL_SIGN_UP status=SUCCESS")
        val verificationEmailSent = try {
            user.sendEmailVerification().await()
            Log.d(TAG, "stage=FIREBASE_AUTH_VERIFICATION_EMAIL status=SENT")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "stage=FIREBASE_AUTH_VERIFICATION_EMAIL status=FAILED errorType=${e::class.simpleName}")
            false
        }

        val syncResult = syncProfileAfterAuth(user, preferredLanguage, signInMethod = "email_signup")
        return syncResult.map { (syncedUser, profile) ->
            SignUpOutcome(
                user = syncedUser,
                profile = profile,
                verificationEmailSent = verificationEmailSent
            )
        }
    }

    private suspend fun syncProfileAfterAuth(
        user: FirebaseUser,
        preferredLanguage: String,
        signInMethod: String
    ): Result<Pair<FirebaseUser, UserProfile>> {
        Log.d(TAG, "stage=PROFILE_SYNC status=START method=$signInMethod")
        val profileResult = userRepository.getOrCreateProfile(preferredLanguage)
        return profileResult.fold(
            onSuccess = { profile ->
                Log.d(TAG, "stage=PROFILE_SYNC status=SUCCESS method=$signInMethod role=${profile.role}")
                Result.success(user to profile)
            },
            onFailure = { error ->
                val syncError = error as? ProfileSyncException
                val category = if (syncError?.isPermissionDenied == true || extractFirestoreException(error)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    "FIRESTORE_PERMISSION_FAILURE"
                } else {
                    "PROFILE_SYNC_FAILURE"
                }
                Log.e(
                    TAG,
                    "stage=PROFILE_SYNC status=FAILED method=$signInMethod category=$category operation=${syncError?.operation ?: "unknown"} firestoreCode=${syncError?.firestoreCode?.name ?: extractFirestoreException(error)?.code?.name}"
                )
                // Keep FirebaseAuth session aligned with failed sign-in state so retries do not
                // leave a half-signed-in user behind an AuthState.Error screen.
                withContext(NonCancellable) {
                    runCatching { auth.signOut() }
                }
                Result.failure(error)
            }
        )
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val emailError = AuthValidator.validateEmail(email)
        if (emailError != null) {
            return Result.failure(IllegalArgumentException(emailError))
        }
        return try {
            Log.d(TAG, "stage=FIREBASE_AUTH_PASSWORD_RESET status=START")
            auth.sendPasswordResetEmail(email.trim()).await()
            Log.d(TAG, "stage=FIREBASE_AUTH_PASSWORD_RESET status=SUCCESS")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? FirebaseAuthException)?.errorCode
            Log.e(TAG, "stage=FIREBASE_AUTH_PASSWORD_RESET status=FAILED category=FIREBASE_AUTH_FAILURE errorCode=$code")
            Result.failure(e)
        }
    }

    suspend fun syncProfileForCurrentUser(
        preferredLanguage: String = "my"
    ): Result<Pair<FirebaseUser, UserProfile>> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("No authenticated user session."))
        Log.d(TAG, "stage=SESSION_RESTORE_PROFILE_SYNC status=START")
        return userRepository.getOrCreateProfile(preferredLanguage).map { profile ->
            Log.d(TAG, "stage=SESSION_RESTORE_PROFILE_SYNC status=SUCCESS")
            user to profile
        }.onFailure { error ->
            val fsCode = extractFirestoreException(error)?.code
            Log.w(TAG, "stage=SESSION_RESTORE_PROFILE_SYNC status=FAILED firestoreCode=${fsCode?.name ?: error::class.simpleName}")
        }
    }

    suspend fun signOut(credentialManager: CredentialManager?) {
        withContext(NonCancellable) {
            if (credentialManager != null) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "stage=SIGN_OUT_CLEAR_CREDENTIAL status=WARNING error=${e::class.simpleName}")
                }
            }
            auth.signOut()
            Log.d(TAG, "stage=FIREBASE_AUTH_SIGN_OUT status=SUCCESS")
        }
    }

    fun mapAuthException(error: Throwable): String {
        if (error is IllegalArgumentException && !error.message.isNullOrBlank()) {
            return error.message!!
        }

        if (error is ProfileSyncException) {
            return if (error.isPermissionDenied) {
                "Sign-in succeeded in Firebase Auth, but Firestore denied access to your profile (${error.operation}). Please verify deployed Firestore Security Rules."
            } else {
                "Sign-in succeeded in Firebase Auth, but profile synchronization failed (${error.firestoreCode?.name ?: error.operation}). Please check your connection and try again."
            }
        }

        val firestoreError = extractFirestoreException(error)
        if (firestoreError != null) {
            return if (firestoreError.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Sign-in succeeded in Firebase Auth, but Firestore returned PERMISSION_DENIED. Please verify deployed Firestore Security Rules."
            } else {
                "Sign-in succeeded in Firebase Auth, but Firestore profile sync failed (${firestoreError.code.name}). Please try again."
            }
        }

        return when (error) {
            is NoCredentialException ->
                "No Google account is available on this device. Add a Google account or sign in with Email."
            is GetCredentialProviderConfigurationException ->
                "Google Play services or Credential Manager is not available on this device."
            is GetCredentialUnsupportedException ->
                "Google Credential Manager is not supported on this device. Please sign in with Email."
            is GetCredentialInterruptedException ->
                "Sign-in was interrupted. Please try again."
            is GetCredentialCustomException ->
                mapCredentialMessage(error.message)
            is GetCredentialCancellationException ->
                "Google Sign-In was cancelled."
            is GetCredentialException ->
                mapCredentialMessage(error.message)
            is GoogleIdTokenParsingException ->
                "Google returned an unreadable sign-in token. Please try again."
            is FirebaseNetworkException ->
                "Network connection failed. Check your internet connection and try again."
            is FirebaseTooManyRequestsException ->
                "Too many authentication attempts. Please wait a moment and try again."
            is FirebaseAuthWeakPasswordException ->
                "Password is too weak. Use at least ${AuthValidator.MIN_PASSWORD_LENGTH} characters with letters and numbers."
            is FirebaseAuthUserCollisionException ->
                "An account already exists with this email address. Please sign in instead."
            is FirebaseAuthInvalidUserException ->
                mapFirebaseErrorCode(error.errorCode)
            is FirebaseAuthInvalidCredentialsException ->
                mapFirebaseErrorCode(error.errorCode)
            is FirebaseAuthException ->
                mapFirebaseErrorCode(error.errorCode, error.localizedMessage)
            else ->
                error.localizedMessage?.takeIf { it.isNotBlank() && it.length < 180 }
                    ?: "Authentication failed. Please check your connection and try again."
        }
    }

    private fun extractFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private fun mapCredentialMessage(rawMessage: String?): String {
        val msg = rawMessage.orEmpty()
        return when {
            msg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
                msg.contains("10:", ignoreCase = true) ||
                msg.contains("28444") ->
                "Google Sign-In SHA-1 or Web Client ID does not match Firebase configuration."
            msg.contains("16:", ignoreCase = true) ||
                msg.contains("28436") ||
                msg.contains("reauth failed", ignoreCase = true) ->
                "Google account verification could not be completed. Check SHA-1 registration and Google Play services."
            msg.contains("network", ignoreCase = true) ->
                "Network connection failed while contacting Google Sign-In."
            else ->
                "Google Sign-In failed (${msg.ifBlank { "unexpected credential error" }})."
        }
    }

    private fun mapFirebaseErrorCode(code: String, fallbackMessage: String? = null): String {
        return when (code.uppercase()) {
            "ERROR_INVALID_EMAIL" ->
                "Please enter a valid email address."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS" ->
                "Incorrect email or password. Please check your credentials and try again."
            "ERROR_USER_NOT_FOUND" ->
                "No YouthVerse account found for that email address."
            "ERROR_USER_DISABLED" ->
                "This account has been disabled. Please contact support."
            "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "An account already exists with this email address."
            "ERROR_WEAK_PASSWORD" ->
                "Password is too weak. Use at least ${AuthValidator.MIN_PASSWORD_LENGTH} characters."
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "This sign-in method is not enabled in Firebase Authentication Console."
            "ERROR_TOO_MANY_REQUESTS" ->
                "Too many attempts. Please wait a while before trying again."
            "ERROR_NETWORK_REQUEST_FAILED" ->
                "Network connection failed. Check your internet connection and try again."
            else -> when {
                code.contains("NETWORK", ignoreCase = true) ->
                    "Network connection failed. Check your internet connection and try again."
                code.contains("TOKEN", ignoreCase = true) || code.contains("CREDENTIAL", ignoreCase = true) ->
                    "Sign-in credentials could not be verified. Please try again."
                code.contains("TOO_MANY", ignoreCase = true) ->
                    "Too many attempts. Please wait a while before trying again."
                !fallbackMessage.isNullOrBlank() && fallbackMessage.length < 180 ->
                    fallbackMessage
                else ->
                    "Authentication failed. Please try again."
            }
        }
    }

    companion object {
        private const val TAG = "YouthVerseAuth"
    }
}
