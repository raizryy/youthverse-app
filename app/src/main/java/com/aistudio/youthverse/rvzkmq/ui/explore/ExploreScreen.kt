package com.aistudio.youthverse.rvzkmq.ui.explore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.ui.components.CategoryChip
import com.aistudio.youthverse.rvzkmq.ui.components.ContentCard
import com.aistudio.youthverse.rvzkmq.ui.components.EmptyStateView
import com.aistudio.youthverse.rvzkmq.ui.components.YouthVerseTopBar
import com.aistudio.youthverse.rvzkmq.ui.theme.CyanAccent
import kotlinx.coroutines.flow.catch

@Composable
fun ExploreScreen(
    contentRepository: ContentRepository,
    onNavigateToContent: (String) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    var selectedType by remember { mutableStateOf("ALL") }
    var selectedGenre by remember { mutableStateOf("ALL") }

    val contentList by remember(selectedType, selectedGenre) {
        contentRepository.observePublishedContent(
            contentType = if (selectedType == "ALL") null else selectedType,
            genre = if (selectedGenre == "ALL") null else selectedGenre
        ).catch { emit(emptyList()) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val contentTypes = listOf("ALL", "NOVEL", "MANGA", "MANHWA", "MANHUA", "LIGHT_NOVEL")
    val genres = listOf(
        "ALL", "Fantasy", "Action", "Romance", "Cultivation", "Xianxia",
        "Wuxia", "Mystery", "Thriller", "Comedy", "Sci-Fi", "Reincarnation", "Supernatural"
    )

    Scaffold(
        topBar = {
            YouthVerseTopBar(
                title = "Explore & Discover",
                actions = {
                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.testTag("explore_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = CyanAccent
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Content Type Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contentTypes) { type ->
                    CategoryChip(
                        label = if (type == "ALL") "All Types" else type,
                        isSelected = selectedType == type,
                        onClick = { selectedType = type }
                    )
                }
            }

            // Genre Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genres) { genre ->
                    CategoryChip(
                        label = genre,
                        isSelected = selectedGenre == genre,
                        onClick = { selectedGenre = genre }
                    )
                }
            }

            if (contentList.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.AutoStories,
                    title = "No Matches Found",
                    subtitle = "No published stories match the selected criteria.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(contentList, key = { it.id }) { item ->
                        ContentCard(
                            item = item,
                            onClick = { onNavigateToContent(item.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
