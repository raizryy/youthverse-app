
package com.aistudio.youthverse.rvzkmq.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.functions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Signed Cloudinary uploads; the API secret never enters the Android process. */
class CloudinaryService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    suspend fun uploadImage(context: Context, imageUri: Uri, folder: String = "youthverse/covers"): Result<String> {
        val user = Firebase.auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in as an authorized administrator to upload media"))
        return try {
            val token = user.getIdToken(true).await()
            if (token.claims["admin"] != true) return Result.failure(SecurityException("Administrator permission is required"))
            if (folder !in setOf("youthverse/covers", "youthverse/chapters")) {
                return Result.failure(IllegalArgumentException("Invalid upload folder"))
            }
            val signed = Firebase.functions("us-central1").getHttpsCallable("createCloudinaryUploadSignature")
                .call(mapOf("folder" to folder)).await().data as? Map<*, *>
                ?: return Result.failure(IllegalStateException("Invalid upload authorization response"))
            val cloudName = signed["cloudName"] as? String ?: return Result.failure(IllegalStateException("Missing cloud name"))
            val apiKey = signed["apiKey"] as? String ?: return Result.failure(IllegalStateException("Missing API key"))
            val timestamp = (signed["timestamp"] as? Number)?.toLong() ?: return Result.failure(IllegalStateException("Missing timestamp"))
            val signedFolder = signed["folder"] as? String ?: return Result.failure(IllegalStateException("Missing folder"))
            val publicId = signed["publicId"] as? String ?: return Result.failure(IllegalStateException("Missing public ID"))
            val signature = signed["signature"] as? String ?: return Result.failure(IllegalStateException("Missing signature"))
            if (cloudName != "do95w9n91" || signedFolder != folder) return Result.failure(SecurityException("Upload authorization mismatch"))

            // ContentResolver reads can block; keep both URI I/O and the HTTP upload off Main.
            val (mime, bytes) = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val detectedMime = resolver.getType(imageUri)
                    ?: throw IllegalArgumentException("Unable to determine image type")
                require(detectedMime in setOf("image/jpeg", "image/png", "image/webp")) {
                    "Only JPEG, PNG and WebP are supported"
                }
                val stream = resolver.openInputStream(imageUri)
                    ?: throw IllegalArgumentException("Cannot open image")
                val imageBytes = stream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var totalBytes = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        require(totalBytes <= 10 * 1024 * 1024) { "Image exceeds 10 MB" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                detectedMime to imageBytes
            }
            withContext(Dispatchers.IO) {
                val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("api_key", apiKey).addFormDataPart("timestamp", timestamp.toString())
                    .addFormDataPart("folder", signedFolder).addFormDataPart("public_id", publicId)
                    .addFormDataPart("signature", signature)
                    .addFormDataPart("file", "$publicId.$ext", bytes.toRequestBody(mime.toMediaTypeOrNull())).build()
                val request = Request.Builder().url("https://api.cloudinary.com/v1_1/$cloudName/image/upload").post(body).build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) { Log.e("CloudinaryService", "Signed upload failed: HTTP ${response.code}"); return@withContext Result.failure(IllegalStateException("Cloudinary upload failed (${response.code})")) }
                    val url = JSONObject(text).optString("secure_url")
                    if (url.startsWith("https://res.cloudinary.com/$cloudName/")) Result.success(url)
                    else Result.failure(IllegalStateException("Unexpected Cloudinary media URL"))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Log.e("CloudinaryService", "Signed upload failed", e); Result.failure(e) }
    }
}
