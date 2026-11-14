package com.aistudio.youthverse.rvzkmq.repository

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.aistudio.youthverse.rvzkmq.data.repository.AuthRepository
import com.aistudio.youthverse.rvzkmq.data.repository.AuthValidator
import com.aistudio.youthverse.rvzkmq.data.repository.ProfileSyncException
import com.aistudio.youthverse.rvzkmq.data.repository.UserRepository
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [36])
class AuthValidationAndErrorTest {

    @Test
    fun emailValidation_rejectsEmptyAndMalformedEmails() {
        assertNotNull(AuthValidator.validateEmail(""))
        assertNotNull(AuthValidator.validateEmail("   "))
        assertNotNull(AuthValidator.validateEmail("invalid-email"))
        assertNotNull(AuthValidator.validateEmail("user@"))
        assertNotNull(AuthValidator.validateEmail("@example.com"))
        assertNotNull(AuthValidator.validateEmail("user@domain"))
    }

    @Test
    fun emailValidation_acceptsValidEmails() {
        assertNull(AuthValidator.validateEmail("reader@youthverse.app"))
        assertNull(AuthValidator.validateEmail("  user.name+tag@example.co.uk  "))
    }

    @Test
    fun signUpValidation_enforcesPasswordLengthAndConfirmation() {
        val shortPassword = AuthValidator.validateSignUpInput(
            email = "reader@youthverse.app",
            password = "short",
            confirmPassword = "short"
        )
        assertFalse(shortPassword.isValid)
        assertNotNull(shortPassword.passwordError)
        assertNull(shortPassword.confirmPasswordError)

        val mismatch = AuthValidator.validateSignUpInput(
            email = "reader@youthverse.app",
            password = "Password123!",
            confirmPassword = "Password456!"
        )
        assertFalse(mismatch.isValid)
        assertNull(mismatch.passwordError)
        assertEquals("Passwords do not match.", mismatch.confirmPasswordError)

        val emptyConfirm = AuthValidator.validateSignUpInput(
            email = "reader@youthverse.app",
            password = "Password123!",
            confirmPassword = ""
        )
        assertFalse(emptyConfirm.isValid)
        assertNotNull(emptyConfirm.confirmPasswordError)

        val valid = AuthValidator.validateSignUpInput(
            email = "reader@youthverse.app",
            password = "Password123!",
            confirmPassword = "Password123!"
        )
        assertTrue(valid.isValid)
        assertNull(valid.firstError)
    }

    @Test
    fun signInValidation_rejectsEmptyFieldsAndAcceptsValidInput() {
        val empty = AuthValidator.validateSignInInput("", "")
        assertFalse(empty.isValid)
        assertNotNull(empty.emailError)
        assertNotNull(empty.passwordError)

        val valid = AuthValidator.validateSignInInput("reader@youthverse.app", "Password123!")
        assertTrue(valid.isValid)
    }

    @Test
    fun errorMapping_handlesGoogleCredentialAndFirebaseExceptions() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val app = if (com.google.firebase.FirebaseApp.getApps(ctx).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(
                ctx,
                com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId("1:197254768036:android:4427b759cca729c17d8d31")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-test")
                    .build()
            )
        } else {
            com.google.firebase.FirebaseApp.getInstance()
        }
        val repo = AuthRepository(
            auth = FirebaseAuth.getInstance(app),
            userRepository = UserRepository(
                db = com.google.firebase.firestore.FirebaseFirestore.getInstance(app, "(default)"),
                auth = FirebaseAuth.getInstance(app)
            )
        )

        assertTrue(repo.mapAuthException(NoCredentialException()).contains("No Google account"))
        assertTrue(repo.mapAuthException(GetCredentialCancellationException()).contains("cancelled"))
        assertTrue(repo.mapAuthException(GetCredentialProviderConfigurationException()).contains("Play services"))
        assertTrue(repo.mapAuthException(GetCredentialUnsupportedException()).contains("not supported"))
        assertTrue(repo.mapAuthException(GetCredentialInterruptedException()).contains("interrupted"))
        assertTrue(repo.mapAuthException(GoogleIdTokenParsingException(RuntimeException("bad token"))).contains("token"))
        assertTrue(
            repo.mapAuthException(GetCredentialCustomException("TYPE", "10: DEVELOPER_ERROR"))
                .contains("SHA-1")
        )
        assertTrue(
            repo.mapAuthException(FirebaseAuthException("ERROR_WRONG_PASSWORD", "Bad pass"))
                .contains("Incorrect email or password")
        )
        assertTrue(
            repo.mapAuthException(FirebaseAuthException("ERROR_EMAIL_ALREADY_IN_USE", "In use"))
                .contains("already exists")
        )
        assertTrue(
            repo.mapAuthException(FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "Disabled"))
                .contains("not enabled")
        )

        val fsDenied = FirebaseFirestoreException("PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val profileDenied = ProfileSyncException("get", FirebaseFirestoreException.Code.PERMISSION_DENIED, fsDenied)
        val deniedMsg = repo.mapAuthException(profileDenied)
        assertTrue(deniedMsg.contains("Firebase Auth") && deniedMsg.contains("Firestore"))

        val fsUnavailable = FirebaseFirestoreException("UNAVAILABLE", FirebaseFirestoreException.Code.UNAVAILABLE)
        val profileSyncFail = ProfileSyncException("create", FirebaseFirestoreException.Code.UNAVAILABLE, fsUnavailable)
        val syncMsg = repo.mapAuthException(profileSyncFail)
        assertTrue(syncMsg.contains("profile synchronization failed"))
    }
}
