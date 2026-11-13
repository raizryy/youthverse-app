package com.aistudio.youthverse.rvzkmq.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.youthverse.rvzkmq.data.model.ChapterItem
import com.aistudio.youthverse.rvzkmq.data.model.ContentItem
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.data.repository.LibraryRepository
import com.aistudio.youthverse.rvzkmq.data.repository.UserRepository
import com.aistudio.youthverse.rvzkmq.ui.components.formatChapterNumber
import com.aistudio.youthverse.rvzkmq.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch

enum class ReaderTheme(val bgColor: Color, val textColor: Color, val nameLabel: String) {
    DARK(DeepSpaceBackground, TextPrimaryDark, "Dark"),
    AMOLED(Color.Black, TextPrimaryDark, "AMOLED"),
    SEPIA(SepiaBackground, SepiaText, "Sepia"),
    LIGHT(LightBackground, LightTextPrimary, "Light")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderScreen(
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

    // Reader custom preferences
    var fontSizeSp by remember { mutableStateOf(17f) }
    var selectedFontFamily by remember { mutableStateOf(LoraFontFamily) }
    var selectedFontName by remember { mutableStateOf("Lora") }
    var readerTheme by remember { mutableStateOf(ReaderTheme.DARK) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Observe published chapters once per contentId (avoids leaking listeners on chapter change)
    LaunchedEffect(contentId) {
        contentRepository.observeChapters(contentId)
            .catch { emit(emptyList()) }
            .collect { list ->
                allChapters = list
            }
    }

    // Fetch content header and current chapter, resetting scroll on chapter change
    LaunchedEffect(contentId, currentChapterId) {
        isLoading = true
        loadError = null
        if (contentItem == null) {
            contentItem = contentRepository.getContent(contentId).getOrNull()
        }
        val chapterResult = contentRepository.getChapter(contentId, currentChapterId)
        currentChapter = chapterResult.getOrNull()
        if (chapterResult.isFailure) {
            loadError = "Unable to load chapter. Please check your connection."
        }
        listState.scrollToItem(0)
        isLoading = false
    }

    // Save reading progress debounced on scroll
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems > 0 && currentChapter != null && uid.isNotBlank()) {
            val progress = ((listState.firstVisibleItemIndex.toFloat() / totalItems.toFloat()) * 100f).coerceIn(0f, 100f)
            delay(1000) // Debounce progress writes to save DB bandwidth
            libraryRepository.saveReadingProgress(
                userId = uid,
                contentId = contentId,
                chapterId = currentChapterId,
                chapterNumber = currentChapter?.chapterNumber ?: 1.0,
                chapterTitle = currentChapter?.title ?: "",
                progress = progress.toDouble(),
                scrollPosition = listState.firstVisibleItemIndex.toLong()
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
                            text = contentItem?.title ?: "Novel Reader",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTheme.textColor,
                            maxLines = 1
                        )
                        Text(
                            text = "Chapter ${currentChapter?.chapterNumber?.formatChapterNumber() ?: "1"}: ${currentChapter?.title ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = readerTheme.textColor.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = readerTheme.textColor
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("reader_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Reader Settings",
                            tint = readerTheme.textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = readerTheme.bgColor)
            )
        },
        containerColor = readerTheme.bgColor
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
        } else if (currentChapter == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = loadError ?: "Chapter not found or unpublished.",
                    color = readerTheme.textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            val paragraphs = remember(currentChapter?.textContent) {
                currentChapter?.textContent?.split("\n\n", "\n")?.filter { it.isNotBlank() } ?: emptyList()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Chapter Heading
                    item {
                        Text(
                            text = "Chapter ${currentChapter?.chapterNumber?.formatChapterNumber() ?: "1"}: ${currentChapter?.title ?: ""}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTheme.textColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Novel Paragraphs
                    items(paragraphs.size) { index ->
                        val paragraph = paragraphs[index]
                        Text(
                            text = paragraph,
                            color = readerTheme.textColor,
                            fontFamily = selectedFontFamily,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.6f).sp,
                            letterSpacing = 0.2.sp
                        )
                    }

                    // Chapter Navigation Footer
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    if (prevChapter != null) currentChapterId = prevChapter.id
                                },
                                enabled = prevChapter != null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("prev_chapter_button")
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Previous")
                            }

                            Button(
                                onClick = {
                                    if (nextChapter != null) currentChapterId = nextChapter.id
                                },
                                enabled = nextChapter != null,
                                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                                modifier = Modifier.testTag("next_chapter_button")
                            ) {
                                Text("Next Chapter")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }

    // Reader Settings Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Reader Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Font Size Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Font Size", color = MaterialTheme.colorScheme.onSurface)
                    Text("${fontSizeSp.toInt()} sp", color = VioletPrimary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = fontSizeSp,
                    onValueChange = { fontSizeSp = it },
                    valueRange = 13f..28f,
                    colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = VioletPrimary)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Font Family Selector
                Text("Font Family", color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fontOptions = listOf(
                        "Lora" to LoraFontFamily,
                        "Outfit" to OutfitFontFamily,
                        "Myanmar" to NotoSansMyanmarFontFamily
                    )
                    fontOptions.forEach { (name, family) ->
                        FilterChip(
                            selected = selectedFontName == name,
                            onClick = {
                                selectedFontName = name
                                selectedFontFamily = family
                            },
                            label = { Text(name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Theme Mode Selector (Dark, AMOLED, Sepia, Light)
                Text("Reading Theme", color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderTheme.entries.forEach { theme ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = theme.bgColor,
                            border = if (readerTheme == theme) {
                                androidx.compose.foundation.BorderStroke(2.dp, VioletPrimary)
                            } else {
                                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            },
                            onClick = { readerTheme = theme }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = theme.nameLabel,
                                    color = theme.textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
