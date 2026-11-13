package com.aistudio.youthverse.rvzkmq.data.repository

import android.content.Context
import com.aistudio.youthverse.rvzkmq.R
import com.aistudio.youthverse.rvzkmq.data.OperationType
import com.aistudio.youthverse.rvzkmq.data.handleFirestoreError
import com.aistudio.youthverse.rvzkmq.data.model.Comment
import com.aistudio.youthverse.rvzkmq.data.model.Review
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

class SocialRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    fun observeComments(contentId: String, chapterId: String? = null): Flow<List<Comment>> = callbackFlow {
        var query: Query = db.collection("comments")
            .whereEqualTo("contentId", contentId)

        if (!chapterId.isNullOrBlank()) {
            query = query.whereEqualTo("chapterId", chapterId)
        }
        query = query.orderBy("createdAt", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "comments")
                close(error)
                return@addSnapshotListener
            }
            val comments = snapshot?.toObjects(Comment::class.java) ?: emptyList()
            trySend(comments)
        }
        awaitClose { registration.remove() }
    }

    suspend fun addComment(contentId: String, chapterId: String? = null, text: String): Result<String> {
        val cleanText = text.trim()
        if (contentId.isBlank() || cleanText.isEmpty() || cleanText.length > 1000) {
            return Result.failure(IllegalArgumentException("Comment must contain 1–1000 characters"))
        }
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val docRef = db.collection("comments").document()
        val payload = hashMapOf<String, Any>(
            "contentId" to contentId,
            "chapterId" to (chapterId ?: ""),
            "userId" to user.uid,
            "userName" to (user.displayName ?: "Reader"),
            "userPhotoUrl" to (user.photoUrl?.toString() ?: ""),
            "text" to cleanText,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(commentId: String): Result<Unit> {
        val docRef = db.collection("comments").document(commentId)
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

    fun observeReviews(contentId: String): Flow<List<Review>> = callbackFlow {
        val query = db.collection("reviews")
            .whereEqualTo("contentId", contentId)
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "reviews")
                close(error)
                return@addSnapshotListener
            }
            val reviews = snapshot?.toObjects(Review::class.java) ?: emptyList()
            trySend(reviews)
        }
        awaitClose { registration.remove() }
    }

    suspend fun addReview(contentId: String, rating: Double, text: String): Result<String> {
        val cleanText = text.trim()
        if (contentId.isBlank() || rating !in 1.0..5.0 || cleanText.length > 2000) {
            return Result.failure(IllegalArgumentException("Review rating or text is invalid"))
        }
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val docRef = db.collection("reviews").document()
        val payload = hashMapOf<String, Any>(
            "contentId" to contentId,
            "userId" to user.uid,
            "userName" to (user.displayName ?: "Reader"),
            "userPhotoUrl" to (user.photoUrl?.toString() ?: ""),
            "rating" to rating,
            "reviewText" to cleanText,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
