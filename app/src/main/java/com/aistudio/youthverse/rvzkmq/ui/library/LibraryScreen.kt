package com.aistudio.youthverse.rvzkmq.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.youthverse.rvzkmq.data.model.ContentItem
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.data.repository.LibraryRepository
import com.aistudio.youthverse.rvzkmq.data.repository.UserRepository
import com.aistudio.youthverse.rvzkmq.ui.components.CategoryChip
import com.aistudio.youthverse.rvzkmq.ui.components.ContentCard
import com.aistudio.youthverse.rvzkmq.ui.components.EmptyStateView
import com.aistudio.youthverse.rvzkmq.ui.components.YouthVerseTopBar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

@Composable
fun LibraryScreen(
    userRepository: UserRepository,
    contentRepository: ContentRepository,
    libraryRepository: LibraryRepository,
    onNavigateToContent: (String) -> Unit
) {
    val uid = userRepository.getCurrentUserId() ?: ""
    val libraryEntries by remember(uid) {
        if (uid.isNotBlank()) libraryRepository.observeUserLibrary(uid).catch { emit(emptyList()) }
        else flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val allContent by remember {
        contentRepository.observePublishedContent().catch { emit(emptyList()) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedTab by remember { mutableStateOf("ALL") }
    val tabs = listOf("ALL", "READING", "COMPLETED", "WANT_TO_READ", "FAVORITES")

    val filteredEntries = remember(libraryEntries, selectedTab) {
        when (selectedTab) {
            "ALL" -> libraryEntries
            "FAVORITES" -> libraryEntries.filter { it.isFavorite }
            else -> libraryEntries.filter { it.status == selectedTab }
        }
    }

    val contentMap = remember(allContent) {
        allContent.associateBy { it.id }
    }

    Scaffold(
        topBar = {
            YouthVerseTopBar(title = "My Library")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Library Tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { tab ->
                    CategoryChip(
                        label = when (tab) {
                            "ALL" -> "All (${libraryEntries.size})"
                            "READING" -> "Reading"
                            "COMPLETED" -> "Completed"
                            "WANT_TO_READ" -> "Want to Read"
                            "FAVORITES" -> "Favorites"
                            else -> tab
                        },
                        isSelected = selectedTab == tab,
                        onClick = { selectedTab = tab }
                    )
                }
            }

            if (filteredEntries.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Bookmark,
                    title = "Your Library is Empty",
                    subtitle = "Explore novels and comics to bookmark them and track your reading journey.",
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
                    items(filteredEntries, key = { it.id }) { entry ->
                        val contentItem = contentMap[entry.contentId] ?: ContentItem(
                            id = entry.contentId,
                            title = "Story"
                        )
                        ContentCard(
                            item = contentItem,
                            onClick = { onNavigateToContent(entry.contentId) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
