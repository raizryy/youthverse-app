package com.aistudio.youthverse.rvzkmq.repository

import com.aistudio.youthverse.rvzkmq.base.FirestoreEmulatorTestBase
import com.aistudio.youthverse.rvzkmq.data.repository.AuthRepository
import com.aistudio.youthverse.rvzkmq.data.repository.UserRepository
import com.aistudio.youthverse.rvzkmq.ui.auth.AuthState
import com.aistudio.youthverse.rvzkmq.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AuthRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun newRepositories(): Pair<UserRepository, AuthRepository> {
        val userRepo = UserRepository(db = firestore, auth = auth)
        val authRepo = AuthRepository(auth = auth, userRepository = userRepo)
        return userRepo to authRepo
    }

    @Test
    fun signUpWithEmail_createsAuthUserAndFirestoreProfileWithoutStoringPassword() = runBlocking {
        val (_, authRepo) = newRepositories()
        val email = "signup_${UUID.randomUUID()}@youthverse.test"

        val result = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signUpWithEmail(
                    email = email,
                    password = "StrongPass123!",
                    confirmPassword = "StrongPass123!",
                    preferredLanguage = "en"
                )
            }
        }

        assertTrue("Sign-up should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val outcome = result.getOrThrow()
        assertEquals(email, outcome.user.email)
        assertEquals(outcome.user.uid, outcome.profile.uid)
        assertEquals("password", outcome.profile.provider)
        assertEquals("USER", outcome.profile.role)
        assertEquals("en", outcome.profile.preferredLanguage)

        val snapshot = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                firestore.collection("users").document(outcome.user.uid).get().await()
            }
        }
        assertTrue(snapshot.exists())
        assertEquals("password", snapshot.getString("provider"))
        assertEquals("USER", snapshot.getString("role"))
        assertNotNull(snapshot.getTimestamp("createdAt"))
        assertNotNull(snapshot.getTimestamp("updatedAt"))
        assertFalse("Plaintext password must never be saved to Firestore", snapshot.contains("password"))
    }

    @Test
    fun signInWithEmail_validCredentials_succeedsAndPreservesExistingProfile() = runBlocking {
        val (userRepo, authRepo) = newRepositories()
        val email = "login_${UUID.randomUUID()}@youthverse.test"

        withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signUpWithEmail(email, "Password123!", "Password123!", preferredLanguage = "my").getOrThrow()
                userRepo.updateLanguage("en").getOrThrow()
                authRepo.signOut(credentialManager = null)
            }
        }
        assertNull(auth.currentUser)

        val loginResult = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signInWithEmail(email, "Password123!", preferredLanguage = "my")
            }
        }

        assertTrue("Email login should succeed: ${loginResult.exceptionOrNull()?.message}", loginResult.isSuccess)
        val (user, profile) = loginResult.getOrThrow()
        assertEquals(email, user.email)
        assertEquals("en", profile.preferredLanguage)
        assertEquals("USER", profile.role)
        assertEquals("password", profile.provider)
    }

    @Test
    fun signInWithEmail_invalidPassword_returnsFailureWithFriendlyError() = runBlocking {
        val (_, authRepo) = newRepositories()
        val email = "invalid_${UUID.randomUUID()}@youthverse.test"

        withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signUpWithEmail(email, "CorrectPass123!", "CorrectPass123!").getOrThrow()
                authRepo.signOut(credentialManager = null)
            }
        }

        val failedLogin = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signInWithEmail(email, "WrongPass999!")
            }
        }

        assertTrue(failedLogin.isFailure)
        val friendly = authRepo.mapAuthException(failedLogin.exceptionOrNull()!!)
        assertTrue("Unexpected error message: $friendly", friendly.contains("Incorrect email or password") || friendly.contains("invalid", ignoreCase = true))
        assertNull(auth.currentUser)
    }

    @Test
    fun sendPasswordResetEmail_handlesValidationAndEmulatorDispatch() = runBlocking {
        val (_, authRepo) = newRepositories()
        val email = "reset_${UUID.randomUUID()}@youthverse.test"

        val invalidResult = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.sendPasswordResetEmail("not-an-email")
            }
        }
        assertTrue(invalidResult.isFailure)

        withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signUpWithEmail(email, "ResetPass123!", "ResetPass123!").getOrThrow()
                authRepo.signOut(credentialManager = null)
            }
        }

        val resetResult = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.sendPasswordResetEmail(email)
            }
        }
        assertTrue("Password reset should succeed: ${resetResult.exceptionOrNull()?.message}", resetResult.isSuccess)
    }

    @Test
    fun signInWithGoogleIdToken_blankToken_failsSafely() = runBlocking {
        val (_, authRepo) = newRepositories()
        val result = authRepo.signInWithGoogleIdToken("")
        assertTrue(result.isFailure)
        assertNull(auth.currentUser)
    }

    @Test
    fun authPersistenceAndSignOut_viewModelRestoresSessionAndLogsOutCleanly() = runBlocking {
        val (_, authRepo) = newRepositories()
        val email = "persist_${UUID.randomUUID()}@youthverse.test"

        val createdUser = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signUpWithEmail(email, "PersistPass123!", "PersistPass123!").getOrThrow().user
            }
        }

        // Simulate app restart by creating a new AuthViewModel while FirebaseAuth still holds currentUser
        val restartedViewModel = AuthViewModel(authRepo)
        val restoredState = withTimeout(TEST_TIMEOUT_MS) {
            restartedViewModel.authState.first { it is AuthState.SignedIn }
        } as AuthState.SignedIn
        assertEquals(createdUser.uid, restoredState.user.uid)
        assertNotNull(restoredState.profile)

        restartedViewModel.signOut(credentialManager = null)
        val signedOutState = withTimeout(TEST_TIMEOUT_MS) {
            restartedViewModel.authState.first { it is AuthState.SignedOut }
        }
        assertTrue(signedOutState is AuthState.SignedOut)
        assertNull(auth.currentUser)
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 10000L
    }
}
