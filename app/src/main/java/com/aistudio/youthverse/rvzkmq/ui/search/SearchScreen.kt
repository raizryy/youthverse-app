package com.aistudio.youthverse.rvzkmq.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.ui.components.ContentCard
import com.aistudio.youthverse.rvzkmq.ui.components.EmptyStateView
import com.aistudio.youthverse.rvzkmq.ui.components.YouthVerseTopBar
import kotlinx.coroutines.flow.catch

@Composable
fun SearchScreen(
    contentRepository: ContentRepository,
    onNavigateToContent: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allPublished by remember {
        contentRepository.observePublishedContent().catch { emit(emptyList()) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val filteredResults = remember(searchQuery, allPublished) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) {
            allPublished
        } else {
            allPublished.filter { item ->
                item.title.lowercase().contains(query) ||
                item.alternativeTitle.lowercase().contains(query) ||
                item.author.lowercase().contains(query) ||
                item.translator.lowercase().contains(query) ||
                item.genres.any { it.lowercase().contains(query) }
            }
        }
    }

    Scaffold(
        topBar = {
            YouthVerseTopBar(
                title = "Search Stories",
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_text_field"),
                placeholder = { Text("Search by title, author, genre...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )

            if (filteredResults.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Search,
                    title = "No Results Found",
                    subtitle = "No published titles matched '$searchQuery'. Try checking spelling or search a different genre.",
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
                    items(filteredResults, key = { it.id }) { item ->
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
