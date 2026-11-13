package com.aistudio.youthverse.rvzkmq.data.repository

import android.content.Context
import com.aistudio.youthverse.rvzkmq.R
import com.aistudio.youthverse.rvzkmq.data.OperationType
import com.aistudio.youthverse.rvzkmq.data.handleFirestoreError
import com.aistudio.youthverse.rvzkmq.data.model.ChapterItem
import com.aistudio.youthverse.rvzkmq.data.model.ContentItem
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ContentRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observePublishedContent(contentType: String? = null, genre: String? = null): Flow<List<ContentItem>> = callbackFlow {
        var query: Query = db.collection("content")
            .whereEqualTo("published", true)
            .whereEqualTo("visibility", true)

        if (!contentType.isNullOrBlank() && contentType != "ALL") {
            query = query.whereEqualTo("contentType", contentType)
        }
        if (!genre.isNullOrBlank() && genre != "ALL") {
            query = query.whereArrayContains("genres", genre)
        }

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "content")
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.toObjects(ContentItem::class.java) ?: emptyList()
            trySend(items)
        }
        awaitClose { registration.remove() }
    }

    fun observeAllContentForAdmin(): Flow<List<ContentItem>> = callbackFlow {
        val query = db.collection("content")
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "content")
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.toObjects(ContentItem::class.java) ?: emptyList()
            trySend(items)
        }
        awaitClose { registration.remove() }
    }

    suspend fun getContent(id: String): Result<ContentItem?> {
        val docRef = db.collection("content").document(id)
        return try {
            val snapshot = docRef.get().await()
            val item = snapshot.toObject(ContentItem::class.java)
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observeChapters(contentId: String, adminMode: Boolean = false): Flow<List<ChapterItem>> = callbackFlow {
        var query = db.collection("content").document(contentId).collection("chapters")
            .orderBy("chapterNumber", Query.Direction.ASCENDING)

        if (!adminMode) {
            query = query.whereEqualTo("published", true)
        }

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "content/$contentId/chapters")
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.toObjects(ChapterItem::class.java) ?: emptyList()
            trySend(items)
        }
        awaitClose { registration.remove() }
    }

    suspend fun getChapter(contentId: String, chapterId: String): Result<ChapterItem?> {
        val docRef = db.collection("content").document(contentId).collection("chapters").document(chapterId)
        return try {
            val snapshot = docRef.get().await()
            val item = snapshot.toObject(ChapterItem::class.java)
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createContent(item: ContentItem): Result<String> {
        val docRef = db.collection("content").document()
        val payload = hashMapOf<String, Any>(
            "title" to item.title,
            "alternativeTitle" to item.alternativeTitle,
            "description" to item.description,
            "author" to item.author,
            "translator" to item.translator,
            "coverUrl" to item.coverUrl,
            "thumbnailUrl" to item.thumbnailUrl,
            "contentType" to item.contentType,
            "genres" to item.genres,
            "language" to item.language,
            "status" to item.status,
            "visibility" to item.visibility,
            "published" to item.published,
            "ratingAverage" to item.ratingAverage,
            "ratingCount" to item.ratingCount,
            "publishedChapterCount" to item.publishedChapterCount,
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

    suspend fun updateContent(item: ContentItem): Result<Unit> {
        if (item.id.isBlank()) return Result.failure(IllegalArgumentException("Item ID is required for update"))
        val docRef = db.collection("content").document(item.id)
        val payload = hashMapOf<String, Any>(
            "title" to item.title,
            "alternativeTitle" to item.alternativeTitle,
            "description" to item.description,
            "author" to item.author,
            "translator" to item.translator,
            "coverUrl" to item.coverUrl,
            "thumbnailUrl" to item.thumbnailUrl,
            "contentType" to item.contentType,
            "genres" to item.genres,
            "language" to item.language,
            "status" to item.status,
            "visibility" to item.visibility,
            "published" to item.published,
            "ratingAverage" to item.ratingAverage,
            "ratingCount" to item.ratingCount,
            "publishedChapterCount" to item.publishedChapterCount,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteContent(contentId: String): Result<Unit> {
        if (contentId.isBlank() || contentId.length > 128 || !contentId.matches(Regex("^[A-Za-z0-9_-]+$"))) {
            return Result.failure(IllegalArgumentException("A valid content ID is required"))
        }
        val docRef = db.collection("content").document(contentId)
        return try {
            FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("deleteContentCascade")
                .call(mapOf("contentId" to contentId)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createChapter(chapter: ChapterItem): Result<String> {
        if (chapter.contentId.isBlank()) {
            return Result.failure(IllegalArgumentException("Content ID is required"))
        }
        val parentRef = db.collection("content").document(chapter.contentId)
        val docRef = parentRef.collection("chapters").document()
        val payload = hashMapOf<String, Any>(
            "contentId" to chapter.contentId,
            "chapterNumber" to chapter.chapterNumber,
            "title" to chapter.title,
            "contentType" to chapter.contentType,
            "textContent" to chapter.textContent,
            "imageUrls" to chapter.imageUrls,
            "published" to chapter.published,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            db.runTransaction { transaction ->
                val parent = transaction.get(parentRef)
                require(parent.exists()) { "Parent content does not exist" }
                transaction.set(docRef, payload)
                if (chapter.published) {
                    transaction.update(parentRef, "publishedChapterCount", FieldValue.increment(1))
                }
                docRef.id
            }.await().let { Result.success(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateChapter(chapter: ChapterItem): Result<Unit> {
        if (chapter.id.isBlank() || chapter.contentId.isBlank()) {
            return Result.failure(IllegalArgumentException("Chapter and content IDs are required for update"))
        }
        val parentRef = db.collection("content").document(chapter.contentId)
        val docRef = parentRef.collection("chapters").document(chapter.id)
        val payload = hashMapOf<String, Any>(
            "chapterNumber" to chapter.chapterNumber,
            "title" to chapter.title,
            "contentType" to chapter.contentType,
            "textContent" to chapter.textContent,
            "imageUrls" to chapter.imageUrls,
            "published" to chapter.published,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            db.runTransaction { transaction ->
                val existing = transaction.get(docRef)
                require(existing.exists()) { "Chapter does not exist" }
                val wasPublished = existing.getBoolean("published") == true
                transaction.update(docRef, payload)
                if (wasPublished != chapter.published) {
                    val delta = if (chapter.published) 1L else -1L
                    transaction.update(parentRef, "publishedChapterCount", FieldValue.increment(delta))
                }
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteChapter(contentId: String, chapterId: String): Result<Unit> {
        if (contentId.isBlank() || chapterId.isBlank()) {
            return Result.failure(IllegalArgumentException("Content and chapter IDs are required"))
        }
        val parentRef = db.collection("content").document(contentId)
        val docRef = parentRef.collection("chapters").document(chapterId)
        return try {
            db.runTransaction { transaction ->
                val existing = transaction.get(docRef)
                if (existing.exists()) {
                    val wasPublished = existing.getBoolean("published") == true
                    transaction.delete(docRef)
                    if (wasPublished) {
                        transaction.update(parentRef, "publishedChapterCount", FieldValue.increment(-1))
                    }
                }
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

}
