package com.aistudio.youthverse.rvzkmq.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.youthverse.rvzkmq.data.model.ChapterItem
import com.aistudio.youthverse.rvzkmq.data.model.ContentItem
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.data.repository.LibraryRepository
import com.aistudio.youthverse.rvzkmq.data.repository.UserRepository
import com.aistudio.youthverse.rvzkmq.ui.components.formatChapterNumber
import com.aistudio.youthverse.rvzkmq.ui.theme.CyanAccent
import com.aistudio.youthverse.rvzkmq.ui.theme.ElevatedSurfaceDark
import com.aistudio.youthverse.rvzkmq.ui.theme.VioletPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    contentId: String,
    initialChapterId: String,
    userRepository: UserRepository,
    contentRepository: ContentRepository,
    libraryRepository: LibraryRepository,
    onBackClick: () -> Unit
) {
    val uid = userRepository.getCurrentUserId() ?: ""

    var currentChapterId by remember(initialChapterId) { mutableStateOf(initialChapterId) }
    var currentChapter by remember { mutableStateOf<ChapterItem?>(null) }
    var contentItem by remember { mutableStateOf<ContentItem?>(null) }
    var allChapters by remember { mutableStateOf<List<ChapterItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Reading mode: Vertical Webtoon continuous scroll vs Horizontal page mode
    var isVerticalMode by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val images = currentChapter?.imageUrls ?: emptyList()
    val pagerState = rememberPagerState(pageCount = { images.size })

    LaunchedEffect(contentId) {
        contentRepository.observeChapters(contentId)
            .catch { emit(emptyList()) }
            .collect { list ->
                allChapters = list
            }
    }

    LaunchedEffect(contentId, currentChapterId) {
        isLoading = true
        loadError = null
        if (contentItem == null) {
            contentItem = contentRepository.getContent(contentId).getOrNull()
        }
        val chapterResult = contentRepository.getChapter(contentId, currentChapterId)
        currentChapter = chapterResult.getOrNull()
        if (chapterResult.isFailure) {
            loadError = "Unable to load chapter pages. Please check your connection."
        }
        listState.scrollToItem(0)
        if (pagerState.pageCount > 0) {
            pagerState.scrollToPage(0)
        }
        isLoading = false
    }

    // Save progress debounced
    LaunchedEffect(listState.firstVisibleItemIndex, pagerState.currentPage) {
        if (images.isNotEmpty() && currentChapter != null && uid.isNotBlank()) {
            val pageIndex = if (isVerticalMode) listState.firstVisibleItemIndex else pagerState.currentPage
            val progress = ((pageIndex.toFloat() / images.size.toFloat()) * 100f).coerceIn(0f, 100f)
            delay(1000)
            libraryRepository.saveReadingProgress(
                userId = uid,
                contentId = contentId,
                chapterId = currentChapterId,
                chapterNumber = currentChapter?.chapterNumber ?: 1.0,
                chapterTitle = currentChapter?.title ?: "",
                progress = progress.toDouble(),
                pageIndex = pageIndex.toLong()
            )
        }
    }

    val currentIndex = allChapters.indexOfFirst { it.id == currentChapterId }
    val prevChapter = if (currentIndex > 0) allChapters[currentIndex - 1] else null
    val nextChapter = if (currentIndex in 0 until allChapters.size - 1) allChapters[currentIndex + 1] else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = contentItem?.title ?: "Manga Reader",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = "Ch. ${currentChapter?.chapterNumber?.formatChapterNumber() ?: "1"}: ${currentChapter?.title ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("manga_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Toggle vertical/horizontal reading mode
                    IconButton(
                        onClick = { isVerticalMode = !isVerticalMode },
                        modifier = Modifier.testTag("toggle_reading_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isVerticalMode) Icons.Default.ViewAgenda else Icons.Default.ViewCarousel,
                            contentDescription = "Toggle Reading Mode",
                            tint = CyanAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
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
        } else if (currentChapter == null || images.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = loadError ?: "No chapter pages found.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isVerticalMode) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(images) { index, imageUrl ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }

                        // Chapter Switcher Footer
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = { if (prevChapter != null) currentChapterId = prevChapter.id },
                                    enabled = prevChapter != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurfaceDark)
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Previous")
                                }

                                Button(
                                    onClick = { if (nextChapter != null) currentChapterId = nextChapter.id },
                                    enabled = nextChapter != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                                ) {
                                    Text("Next Chapter")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                }
                            }
                        }
                    }
                } else {
                    // Horizontal page flip mode
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = images[page],
                                contentDescription = "Page ${page + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Floating Bottom Controls: Page Indicator + Horizontal Chapter Navigation
                val currentPageNumber = if (isVerticalMode) {
                    (listState.firstVisibleItemIndex + 1).coerceAtMost(images.size)
                } else {
                    pagerState.currentPage + 1
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isVerticalMode && prevChapter != null) {
                        FilledTonalIconButton(
                            onClick = { currentChapterId = prevChapter.id },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.75f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Chapter")
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = "Page $currentPageNumber / ${images.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (!isVerticalMode && nextChapter != null) {
                        FilledTonalIconButton(
                            onClick = { currentChapterId = nextChapter.id },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = VioletPrimary.copy(alpha = 0.9f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Chapter")
                        }
                    }
                }
            }
        }
    }
}
