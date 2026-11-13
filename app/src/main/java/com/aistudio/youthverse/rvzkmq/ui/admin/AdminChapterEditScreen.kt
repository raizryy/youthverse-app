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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.youthverse.rvzkmq.data.model.ChapterItem
import com.aistudio.youthverse.rvzkmq.data.remote.CloudinaryService
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.ui.components.CategoryChip
import com.aistudio.youthverse.rvzkmq.ui.components.YouthVerseTopBar
import com.aistudio.youthverse.rvzkmq.ui.components.formatChapterNumber
import com.aistudio.youthverse.rvzkmq.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

@Composable
fun AdminChapterEditScreen(
    contentId: String,
    chapterId: String?,
    contentRepository: ContentRepository,
    onSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cloudinaryService = remember { CloudinaryService() }

    var chapterNumberText by remember { mutableStateOf("1") }
    var title by remember { mutableStateOf("") }
    var contentType by remember { mutableStateOf("TEXT") } // "TEXT" or "IMAGE"
    var textContent by remember { mutableStateOf("") }
    var imageUrlsText by remember { mutableStateOf("") }
    var isPublished by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isUploading = true
                errorMessage = null
                val newUrls = mutableListOf<String>()
                var failedCount = 0
                for (uri in uris) {
                    val res = cloudinaryService.uploadImage(context, uri, folder = "youthverse/chapters")
                    res.onSuccess { url -> newUrls.add(url) }
                        .onFailure { failedCount++ }
                }
                if (newUrls.isNotEmpty()) {
                    imageUrlsText = if (imageUrlsText.isBlank()) {
                        newUrls.joinToString("\n")
                    } else {
                        imageUrlsText + "\n" + newUrls.joinToString("\n")
                    }
                }
                if (failedCount > 0) {
                    errorMessage = "$failedCount page(s) failed to upload. Please retry."
                }
                isUploading = false
            }
        }
    }

    LaunchedEffect(contentId, chapterId) {
        if (!chapterId.isNullOrBlank()) {
            val res = contentRepository.getChapter(contentId, chapterId)
            res.getOrNull()?.let { ch ->
                chapterNumberText = ch.chapterNumber.formatChapterNumber()
                title = ch.title
                contentType = ch.contentType
                textContent = ch.textContent
                imageUrlsText = ch.imageUrls.joinToString("\n")
                isPublished = ch.published
            }
        }
    }

    Scaffold(
        topBar = {
            YouthVerseTopBar(
                title = if (chapterId.isNullOrBlank()) "Add Chapter" else "Edit Chapter",
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = chapterNumberText,
                    onValueChange = { chapterNumberText = it },
                    label = { Text("Chapter # *") },
                    modifier = Modifier
                        .width(100.dp)
                        .testTag("input_chapter_number"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chapter Title *") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_chapter_title"),
                    singleLine = true
                )
            }

            // Chapter Format Selection (Text for novels vs Image for comics)
            Text("Chapter Format *", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryChip(
                    label = "Text Novel Chapter",
                    isSelected = contentType == "TEXT",
                    onClick = { contentType = "TEXT" }
                )
                CategoryChip(
                    label = "Manga / Comic Images",
                    isSelected = contentType == "IMAGE",
                    onClick = { contentType = "IMAGE" }
                )
            }

            if (contentType == "TEXT") {
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    label = { Text("Chapter Text Content (Burmese / English Unicode) *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .testTag("input_chapter_text"),
                    maxLines = 20
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isUploading && !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload Comic Pages (Cloudinary)")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = imageUrlsText,
                            onValueChange = { imageUrlsText = it },
                            label = { Text("Ordered Image Page URLs (one per line)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            maxLines = 10
                        )
                    }
                }
            }

            // Publication Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Publish Immediately", fontWeight = FontWeight.Bold)
                    Text(
                        if (isPublished) "Published (Visible to readers)" else "Draft",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPublished,
                    onCheckedChange = { isPublished = it },
                    modifier = Modifier.testTag("switch_chapter_published")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val num = chapterNumberText.trim().toDoubleOrNull()
                    if (num == null || num < 0.0) {
                        errorMessage = "Please enter a valid chapter number (0 or greater)."
                        return@Button
                    }
                    if (title.isBlank()) {
                        errorMessage = "Chapter title is required."
                        return@Button
                    }
                    val parsedUrls = imageUrlsText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                    if (contentType == "TEXT" && textContent.isBlank()) {
                        errorMessage = "Text content is required for novel chapters."
                        return@Button
                    }
                    if (contentType == "IMAGE" && parsedUrls.isEmpty()) {
                        errorMessage = "At least one comic page image URL is required."
                        return@Button
                    }
                    errorMessage = null
                    isSaving = true
                    coroutineScope.launch {
                        val chapter = ChapterItem(
                            id = chapterId ?: "",
                            contentId = contentId,
                            chapterNumber = num,
                            title = title.trim(),
                            contentType = contentType,
                            textContent = textContent.trim(),
                            imageUrls = parsedUrls,
                            published = isPublished
                        )
                        val saveResult = if (chapterId.isNullOrBlank()) {
                            contentRepository.createChapter(chapter)
                        } else {
                            contentRepository.updateChapter(chapter)
                        }
                        isSaving = false
                        saveResult.onSuccess {
                            onSuccess()
                        }.onFailure { err ->
                            errorMessage = err.localizedMessage ?: "Failed to save chapter"
                        }
                    }
                },
                enabled = !isSaving && !isUploading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_chapter_button"),
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        if (chapterId.isNullOrBlank()) "Save & Publish Chapter" else "Save Changes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
