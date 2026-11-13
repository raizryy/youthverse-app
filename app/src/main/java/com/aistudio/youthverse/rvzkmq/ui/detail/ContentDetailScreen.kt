package com.aistudio.youthverse.rvzkmq.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aistudio.youthverse.rvzkmq.data.model.ContentItem
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.data.repository.LibraryRepository
import com.aistudio.youthverse.rvzkmq.data.repository.SocialRepository
import com.aistudio.youthverse.rvzkmq.data.repository.UserRepository
import com.aistudio.youthverse.rvzkmq.ui.components.YouthVerseTopBar
import com.aistudio.youthverse.rvzkmq.ui.components.formatChapterNumber
import com.aistudio.youthverse.rvzkmq.ui.theme.AmberGold
import com.aistudio.youthverse.rvzkmq.ui.theme.CyanAccent
import com.aistudio.youthverse.rvzkmq.ui.theme.DeepSpaceBackground
import com.aistudio.youthverse.rvzkmq.ui.theme.VioletPrimary
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ContentDetailScreen(
    contentId: String,
    userRepository: UserRepository,
    contentRepository: ContentRepository,
    libraryRepository: LibraryRepository,
    socialRepository: SocialRepository,
    onNavigateToReader: (String, String) -> Unit,
    onNavigateToAdminEdit: (String) -> Unit,
    onNavigateToAdminAddChapter: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uid = userRepository.getCurrentUserId() ?: ""
    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(uid) {
        isAdmin = userRepository.isUserAdmin(forceRefresh = false)
    }

    var contentItem by remember { mutableStateOf<ContentItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isUpdatingLibrary by remember { mutableStateOf(false) }

    val chapters by remember(contentId, isAdmin) {
        contentRepository.observeChapters(contentId, adminMode = isAdmin).catch { emit(emptyList()) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val reviews by remember(contentId) {
        socialRepository.observeReviews(contentId).catch { emit(emptyList()) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val libraryEntries by remember(uid) {
        if (uid.isNotBlank()) libraryRepository.observeUserLibrary(uid).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val currentEntry = remember(libraryEntries, contentId) {
        libraryEntries.firstOrNull { it.contentId == contentId }
    }

    var showReviewDialog by remember { mutableStateOf(false) }

    LaunchedEffect(contentId) {
        isLoading = true
        loadError = null
        val result = contentRepository.getContent(contentId)
        contentItem = result.getOrNull()
        if (result.isFailure) {
            loadError = "Unable to load story details. Please check your connection."
        }
        isLoading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            YouthVerseTopBar(
                title = contentItem?.title ?: "Story Details",
                onBackClick = onBackClick,
                actions = {
                    if (isAdmin && contentItem != null) {
                        IconButton(
                            onClick = { onNavigateToAdminEdit(contentId) },
                            modifier = Modifier.testTag("admin_edit_content_button")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Content", tint = VioletPrimary)
                        }
                        IconButton(
                            onClick = { onNavigateToAdminAddChapter(contentId) },
                            modifier = Modifier.testTag("admin_add_chapter_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Chapter", tint = CyanAccent)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = VioletPrimary)
            }
        } else if (contentItem == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = loadError ?: "Story not found or unpublished.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            val item = contentItem!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                // Header Banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        if (item.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = item.coverUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            DeepSpaceBackground.copy(alpha = 0.4f),
                                            DeepSpaceBackground
                                        )
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Cover thumbnail
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(8.dp),
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(160.dp)
                            ) {
                                if (item.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = item.coverUrl,
                                        contentDescription = item.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoStories,
                                            contentDescription = null,
                                            tint = VioletPrimary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = VioletPrimary
                                ) {
                                    Text(
                                        text = item.contentType,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (item.alternativeTitle.isNotBlank()) {
                                    Text(
                                        text = item.alternativeTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Author: ${item.author}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CyanAccent
                                )
                                if (item.translator.isNotBlank()) {
                                    Text(
                                        text = "Translator: ${item.translator}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons (Read / Resume / Add to Library)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (chapters.isNotEmpty()) {
                                    val targetChapter = if (!currentEntry?.lastReadChapterId.isNullOrBlank()) {
                                        chapters.firstOrNull { it.id == currentEntry?.lastReadChapterId } ?: chapters.first()
                                    } else {
                                        chapters.first()
                                    }
                                    onNavigateToReader(contentId, targetChapter.id)
                                }
                            },
                            enabled = chapters.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("start_reading_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentEntry?.lastReadChapterId?.isNotBlank() == true) "Resume Reading" else "Start Reading",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (!isUpdatingLibrary && uid.isNotBlank()) {
                                    coroutineScope.launch {
                                        isUpdatingLibrary = true
                                        val res = if (currentEntry != null) {
                                            libraryRepository.removeFromLibrary(uid, contentId)
                                        } else {
                                            libraryRepository.setLibraryStatus(
                                                userId = uid,
                                                contentId = contentId,
                                                status = "READING",
                                                isFavorite = false,
                                                isBookmarked = true
                                            )
                                        }
                                        isUpdatingLibrary = false
                                        if (res.isFailure) {
                                            snackbarHostState.showSnackbar("Unable to update library. Please try again.")
                                        }
                                    }
                                }
                            },
                            enabled = !isUpdatingLibrary && uid.isNotBlank(),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("library_toggle_button")
                        ) {
                            val activeAccent = if (currentEntry != null) VioletPrimary else MaterialTheme.colorScheme.onBackground
                            Icon(
                                imageVector = if (currentEntry != null) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                tint = activeAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentEntry != null) "In Library" else "Add to Library",
                                color = activeAccent
                            )
                        }
                    }
                }

                // Genre Chips
                if (item.genres.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            items(item.genres) { genre ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Synopsis / Description
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Synopsis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.description.ifBlank { "No synopsis provided." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }
                }

                // Chapter List Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chapters (${chapters.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Chapter items
                if (chapters.isEmpty()) {
                    item {
                        Text(
                            text = "No chapters published yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(chapters, key = { it.id }) { chapter ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { onNavigateToReader(contentId, chapter.id) }
                                .testTag("chapter_item_${chapter.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Chapter ${chapter.chapterNumber.formatChapterNumber()}: ${chapter.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!chapter.published) {
                                        Text(
                                            text = "DRAFT (Unpublished)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberGold
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = VioletPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Reviews Section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reader Reviews (${reviews.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(
                            onClick = { showReviewDialog = true },
                            modifier = Modifier.testTag("write_review_button")
                        ) {
                            Text("Write Review", color = VioletPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (reviews.isEmpty()) {
                    item {
                        Text(
                            text = "No reviews yet. Be the first reader to share your thoughts!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    items(reviews, key = { it.id }) { review ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = review.userName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = AmberGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = String.format(Locale.US, "%.1f", review.rating),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                if (review.reviewText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = review.reviewText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReviewDialog) {
        WriteReviewDialog(
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, text ->
                socialRepository.addReview(contentId, rating, text)
            }
        )
    }
}

@Composable
fun WriteReviewDialog(
    onDismiss: () -> Unit,
    onSubmit: suspend (Double, String) -> Result<String>
) {
    val coroutineScope = rememberCoroutineScope()
    var rating by remember { mutableStateOf(5.0) }
    var reviewText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Write a Review", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Your Rating:")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = { rating = star.toDouble() },
                            enabled = !isSubmitting
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "$star stars",
                                tint = if (star <= rating) AmberGold else Color.Gray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { if (it.length <= 2000) reviewText = it },
                    enabled = !isSubmitting,
                    placeholder = { Text("Write your review...") },
                    supportingText = { Text("${reviewText.length}/2000") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                submitError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isSubmitting) {
                        coroutineScope.launch {
                            isSubmitting = true
                            submitError = null
                            val res = onSubmit(rating, reviewText)
                            isSubmitting = false
                            if (res.isSuccess) {
                                onDismiss()
                            } else {
                                submitError = "Unable to submit review. Please try again."
                            }
                        }
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
            ) {
                Text(if (isSubmitting) "Submitting…" else "Submit Review")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}
