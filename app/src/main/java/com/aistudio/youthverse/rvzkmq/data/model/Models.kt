package com.aistudio.youthverse.rvzkmq.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val role: String = "USER", // "USER" or "ADMIN"
    val preferredLanguage: String = "my", // "my" or "en"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ContentItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val alternativeTitle: String = "",
    val description: String = "",
    val author: String = "",
    val translator: String = "",
    val coverUrl: String = "",
    val thumbnailUrl: String = "",
    val contentType: String = "NOVEL", // "NOVEL", "MANGA", "MANHUA", "MANHWA", "LIGHT_NOVEL"
    val genres: List<String> = emptyList(),
    val language: String = "my",
    val status: String = "ONGOING", // "ONGOING", "COMPLETED"
    val visibility: Boolean = true,
    val published: Boolean = true,
    val ratingAverage: Double = 0.0,
    val ratingCount: Long = 0L,
    val publishedChapterCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChapterItem(
    @DocumentId val id: String = "",
    val contentId: String = "",
    val chapterNumber: Double = 1.0,
    val title: String = "",
    val contentType: String = "TEXT", // "TEXT" or "IMAGE"
    val textContent: String = "",
    val imageUrls: List<String> = emptyList(),
    val published: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LibraryEntry(
    @DocumentId val id: String = "",
    val contentId: String = "",
    val userId: String = "",
    val status: String = "READING", // "READING", "COMPLETED", "WANT_TO_READ", "ON_HOLD"
    @get:PropertyName("isFavorite") @set:PropertyName("isFavorite")
    var isFavorite: Boolean = false,
    @get:PropertyName("isBookmarked") @set:PropertyName("isBookmarked")
    var isBookmarked: Boolean = false,
    val lastReadChapterId: String = "",
    val lastReadChapterNumber: Double = 1.0,
    val progressPercentage: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class HistoryEntry(
    @DocumentId val id: String = "",
    val contentId: String = "",
    val userId: String = "",
    val chapterId: String = "",
    val chapterNumber: Double = 1.0,
    val chapterTitle: String = "",
    val progressPercentage: Double = 0.0,
    val scrollPosition: Long = 0L,
    val pageIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Comment(
    @DocumentId val id: String = "",
    val contentId: String = "",
    val chapterId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Review(
    @DocumentId val id: String = "",
    val contentId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "",
    val rating: Double = 5.0,
    val reviewText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
