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
        val viewModelStore = androidx.lifecycle.ViewModelStore()
        try {
            val restartedViewModel = androidx.lifecycle.ViewModelProvider(
                viewModelStore,
                AuthViewModel.factory(authRepo)
            )[AuthViewModel::class.java]
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
        } finally {
            viewModelStore.clear()
        }
    }

    @Test
    fun signInWithEmail_existingAdminProfileWithMissingProvider_succeedsAndPreservesAdminRole() = runBlocking {
        val (_, authRepo) = newRepositories()
        val email = "admin_${UUID.randomUUID()}@youthverse.test"

        val createdUser = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signUpWithEmail(email, "AdminPass123!", "AdminPass123!").getOrThrow().user
            }
        }
        // Promote the Firestore profile doc to ADMIN and clear provider using an admin-claimed token
        // (simulating a pre-existing production admin user whose doc predates the 'provider' field).
        val adminToken = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                createdUser.getIdToken(false).await()
                firestore.collection("users").document(createdUser.uid).get().await()
            }
        }
        assertTrue(adminToken.exists())

        withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signOut(credentialManager = null)
                // Sign in as pre-seeded admin user from seed-emulator-admin.mjs (has admin: true claim)
                val seededAdmin = auth.signInWithEmailAndPassword("admin@test.com", "password123").await().user!!
                firestore.collection("users").document(createdUser.uid).update(
                    mapOf(
                        "role" to "ADMIN",
                        "provider" to ""
                    )
                ).await()
                assertNotNull(seededAdmin.uid)
                authRepo.signOut(credentialManager = null)
            }
        }

        val loginResult = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signInWithEmail(email, "AdminPass123!", preferredLanguage = "en")
            }
        }

        assertTrue("Existing ADMIN profile sign-in should succeed: ${loginResult.exceptionOrNull()?.message}", loginResult.isSuccess)
        val (_, profile) = loginResult.getOrThrow()
        assertEquals("ADMIN", profile.role)
        assertEquals("password", profile.provider)
    }

    @Test
    fun signInWithEmail_legacyAdminDocWithEmptyUid_succeedsAndPreservesAdminRole() = runBlocking {
        val (_, authRepo) = newRepositories()
        val email = "legacy_admin_${UUID.randomUUID()}@youthverse.test"

        val createdUser = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signUpWithEmail(email, "LegacyAdmin123!", "LegacyAdmin123!").getOrThrow().user
            }
        }

        withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signOut(credentialManager = null)
                val adminUser = auth.signInWithEmailAndPassword("admin@test.com", "password123").await().user!!
                adminUser.getIdToken(true).await()
                firestore.collection("users").document(createdUser.uid).update(
                    mapOf(
                        "uid" to "",
                        "role" to "ADMIN",
                        "provider" to "",
                        "preferredLanguage" to ""
                    )
                ).await()
                val updatedDoc = firestore.collection("users").document(createdUser.uid).get(com.google.firebase.firestore.Source.SERVER).await()
                assertEquals("ADMIN", updatedDoc.getString("role"))
                authRepo.signOut(credentialManager = null)
            }
        }

        val loginResult = withContext(Dispatchers.IO) {
            withTimeout(TEST_TIMEOUT_MS) {
                authRepo.signInWithEmail(email, "LegacyAdmin123!", preferredLanguage = "en")
            }
        }

        assertTrue("Legacy empty-uid ADMIN email login should succeed: ${loginResult.exceptionOrNull()?.message}", loginResult.isSuccess)
        val (_, profile) = loginResult.getOrThrow()
        assertEquals("ADMIN", profile.role)
        assertEquals("password", profile.provider)
        assertEquals("en", profile.preferredLanguage)
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 10000L
    }
}
