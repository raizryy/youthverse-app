package com.aistudio.youthverse.rvzkmq.data.repository

import android.content.Context
import com.aistudio.youthverse.rvzkmq.R
import com.aistudio.youthverse.rvzkmq.data.OperationType
import com.aistudio.youthverse.rvzkmq.data.handleFirestoreError
import com.aistudio.youthverse.rvzkmq.data.model.HistoryEntry
import com.aistudio.youthverse.rvzkmq.data.model.LibraryEntry
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LibraryRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val currentUserId: String?
        get() = Firebase.auth.currentUser?.uid

    private fun ownsUserId(userId: String): Boolean = currentUserId == userId

    fun observeUserLibrary(userId: String): Flow<List<LibraryEntry>> = callbackFlow {
        if (!ownsUserId(userId)) {
            trySend(emptyList())
            close(SecurityException("Cannot observe another user's library"))
            return@callbackFlow
        }
        val query = db.collection("users").document(userId).collection("library")
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$userId/library")
                close(error)
                return@addSnapshotListener
            }
            val entries = snapshot?.toObjects(LibraryEntry::class.java) ?: emptyList()
            trySend(entries)
        }
        awaitClose { registration.remove() }
    }

    suspend fun getLibraryEntry(userId: String, contentId: String): Result<LibraryEntry?> {
        if (!ownsUserId(userId)) return Result.failure(SecurityException("Cannot access another user's library"))
        val docRef = db.collection("users").document(userId).collection("library").document(contentId)
        return try {
            val snapshot = docRef.get().await()
            val entry = snapshot.toObject(LibraryEntry::class.java)
            Result.success(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun setLibraryStatus(
        userId: String,
        contentId: String,
        status: String,
        isFavorite: Boolean? = null,
        isBookmarked: Boolean? = null
    ): Result<Unit> {
        if (!ownsUserId(userId)) return Result.failure(SecurityException("Cannot modify another user's library"))
        if (status !in setOf("READING", "COMPLETED", "WANT_TO_READ", "ON_HOLD")) {
            return Result.failure(IllegalArgumentException("Invalid library status"))
        }
        val docRef = db.collection("users").document(userId).collection("library").document(contentId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                isFavorite?.let { updates["isFavorite"] = it }
                isBookmarked?.let { updates["isBookmarked"] = it }
                docRef.update(updates).await()
            } else {
                val payload = hashMapOf<String, Any>(
                    "contentId" to contentId,
                    "userId" to userId,
                    "status" to status,
                    "isFavorite" to (isFavorite ?: false),
                    "isBookmarked" to (isBookmarked ?: false),
                    "lastReadChapterId" to "",
                    "lastReadChapterNumber" to 1.0,
                    "progressPercentage" to 0.0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeFromLibrary(userId: String, contentId: String): Result<Unit> {
        if (!ownsUserId(userId)) return Result.failure(SecurityException("Cannot remove another user's library entry"))
        val docRef = db.collection("users").document(userId).collection("library").document(contentId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserHistory(userId: String): Flow<List<HistoryEntry>> = callbackFlow {
        if (!ownsUserId(userId)) {
            trySend(emptyList())
            close(SecurityException("Cannot observe another user's history"))
            return@callbackFlow
        }
        val query = db.collection("users").document(userId).collection("history")
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$userId/history")
                close(error)
                return@addSnapshotListener
            }
            val entries = snapshot?.toObjects(HistoryEntry::class.java) ?: emptyList()
            trySend(entries)
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveReadingProgress(
        userId: String,
        contentId: String,
        chapterId: String,
        chapterNumber: Double,
        chapterTitle: String,
        progress: Double,
        scrollPosition: Long = 0L,
        pageIndex: Long = 0L
    ): Result<Unit> {
        if (!ownsUserId(userId)) return Result.failure(SecurityException("Cannot modify another user's reading history"))
        if (progress !in 0.0..100.0 || chapterNumber < 0.0 || scrollPosition < 0L || pageIndex < 0L) {
            return Result.failure(IllegalArgumentException("Reading progress values are out of range"))
        }
        val docRef = db.collection("users").document(userId).collection("history").document(contentId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "chapterId" to chapterId,
                        "chapterNumber" to chapterNumber,
                        "chapterTitle" to chapterTitle,
                        "progressPercentage" to progress,
                        "scrollPosition" to scrollPosition,
                        "pageIndex" to pageIndex,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = hashMapOf<String, Any>(
                    "contentId" to contentId,
                    "userId" to userId,
                    "chapterId" to chapterId,
                    "chapterNumber" to chapterNumber,
                    "chapterTitle" to chapterTitle,
                    "progressPercentage" to progress,
                    "scrollPosition" to scrollPosition,
                    "pageIndex" to pageIndex,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }

            // Also update library entry if present
            val libDoc = db.collection("users").document(userId).collection("library").document(contentId)
            val libSnap = libDoc.get().await()
            if (libSnap.exists()) {
                libDoc.update(
                    mapOf(
                        "lastReadChapterId" to chapterId,
                        "lastReadChapterNumber" to chapterNumber,
                        "progressPercentage" to progress,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeHistoryEntry(userId: String, contentId: String): Result<Unit> {
        if (!ownsUserId(userId)) return Result.failure(SecurityException("Cannot remove another user's history"))
        val docRef = db.collection("users").document(userId).collection("history").document(contentId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
