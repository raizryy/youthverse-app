package com.aistudio.youthverse.rvzkmq.data.repository

import android.content.Context
import android.util.Log
import com.aistudio.youthverse.rvzkmq.R
import com.aistudio.youthverse.rvzkmq.data.OperationType
import com.aistudio.youthverse.rvzkmq.data.handleFirestoreError
import com.aistudio.youthverse.rvzkmq.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class ProfileSyncException(
    val operation: String,
    val firestoreCode: FirebaseFirestoreException.Code?,
    cause: Throwable
) : Exception(cause.message ?: "Profile sync failed at $operation", cause) {
    val isPermissionDenied: Boolean
        get() = firestoreCode == FirebaseFirestoreException.Code.PERMISSION_DENIED
}

class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val profileSyncMutex = Mutex()

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

    suspend fun getOrCreateProfile(preferredLanguageOverride: String? = null): Result<UserProfile> =
        profileSyncMutex.withLock {
            val user = auth.currentUser ?: return@withLock Result.failure(IllegalStateException("Not authenticated"))
            val uid = user.uid
            val docRef = db.collection("users").document(uid)
            val resolvedProvider = resolveProvider(user)

        Log.d(TAG, "stage=FIRESTORE_PROFILE_GET status=START provider=$resolvedProvider")
        val snapshot = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fsCode = extractFirestoreCode(e)
            Log.e(TAG, "stage=FIRESTORE_PROFILE_GET status=FAILED firestoreCode=${fsCode?.name ?: e::class.simpleName}")
            handleFirestoreError(e, OperationType.GET, docRef.path)
            return@withLock Result.failure(ProfileSyncException(operation = "get", firestoreCode = fsCode, cause = e))
        }

        try {
            if (snapshot.exists()) {
                Log.d(TAG, "stage=FIRESTORE_PROFILE_GET status=EXISTS")
                val profile = snapshotToUserProfile(snapshot, uid, user.email.orEmpty())

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
                    Log.d(TAG, "stage=FIRESTORE_PROFILE_UPDATE status=START fields=${updates.keys.sorted().joinToString(",")}")
                    try {
                        docRef.update(updates).await()
                        appliedUpdates = true
                        Log.d(TAG, "stage=FIRESTORE_PROFILE_UPDATE status=SUCCESS")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val fsCode = extractFirestoreCode(e)
                        Log.w(
                            TAG,
                            "stage=FIRESTORE_PROFILE_UPDATE status=NON_FATAL_FAILURE firestoreCode=${fsCode?.name ?: e::class.simpleName}; continuing with existing profile"
                        )
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
                Log.d(TAG, "stage=FIRESTORE_PROFILE_CREATE status=START provider=$resolvedProvider")
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
                try {
                    docRef.set(payload).await()
                    Log.d(TAG, "stage=FIRESTORE_PROFILE_CREATE status=SUCCESS")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val fsCode = extractFirestoreCode(e)
                    Log.e(TAG, "stage=FIRESTORE_PROFILE_CREATE status=FAILED firestoreCode=${fsCode?.name ?: e::class.simpleName}")
                    handleFirestoreError(e, OperationType.CREATE, docRef.path)
                    return@withLock Result.failure(ProfileSyncException(operation = "create", firestoreCode = fsCode, cause = e))
                }
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
            val fsCode = extractFirestoreCode(e)
            Log.e(TAG, "stage=FIRESTORE_PROFILE_SYNC status=FAILED error=${e::class.simpleName}")
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(ProfileSyncException(operation = "sync", firestoreCode = fsCode, cause = e))
        }
    }

    private fun snapshotToUserProfile(
        snapshot: DocumentSnapshot,
        fallbackUid: String,
        fallbackEmail: String
    ): UserProfile {
        return runCatching {
            snapshot.toObject(UserProfile::class.java)
        }.onFailure { err ->
            Log.w(TAG, "stage=FIRESTORE_PROFILE_PARSE status=FALLBACK error=${err::class.simpleName}")
        }.getOrNull() ?: UserProfile(
            id = snapshot.id,
            uid = runCatching { snapshot.getString("uid") }.getOrNull().orEmpty().ifBlank { fallbackUid },
            displayName = runCatching { snapshot.getString("displayName") }.getOrNull().orEmpty(),
            email = runCatching { snapshot.getString("email") }.getOrNull().orEmpty().ifBlank { fallbackEmail },
            photoUrl = runCatching { snapshot.getString("photoUrl") }.getOrNull().orEmpty(),
            provider = runCatching { snapshot.getString("provider") }.getOrNull().orEmpty(),
            role = runCatching { snapshot.getString("role") }.getOrNull().orEmpty().ifBlank { "USER" },
            preferredLanguage = runCatching { snapshot.getString("preferredLanguage") }.getOrNull().orEmpty(),
            createdAt = runCatching { snapshot.getTimestamp("createdAt") }.getOrNull(),
            updatedAt = runCatching { snapshot.getTimestamp("updatedAt") }.getOrNull()
        )
    }

    private fun extractFirestoreCode(error: Throwable): FirebaseFirestoreException.Code? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
            ?.code
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
                Log.e(TAG, "stage=FIRESTORE_PROFILE_OBSERVE status=FAILED firestoreCode=${error.code.name}")
                handleFirestoreError(error, OperationType.GET, docRef.path)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshotToUserProfile(snapshot, uid, auth.currentUser?.email.orEmpty())
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
            Log.d(TAG, "stage=FIRESTORE_LANGUAGE_UPDATE status=SUCCESS lang=$lang")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fsCode = extractFirestoreCode(e)
            Log.e(TAG, "stage=FIRESTORE_LANGUAGE_UPDATE status=FAILED firestoreCode=${fsCode?.name ?: e::class.simpleName}")
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(ProfileSyncException(operation = "updateLanguage", firestoreCode = fsCode, cause = e))
        }
    }

    companion object {
        private const val TAG = "YouthVerseProfile"
    }
}
