package com.aistudio.youthverse.rvzkmq.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aistudio.youthverse.rvzkmq.data.model.ContentItem
import com.aistudio.youthverse.rvzkmq.data.model.HistoryEntry
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.data.repository.LibraryRepository
import com.aistudio.youthverse.rvzkmq.data.repository.UserRepository
import com.aistudio.youthverse.rvzkmq.ui.components.ContentCard
import com.aistudio.youthverse.rvzkmq.ui.components.EmptyStateView
import com.aistudio.youthverse.rvzkmq.ui.components.formatChapterNumber
import com.aistudio.youthverse.rvzkmq.ui.theme.CyanAccent
import com.aistudio.youthverse.rvzkmq.ui.theme.DeepSpaceBackground
import com.aistudio.youthverse.rvzkmq.ui.theme.VioletPrimary
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userRepository: UserRepository,
    contentRepository: ContentRepository,
    libraryRepository: LibraryRepository,
    onNavigateToContent: (String) -> Unit,
    onNavigateToReader: (String, String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToAdminCreate: () -> Unit
) {
    val uid = userRepository.getCurrentUserId() ?: ""
    val profile by remember(uid) {
        userRepository.observeProfile().catch { emit(null) }
    }.collectAsStateWithLifecycle(initialValue = null)
    val publishedContent by remember {
        contentRepository.observePublishedContent().catch { emit(emptyList()) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val historyEntries by remember(uid) {
        if (uid.isNotBlank()) libraryRepository.observeUserHistory(uid).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(uid) {
        isAdmin = userRepository.isUserAdmin(forceRefresh = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "YouthVerse",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val greeting = if (profile?.preferredLanguage == "my") {
                            "မင်္ဂလာပါ၊ ${profile?.displayName ?: "စာဖတ်သူ"}"
                        } else {
                            "Welcome, ${profile?.displayName ?: "Reader"}"
                        }
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.testTag("home_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = CyanAccent
                        )
                    }
                    if (isAdmin) {
                        IconButton(
                            onClick = onNavigateToAdminCreate,
                            modifier = Modifier.testTag("home_admin_add_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Content",
                                tint = VioletPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (publishedContent.isEmpty() && historyEntries.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.AutoStories,
                title = "The Universe Awaits Stories",
                subtitle = if (isAdmin) {
                    "No published stories in Firestore yet. As an authorized admin, tap below to publish the first novel or comic."
                } else {
                    "No stories published yet. Please check back soon as administrators release new novels and comics."
                },
                actionButtonText = if (isAdmin) "Publish New Title" else null,
                onActionClick = if (isAdmin) onNavigateToAdminCreate else null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            val novels = remember(publishedContent) {
                publishedContent.filter { it.contentType in listOf("NOVEL", "LIGHT_NOVEL") }
            }
            val comics = remember(publishedContent) {
                publishedContent.filter { it.contentType in listOf("MANGA", "MANHWA", "MANHUA") }
            }
            val latestReleases = remember(publishedContent) {
                publishedContent.sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Continue Reading Section
                if (historyEntries.isNotEmpty()) {
                    item {
                        SectionHeader(title = if (profile?.preferredLanguage == "my") "ဆက်လက်ဖတ်ရှုရန်" else "Continue Reading")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(historyEntries, key = { it.id }) { entry ->
                                ContinueReadingCard(
                                    entry = entry,
                                    onClick = {
                                        onNavigateToReader(entry.contentId, entry.chapterId)
                                    }
                                )
                            }
                        }
                    }
                }

                // Featured / Hero Banner
                if (publishedContent.isNotEmpty()) {
                    val featured = publishedContent.first()
                    item {
                        FeaturedBanner(
                            item = featured,
                            onClick = { onNavigateToContent(featured.id) }
                        )
                    }
                }

                // Trending Novels
                if (novels.isNotEmpty()) {
                    item {
                        SectionHeader(title = if (profile?.preferredLanguage == "my") "လူကြိုက်များသော ဝတ္ထုများ" else "Trending Novels")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(novels, key = { it.id }) { item ->
                                ContentCard(
                                    item = item,
                                    onClick = { onNavigateToContent(item.id) }
                                )
                            }
                        }
                    }
                }

                // Popular Manga, Manhwa, Manhua
                if (comics.isNotEmpty()) {
                    item {
                        SectionHeader(title = if (profile?.preferredLanguage == "my") "မန်ဂါနှင့် ကာတွန်းများ" else "Popular Manga & Comics")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(comics, key = { it.id }) { item ->
                                ContentCard(
                                    item = item,
                                    onClick = { onNavigateToContent(item.id) }
                                )
                            }
                        }
                    }
                }

                // Latest Releases
                if (publishedContent.size > 2) {
                    item {
                        SectionHeader(title = if (profile?.preferredLanguage == "my") "အသစ်ထွက်ရှိထားသော စာအုပ်များ" else "New Releases")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(latestReleases, key = { it.id }) { item ->
                                ContentCard(
                                    item = item,
                                    onClick = { onNavigateToContent(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun FeaturedBanner(
    item: ContentItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(200.dp)
            .clickable(onClick = onClick)
            .testTag("featured_banner_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSpaceBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, DeepSpaceBackground.copy(alpha = 0.95f)),
                            startY = 60f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = VioletPrimary
                ) {
                    Text(
                        text = "FEATURED ${item.contentType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    entry: HistoryEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick)
            .testTag("continue_reading_${entry.contentId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VioletPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = entry.chapterTitle.ifBlank { "Chapter ${entry.chapterNumber.formatChapterNumber()}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.0f", entry.progressPercentage)}% completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (entry.progressPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = CyanAccent,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
