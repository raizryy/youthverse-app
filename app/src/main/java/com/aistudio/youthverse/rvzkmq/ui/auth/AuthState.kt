package com.aistudio.youthverse.rvzkmq.ui.auth

import com.aistudio.youthverse.rvzkmq.data.model.UserProfile
import com.google.firebase.auth.FirebaseUser

sealed class AuthState {
    data object Loading : AuthState()
    data object SignedOut : AuthState()
    data object SigningIn : AuthState()
    data object SigningUp : AuthState()
    data class SignedIn(
        val user: FirebaseUser,
        val profile: UserProfile? = null
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class PasswordResetState {
    data object Idle : PasswordResetState()
    data object Sending : PasswordResetState()
    data class Sent(val email: String) : PasswordResetState()
    data class Error(val message: String) : PasswordResetState()
}

enum class AuthLanguage(val code: String, val label: String) {
    EN("en", "EN"),
    MY("my", "မြန်မာ")
}
