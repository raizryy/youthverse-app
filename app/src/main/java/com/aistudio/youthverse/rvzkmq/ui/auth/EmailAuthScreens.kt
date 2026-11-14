package com.aistudio.youthverse.rvzkmq.ui.auth

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.youthverse.rvzkmq.data.repository.AuthValidator
import com.aistudio.youthverse.rvzkmq.ui.theme.BorderDark
import com.aistudio.youthverse.rvzkmq.ui.theme.CyanAccent
import com.aistudio.youthverse.rvzkmq.ui.theme.DeepSpaceBackground
import com.aistudio.youthverse.rvzkmq.ui.theme.ElevatedSurfaceDark
import com.aistudio.youthverse.rvzkmq.ui.theme.ErrorRed
import com.aistudio.youthverse.rvzkmq.ui.theme.SuccessGreen
import com.aistudio.youthverse.rvzkmq.ui.theme.TextMutedDark
import com.aistudio.youthverse.rvzkmq.ui.theme.TextPrimaryDark
import com.aistudio.youthverse.rvzkmq.ui.theme.TextSecondaryDark
import com.aistudio.youthverse.rvzkmq.ui.theme.VioletPrimary

@Composable
fun EmailSignInScreen(
    authState: AuthState,
    language: AuthLanguage,
    onBackClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
    onClearError: () -> Unit,
    onSubmitSignIn: (email: String, password: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var emailError by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordError by rememberSaveable { mutableStateOf<String?>(null) }

    val isSubmitting = authState is AuthState.SigningIn || authState is AuthState.SigningUp
    val authErrorMessage = (authState as? AuthState.Error)?.message
    val isMyanmar = language == AuthLanguage.MY

    fun submit() {
        if (isSubmitting) return
        val validation = AuthValidator.validateSignInInput(email, password)
        emailError = validation.emailError
        passwordError = validation.passwordError
        if (validation.isValid) {
            focusManager.clearFocus()
            onSubmitSignIn(email.trim(), password)
        }
    }

    AuthScaffoldContainer(
        title = if (isMyanmar) "အီးမေးလ်ဖြင့် ဝင်မည်" else "Sign in with Email",
        subtitle = if (isMyanmar) {
            "သင်၏ YouthVerse အကောင့်သို့ ပြန်လည်ဝင်ရောက်ပါ"
        } else {
            "Welcome back to your YouthVerse reading universe"
        },
        onBackClick = onBackClick
    ) {
        if (authErrorMessage != null) {
            AuthErrorBanner(message = authErrorMessage)
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = null
                onClearError()
            },
            label = { Text(if (isMyanmar) "အီးမေးလ် လိပ်စာ" else "Email Address") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = CyanAccent) },
            isError = emailError != null,
            supportingText = emailError?.let { err -> { Text(err) } },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = authTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("email_sign_in_email_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
                onClearError()
            },
            label = { Text(if (isMyanmar) "စကားဝှက်" else "Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CyanAccent) },
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.testTag("email_sign_in_password_toggle")
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = TextSecondaryDark
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = passwordError?.let { err -> { Text(err) } },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = authTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("email_sign_in_password_input")
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            TextButton(
                onClick = {
                    onClearError()
                    onForgotPasswordClick()
                },
                enabled = !isSubmitting,
                modifier = Modifier.testTag("forgot_password_link")
            ) {
                Text(
                    text = if (isMyanmar) "စကားဝှက် မေ့နေပါသလား?" else "Forgot Password?",
                    color = CyanAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { submit() },
            enabled = !isSubmitting,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VioletPrimary,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("email_sign_in_submit_button")
        ) {
            if (isSubmitting) {
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
                Text(
                    text = if (isMyanmar) "ဝင်ရောက်မည်" else "Sign In",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isMyanmar) "အကောင့်မရှိသေးပါသလား?" else "New to YouthVerse?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark
            )
            TextButton(
                onClick = {
                    onClearError()
                    onCreateAccountClick()
                },
                enabled = !isSubmitting,
                modifier = Modifier.testTag("go_to_sign_up_button")
            ) {
                Text(
                    text = if (isMyanmar) "အကောင့်အသစ်ဖွင့်မည်" else "Create an Account",
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmailSignUpScreen(
    authState: AuthState,
    language: AuthLanguage,
    onBackClick: () -> Unit,
    onSignInClick: () -> Unit,
    onClearError: () -> Unit,
    onSubmitSignUp: (email: String, password: String, confirmPassword: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var emailError by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmError by rememberSaveable { mutableStateOf<String?>(null) }

    val isSubmitting = authState is AuthState.SigningUp || authState is AuthState.SigningIn
    val authErrorMessage = (authState as? AuthState.Error)?.message
    val isMyanmar = language == AuthLanguage.MY

    fun submit() {
        if (isSubmitting) return
        val validation = AuthValidator.validateSignUpInput(email, password, confirmPassword)
        emailError = validation.emailError
        passwordError = validation.passwordError
        confirmError = validation.confirmPasswordError
        if (validation.isValid) {
            focusManager.clearFocus()
            onSubmitSignUp(email.trim(), password, confirmPassword)
        }
    }

    AuthScaffoldContainer(
        title = if (isMyanmar) "အကောင့်အသစ် ဖွင့်မည်" else "Create an Account",
        subtitle = if (isMyanmar) {
            "စာဖတ်မှတ်တမ်းနှင့် စာအုပ်စင်များကို သိမ်းဆည်းရန် မှတ်ပုံတင်ပါ"
        } else {
            "Register with email to sync your library & reading progress"
        },
        onBackClick = onBackClick
    ) {
        if (authErrorMessage != null) {
            AuthErrorBanner(message = authErrorMessage)
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = null
                onClearError()
            },
            label = { Text(if (isMyanmar) "အီးမေးလ် လိပ်စာ" else "Email Address") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = CyanAccent) },
            isError = emailError != null,
            supportingText = emailError?.let { err -> { Text(err) } },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = authTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sign_up_email_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
                onClearError()
            },
            label = { Text(if (isMyanmar) "စကားဝှက် (အနည်းဆုံး ၈ လုံး)" else "Password (min 8 characters)") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CyanAccent) },
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.testTag("sign_up_password_toggle")
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = TextSecondaryDark
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = {
                val hint = passwordError ?: if (isMyanmar) {
                    "အနည်းဆုံး စာလုံး ${AuthValidator.MIN_PASSWORD_LENGTH} လုံး ထည့်သွင်းပါ"
                } else {
                    "Use at least ${AuthValidator.MIN_PASSWORD_LENGTH} characters"
                }
                Text(hint)
            },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            colors = authTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sign_up_password_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                confirmError = null
                onClearError()
            },
            label = { Text(if (isMyanmar) "စကားဝှက် အတည်ပြုပါ" else "Confirm Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CyanAccent) },
            trailingIcon = {
                IconButton(
                    onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                    modifier = Modifier.testTag("sign_up_confirm_password_toggle")
                ) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (confirmPasswordVisible) "Hide confirm password" else "Show confirm password",
                        tint = TextSecondaryDark
                    )
                }
            },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = confirmError != null,
            supportingText = confirmError?.let { err -> { Text(err) } },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = authTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sign_up_confirm_password_input")
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { submit() },
            enabled = !isSubmitting,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VioletPrimary,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("sign_up_submit_button")
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isMyanmar) "အကောင့်ဖွင့်နေသည်…" else "Creating account…",
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = if (isMyanmar) "အကောင့်အသစ်ဖွင့်မည်" else "Create Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isMyanmar) "အကောင့်ရှိပြီးသားလား?" else "Already have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark
            )
            TextButton(
                onClick = {
                    onClearError()
                    onSignInClick()
                },
                enabled = !isSubmitting,
                modifier = Modifier.testTag("go_to_sign_in_button")
            ) {
                Text(
                    text = if (isMyanmar) "ဝင်ရောက်မည်" else "Sign In",
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    passwordResetState: PasswordResetState,
    language: AuthLanguage,
    onBackClick: () -> Unit,
    onClearResetState: () -> Unit,
    onSubmitReset: (email: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var emailError by rememberSaveable { mutableStateOf<String?>(null) }

    val isSending = passwordResetState is PasswordResetState.Sending
    val isMyanmar = language == AuthLanguage.MY

    fun submit() {
        if (isSending) return
        val error = AuthValidator.validateEmail(email)
        emailError = error
        if (error == null) {
            focusManager.clearFocus()
            onSubmitReset(email.trim())
        }
    }

    AuthScaffoldContainer(
        title = if (isMyanmar) "စကားဝှက် ပြန်လည်သတ်မှတ်မည်" else "Reset Password",
        subtitle = if (isMyanmar) {
            "စကားဝှက် ပြန်လည်သတ်မှတ်ရန် လင့်ခ်ပေးပို့မည့် အီးမေးလ်ကို ထည့်သွင်းပါ"
        } else {
            "Enter your registered email address and we will send you a password reset link."
        },
        onBackClick = onBackClick
    ) {
        when (passwordResetState) {
            is PasswordResetState.Error -> {
                AuthErrorBanner(message = passwordResetState.message)
                Spacer(modifier = Modifier.height(16.dp))
            }
            is PasswordResetState.Sent -> {
                Surface(
                    color = SuccessGreen.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("forgot_password_success_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isMyanmar) {
                                "${passwordResetState.email} သို့ စကားဝှက်ပြန်လည်သတ်မှတ်ရန် အီးမေးလ် ပေးပို့ပြီးပါပြီ။"
                            } else {
                                "Password reset email sent to ${passwordResetState.email}. Check your inbox and spam folder."
                            },
                            color = TextPrimaryDark,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            else -> Unit
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = null
                onClearResetState()
            },
            label = { Text(if (isMyanmar) "မှတ်ပုံတင်ထားသော အီးမေးလ်" else "Registered Email Address") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = CyanAccent) },
            isError = emailError != null,
            supportingText = emailError?.let { err -> { Text(err) } },
            singleLine = true,
            enabled = !isSending,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = authTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("forgot_password_email_input")
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { submit() },
            enabled = !isSending,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VioletPrimary,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("forgot_password_submit_button")
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isMyanmar) "ပေးပို့နေသည်…" else "Sending reset email…",
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = if (isMyanmar) "ပြန်လည်သတ်မှတ်ရန် လင့်ခ်ပို့မည်" else "Send Reset Link",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onBackClick,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderDark),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("forgot_password_back_button")
        ) {
            Text(
                text = if (isMyanmar) "အကောင့်ဝင်ရန် စာမျက်နှာသို့ ပြန်သွားမည်" else "Back to Sign In",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AuthScaffoldContainer(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    content: @Composable () -> Unit
) {
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
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("auth_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "YouthVerse",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark
                )

                Spacer(modifier = Modifier.height(28.dp))

                content()
            }
        }
    }
}

@Composable
internal fun AuthErrorBanner(message: String) {
    Surface(
        color = ErrorRed.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_error_banner")
    ) {
        Text(
            text = message,
            color = ErrorRed,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VioletPrimary,
    unfocusedBorderColor = BorderDark,
    focusedLabelColor = CyanAccent,
    unfocusedLabelColor = TextSecondaryDark,
    cursorColor = CyanAccent,
    focusedTextColor = TextPrimaryDark,
    unfocusedTextColor = TextPrimaryDark,
    focusedSupportingTextColor = TextSecondaryDark,
    unfocusedSupportingTextColor = TextSecondaryDark,
    errorBorderColor = ErrorRed,
    errorLabelColor = ErrorRed,
    errorSupportingTextColor = ErrorRed,
    errorTextColor = TextPrimaryDark,
    disabledTextColor = TextSecondaryDark,
    disabledBorderColor = BorderDark.copy(alpha = 0.6f),
    disabledLabelColor = TextMutedDark
)
