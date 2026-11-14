package com.aistudio.youthverse.rvzkmq.data.repository

import android.content.Context
import com.aistudio.youthverse.rvzkmq.R
import com.aistudio.youthverse.rvzkmq.data.OperationType
import com.aistudio.youthverse.rvzkmq.data.handleFirestoreError
import com.aistudio.youthverse.rvzkmq.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /**
     * Reads the server-issued Firebase Auth custom claim for UI visibility.
     * Firestore Security Rules remain the actual authorization boundary.
     */
    suspend fun isUserAdmin(forceRefresh: Boolean = false): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            user.getIdToken(forceRefresh).await().claims["admin"] == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getOrCreateProfile(preferredLanguageOverride: String? = null): Result<UserProfile> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val uid = user.uid
        val docRef = db.collection("users").document(uid)
        val resolvedProvider = resolveProvider(user)

        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                    ?: UserProfile(id = uid, uid = uid, email = user.email.orEmpty())

                // Preserve existing profile fields; only fill missing metadata or update changed email
                // without ever allowing client-controlled role or UID changes.
                val updates = mutableMapOf<String, Any>()
                val authName = user.displayName?.trim().orEmpty()
                val authEmail = user.email?.trim().orEmpty()
                val authPhoto = user.photoUrl?.toString().orEmpty()

                if (authName.isNotBlank() && (profile.displayName.isBlank() || profile.displayName == "Reader")) {
                    updates["displayName"] = authName.take(100)
                }
                if (authEmail.isNotBlank() && authEmail != profile.email) {
                    updates["email"] = authEmail.take(200)
                }
                if (authPhoto.isNotBlank() && profile.photoUrl.isBlank()) {
                    updates["photoUrl"] = authPhoto.take(500)
                }
                if (profile.provider.isBlank()) {
                    updates["provider"] = resolvedProvider
                }
                if (profile.preferredLanguage !in setOf("my", "en")) {
                    updates["preferredLanguage"] = if (preferredLanguageOverride in setOf("my", "en")) {
                        preferredLanguageOverride!!
                    } else {
                        "my"
                    }
                }

                var appliedUpdates = false
                if (updates.isNotEmpty()) {
                    updates["updatedAt"] = FieldValue.serverTimestamp()
                    try {
                        docRef.update(updates).await()
                        appliedUpdates = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                    }
                }
                Result.success(
                    if (appliedUpdates) {
                        profile.copy(
                            displayName = (updates["displayName"] as? String) ?: profile.displayName,
                            email = (updates["email"] as? String) ?: profile.email,
                            photoUrl = (updates["photoUrl"] as? String) ?: profile.photoUrl,
                            provider = (updates["provider"] as? String) ?: profile.provider,
                            preferredLanguage = (updates["preferredLanguage"] as? String) ?: profile.preferredLanguage
                        )
                    } else {
                        profile
                    }
                )
            } else {
                val initialDisplayName = user.displayName?.trim()?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Reader"
                val initialLang = if (preferredLanguageOverride in setOf("my", "en")) {
                    preferredLanguageOverride!!
                } else {
                    "my"
                }
                val initialEmail = user.email?.trim().orEmpty().take(200)
                val initialPhoto = user.photoUrl?.toString().orEmpty().take(500)

                val payload = hashMapOf<String, Any>(
                    "uid" to uid,
                    "displayName" to initialDisplayName.take(100),
                    "email" to initialEmail,
                    "photoUrl" to initialPhoto,
                    "provider" to resolvedProvider,
                    "role" to "USER", // Rules mandate role == 'USER' on client create
                    "preferredLanguage" to initialLang,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(
                    UserProfile(
                        id = uid,
                        uid = uid,
                        displayName = initialDisplayName.take(100),
                        email = initialEmail,
                        photoUrl = initialPhoto,
                        provider = resolvedProvider,
                        role = "USER",
                        preferredLanguage = initialLang
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    private fun resolveProvider(user: FirebaseUser): String {
        return if (user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }) {
            "google"
        } else {
            "password"
        }
    }

    fun observeProfile(): Flow<UserProfile?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val docRef = db.collection("users").document(uid)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, docRef.path)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                trySend(profile)
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun updateLanguage(lang: String): Result<Unit> {
        if (lang !in setOf("my", "en")) {
            return Result.failure(IllegalArgumentException("Unsupported language"))
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "preferredLanguage" to lang,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
