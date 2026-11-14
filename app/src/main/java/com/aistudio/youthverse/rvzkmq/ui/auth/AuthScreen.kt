package com.aistudio.youthverse.rvzkmq.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.youthverse.rvzkmq.ui.theme.BorderDark
import com.aistudio.youthverse.rvzkmq.ui.theme.CyanAccent
import com.aistudio.youthverse.rvzkmq.ui.theme.DeepSpaceBackground
import com.aistudio.youthverse.rvzkmq.ui.theme.ElevatedSurfaceDark
import com.aistudio.youthverse.rvzkmq.ui.theme.VioletPrimary
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuthRoute {
    WELCOME,
    EMAIL_SIGN_IN,
    EMAIL_SIGN_UP,
    FORGOT_PASSWORD
}

@Composable
fun AuthFlow(
    authViewModel: AuthViewModel,
    authState: AuthState
) {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val language by authViewModel.authLanguage.collectAsStateWithLifecycle()
    val passwordResetState by authViewModel.passwordResetState.collectAsStateWithLifecycle()
    var currentRoute by rememberSaveable { mutableStateOf(AuthRoute.WELCOME) }

    LaunchedEffect(Unit) {
        val activity = context as? Activity
        if (activity != null) {
            authViewModel.attemptAutoSignIn(activity, credentialManager)
        }
    }

    BackHandler(enabled = currentRoute != AuthRoute.WELCOME) {
        authViewModel.clearError()
        authViewModel.resetPasswordUiState()
        currentRoute = when (currentRoute) {
            AuthRoute.FORGOT_PASSWORD -> AuthRoute.EMAIL_SIGN_IN
            AuthRoute.EMAIL_SIGN_IN, AuthRoute.EMAIL_SIGN_UP -> AuthRoute.WELCOME
            AuthRoute.WELCOME -> AuthRoute.WELCOME
        }
    }

    when (currentRoute) {
        AuthRoute.WELCOME -> {
            WelcomeAuthScreen(
                authState = authState,
                language = language,
                onLanguageChange = authViewModel::setAuthLanguage,
                onGoogleSignInClick = {
                    val activity = context as? Activity
                    authViewModel.signInWithGoogle(activity, credentialManager)
                },
                onEmailSignInClick = {
                    authViewModel.clearError()
                    currentRoute = AuthRoute.EMAIL_SIGN_IN
                },
                onCreateAccountClick = {
                    authViewModel.clearError()
                    currentRoute = AuthRoute.EMAIL_SIGN_UP
                }
            )
        }
        AuthRoute.EMAIL_SIGN_IN -> {
            EmailSignInScreen(
                authState = authState,
                language = language,
                onBackClick = {
                    authViewModel.clearError()
                    currentRoute = AuthRoute.WELCOME
                },
                onForgotPasswordClick = {
                    authViewModel.clearError()
                    authViewModel.resetPasswordUiState()
                    currentRoute = AuthRoute.FORGOT_PASSWORD
                },
                onCreateAccountClick = {
                    authViewModel.clearError()
                    currentRoute = AuthRoute.EMAIL_SIGN_UP
                },
                onClearError = authViewModel::clearError,
                onSubmitSignIn = authViewModel::signInWithEmail
            )
        }
        AuthRoute.EMAIL_SIGN_UP -> {
            EmailSignUpScreen(
                authState = authState,
                language = language,
                onBackClick = {
                    authViewModel.clearError()
                    currentRoute = AuthRoute.WELCOME
                },
                onSignInClick = {
                    authViewModel.clearError()
                    currentRoute = AuthRoute.EMAIL_SIGN_IN
                },
                onClearError = authViewModel::clearError,
                onSubmitSignUp = authViewModel::signUpWithEmail
            )
        }
        AuthRoute.FORGOT_PASSWORD -> {
            ForgotPasswordScreen(
                passwordResetState = passwordResetState,
                language = language,
                onBackClick = {
                    authViewModel.resetPasswordUiState()
                    currentRoute = AuthRoute.EMAIL_SIGN_IN
                },
                onClearResetState = authViewModel::resetPasswordUiState,
                onSubmitReset = authViewModel::sendPasswordReset
            )
        }
    }
}

@Composable
fun AuthLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DeepSpaceBackground,
                        ElevatedSurfaceDark,
                        DeepSpaceBackground
                    )
                )
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(VioletPrimary, DeepSpaceBackground)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = "YouthVerse",
                    tint = CyanAccent,
                    modifier = Modifier.size(46.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "YouthVerse",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(
                color = CyanAccent,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp
            )
        }
    }
}

@Composable
fun WelcomeAuthScreen(
    authState: AuthState,
    language: AuthLanguage,
    onLanguageChange: (AuthLanguage) -> Unit,
    onGoogleSignInClick: () -> Unit,
    onEmailSignInClick: () -> Unit,
    onCreateAccountClick: () -> Unit
) {
    val isSigningIn = authState is AuthState.SigningIn || authState is AuthState.SigningUp
    val errorMessage = (authState as? AuthState.Error)?.message
    val isMyanmar = language == AuthLanguage.MY

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DeepSpaceBackground,
                        ElevatedSurfaceDark,
                        DeepSpaceBackground
                    )
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Language selector row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuthLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = language == lang,
                        onClick = { onLanguageChange(lang) },
                        label = { Text(lang.label, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VioletPrimary.copy(alpha = 0.25f),
                            selectedLabelColor = CyanAccent,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("auth_lang_${lang.code}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                // App Brand Logo
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(VioletPrimary, DeepSpaceBackground)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = "YouthVerse Logo",
                        tint = CyanAccent,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "YouthVerse",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isMyanmar) {
                        "မြန်မာနှင့် အင်္ဂလိပ် ဝတ္ထု၊ မန်ဂါနှင့် ကာတွန်းစာအုပ်များ ဖတ်ရှုနိုင်ရာ"
                    } else {
                        "Digital Reading Universe for Novels & Manga in Myanmar & English"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Feature Highlights
                FeatureRow(
                    icon = Icons.Default.AutoStories,
                    text = if (isMyanmar) "ဝတ္ထု၊ Manga, Manhwa နှင့် Manhua များ" else "Novels, Manga, Manhwa & Manhua"
                )
                Spacer(modifier = Modifier.height(10.dp))
                FeatureRow(
                    icon = Icons.Default.Translate,
                    text = if (isMyanmar) "မြန်မာနှင့် အင်္ဂလိပ် နှစ်ဘာသာ စနစ်" else "Bilingual: Myanmar & English Unicode"
                )
                Spacer(modifier = Modifier.height(10.dp))
                FeatureRow(
                    icon = Icons.Default.Bookmark,
                    text = if (isMyanmar) "Cloud Library နှင့် စာဖတ်မှတ်တမ်း သိမ်းဆည်းမှု" else "Cloud Library & Progress Sync"
                )
                Spacer(modifier = Modifier.height(10.dp))
                FeatureRow(
                    icon = Icons.Default.Palette,
                    text = if (isMyanmar) "Dark, AMOLED နှင့် Sepia စာဖတ်စနစ်" else "Custom Reader: Dark, AMOLED & Sepia"
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (errorMessage != null) {
                    AuthErrorBanner(message = errorMessage)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 1. Google Sign-In Button
                Button(
                    onClick = onGoogleSignInClick,
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VioletPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isMyanmar) "ဝင်ရောက်နေသည်…" else "Signing in…",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isMyanmar) "Google ဖြင့် ဝင်ရောက်မည်" else "Sign in with Google",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Sign in with Email Button
                Button(
                    onClick = onEmailSignInClick,
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElevatedSurfaceDark,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("email_sign_in_nav_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isMyanmar) "အီးမေးလ်ဖြင့် ဝင်မည်" else "Sign in with Email",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Create an Account Button
                OutlinedButton(
                    onClick = onCreateAccountClick,
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CyanAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("create_account_nav_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isMyanmar) "အကောင့်အသစ် ဖွင့်မည်" else "Create an Account",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isMyanmar) {
                        "ဆက်လက်အသုံးပြုခြင်းဖြင့် YouthVerse ၏ စည်းကမ်းချက်များကို သဘောတူပါသည်။"
                    } else {
                        "By continuing, you agree to YouthVerse Terms & Privacy Policy."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(0.92f)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(VioletPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
    }
}

internal suspend fun trySilentGoogleIdToken(
    context: Context,
    credentialManager: CredentialManager
): String? {
    val activity = context as? Activity ?: return null
    val clientId = AuthViewModel.resolveDefaultWebClientId(activity) ?: return null
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken.takeIf { it.isNotBlank() }
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Silent sign-in is optional. Keep the sign-in screen usable when no
        // authorized Google account is available or the user dismisses it.
        null
    }
}

internal suspend fun acquireGoogleIdToken(
    context: Context,
    credentialManager: CredentialManager
): GoogleCredentialOutcome {
    val activity = context as? Activity
        ?: return GoogleCredentialOutcome.Failure("Sign-in could not start. Please reopen the app and try again.")

    val clientId = AuthViewModel.resolveDefaultWebClientId(activity)
        ?: return GoogleCredentialOutcome.Failure(
            "Google Sign-In is not configured. Check the Firebase Android app and web client ID."
        )

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    return try {
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleCredentialOutcome.Failure("Google returned an unsupported sign-in credential. Please try again.")
        } else {
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            if (token.isBlank()) {
                GoogleCredentialOutcome.Failure("Google Sign-In returned an empty ID token. Please try again.")
            } else {
                GoogleCredentialOutcome.Token(token)
            }
        }
    } catch (e: GetCredentialCancellationException) {
        Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
        val msg = e.message.orEmpty()
        if (msg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
            msg.contains("reauth failed", ignoreCase = true) ||
            msg.contains("16:") ||
            msg.contains("28436") ||
            msg.contains("28444")
        ) {
            GoogleCredentialOutcome.Failure(friendlyAuthError(e))
        } else {
            GoogleCredentialOutcome.Cancelled
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("Auth", "Google Sign-In failed: ${e::class.simpleName} - ${e.message}", e)
        GoogleCredentialOutcome.Failure(friendlyAuthError(e))
    }
}

internal sealed class GoogleCredentialOutcome {
    data class Token(val idToken: String) : GoogleCredentialOutcome()
    data object Cancelled : GoogleCredentialOutcome()
    data class Failure(val message: String) : GoogleCredentialOutcome()
}

private fun friendlyAuthError(error: Exception): String {
    if (error is NoCredentialException) {
        return "No Google account found on this device. Add a Google account or sign in with Email."
    }
    if (error is GetCredentialProviderConfigurationException) {
        return "Google Play services Credential Manager is unavailable on this device."
    }
    if (error is GetCredentialUnsupportedException) {
        return "Google Sign-In via Credential Manager is unsupported on this device. Please use Email Sign-In."
    }
    if (error is GetCredentialInterruptedException) {
        return "Google Sign-In was interrupted. Please try again."
    }
    if (error is GoogleIdTokenParsingException) {
        return "Google returned an invalid ID token. Please try again."
    }
    if (error is GetCredentialException) {
        val msg = error.message.orEmpty()
        return when {
            msg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
                msg.contains("10:", ignoreCase = true) ||
                msg.contains("28444") ->
                "Google Sign-In SHA-1 or Web Client ID does not match Firebase Console configuration."
            msg.contains("reauth failed", ignoreCase = true) ||
                msg.contains("16:") ||
                msg.contains("28436") ->
                "Google account verification failed. Verify SHA-1/SHA-256 registration and Google Play services."
            msg.contains("network", ignoreCase = true) ->
                "Network connection failed. Check your internet connection and try again."
            else ->
                "Google Sign-In could not complete (${msg.ifBlank { "credential error" }})."
        }
    }
    val code = (error as? FirebaseAuthException)?.errorCode.orEmpty()
    return when {
        code.contains("NETWORK", ignoreCase = true) ->
            "Network connection failed. Check your internet connection and try again."
        code.contains("INVALID_CREDENTIAL", ignoreCase = true) ||
            code.contains("TOKEN", ignoreCase = true) ->
            "Google sign-in could not be verified. Please try again."
        code.contains("ACCOUNT_EXISTS", ignoreCase = true) ->
            "An account already exists with this sign-in method."
        code.contains("TOO_MANY", ignoreCase = true) ->
            "Too many attempts. Please wait a while before trying again."
        else ->
            "Sign-in failed. Check your Google account and internet connection, then try again."
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {},
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
