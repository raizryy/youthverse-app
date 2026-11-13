package com.aistudio.youthverse.rvzkmq.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.youthverse.rvzkmq.data.model.ContentItem
import com.aistudio.youthverse.rvzkmq.data.remote.CloudinaryService
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.ui.components.CategoryChip
import com.aistudio.youthverse.rvzkmq.ui.components.YouthVerseTopBar
import com.aistudio.youthverse.rvzkmq.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminContentEditScreen(
    contentId: String?,
    contentRepository: ContentRepository,
    onSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cloudinaryService = remember { CloudinaryService() }

    var title by remember { mutableStateOf("") }
    var altTitle by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var translator by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contentType by remember { mutableStateOf("NOVEL") }
    var selectedGenres by remember { mutableStateOf<Set<String>>(setOf("Fantasy")) }
    var coverUrl by remember { mutableStateOf("") }
    var isPublished by remember { mutableStateOf(true) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isUploadingImage = true
                errorMessage = null
                val result = cloudinaryService.uploadImage(context, uri, folder = "youthverse/covers")
                result.onSuccess { uploadedUrl ->
                    coverUrl = uploadedUrl
                    isUploadingImage = false
                }.onFailure { err ->
                    errorMessage = "Failed to upload cover: ${err.message}"
                    isUploadingImage = false
                }
            }
        }
    }

    LaunchedEffect(contentId) {
        if (!contentId.isNullOrBlank()) {
            val res = contentRepository.getContent(contentId)
            res.getOrNull()?.let { item ->
                title = item.title
                altTitle = item.alternativeTitle
                author = item.author
                translator = item.translator
                description = item.description
                contentType = item.contentType
                selectedGenres = item.genres.toSet()
                coverUrl = item.coverUrl
                isPublished = item.published
            }
        }
    }

    val availableGenres = listOf(
        "Fantasy", "Action", "Romance", "Cultivation", "Xianxia",
        "Wuxia", "Mystery", "Thriller", "Comedy", "Sci-Fi", "Reincarnation", "Supernatural"
    )

    Scaffold(
        topBar = {
            YouthVerseTopBar(
                title = if (contentId.isNullOrBlank()) "Publish New Title" else "Edit Content",
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Cover Image Upload Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "Cover Image",
                            modifier = Modifier
                                .width(130.dp)
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isUploadingImage && !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        modifier = Modifier.testTag("upload_cover_button")
                    ) {
                        if (isUploadingImage) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (coverUrl.isBlank()) "Upload Cover Image (Cloudinary)" else "Change Cover Image")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = coverUrl,
                        onValueChange = { coverUrl = it },
                        label = { Text("Or paste Image URL directly") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Title & Author Fields
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_title"),
                singleLine = true
            )

            OutlinedTextField(
                value = altTitle,
                onValueChange = { altTitle = it },
                label = { Text("Alternative Title / Myanmar Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author *") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_author"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = translator,
                    onValueChange = { translator = it },
                    label = { Text("Translator") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // Content Type Selector
            Text("Content Type *", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("NOVEL", "MANGA", "MANHWA", "MANHUA", "LIGHT_NOVEL").forEach { type ->
                    CategoryChip(
                        label = type,
                        isSelected = contentType == type,
                        onClick = { contentType = type }
                    )
                }
            }

            // Genres Selector
            Text("Select Genres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableGenres.forEach { genre ->
                    val selected = selectedGenres.contains(genre)
                    CategoryChip(
                        label = genre,
                        isSelected = selected,
                        onClick = {
                            selectedGenres = if (selected) selectedGenres - genre else selectedGenres + genre
                        }
                    )
                }
            }

            // Description / Synopsis
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Synopsis") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("input_description"),
                maxLines = 6
            )

            // Published Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Publication Status", fontWeight = FontWeight.Bold)
                    Text(
                        if (isPublished) "Published (Visible to readers)" else "Draft (Visible to admins only)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPublished,
                    onCheckedChange = { isPublished = it },
                    modifier = Modifier.testTag("switch_published")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save / Update Button
            Button(
                onClick = {
                    if (title.isBlank() || author.isBlank()) {
                        errorMessage = "Title and Author are required."
                        return@Button
                    }
                    isSaving = true
                    coroutineScope.launch {
                        val newItem = ContentItem(
                            id = contentId ?: "",
                            title = title.trim(),
                            alternativeTitle = altTitle.trim(),
                            author = author.trim(),
                            translator = translator.trim(),
                            description = description.trim(),
                            coverUrl = coverUrl.trim(),
                            thumbnailUrl = coverUrl.trim(),
                            contentType = contentType,
                            genres = selectedGenres.toList(),
                            published = isPublished
                        )
                        val saveResult = if (contentId.isNullOrBlank()) {
                            contentRepository.createContent(newItem)
                        } else {
                            contentRepository.updateContent(newItem)
                        }
                        isSaving = false
                        saveResult.onSuccess {
                            onSuccess()
                        }.onFailure { err ->
                            errorMessage = err.localizedMessage ?: "Failed to save content"
                        }
                    }
                },
                enabled = !isSaving && !isUploadingImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_content_button"),
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        if (contentId.isNullOrBlank()) "Publish Story" else "Save Changes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
