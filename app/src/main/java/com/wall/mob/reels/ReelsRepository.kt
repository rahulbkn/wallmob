package com.wall.mob.reels

import android.content.Context
import android.content.SharedPreferences
import com.wall.mob.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

class ReelsRepository(context: Context) {

    private val adminUserIds = setOf("rahulkumarbknv@gmail.com")

    private val api = RetrofitClient.api
    private val appContext = context.applicationContext
    private val sessionManager = SessionManager(appContext)
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("reels_prefs", Context.MODE_PRIVATE)

    fun deviceId(): String {
        prefs.getString("device_id", null)?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString("device_id", id).apply()
        return id
    }

    fun saveOwnerToken(videoId: String, token: String) {
        prefs.edit().putString("owner_token_$videoId", token).apply()
    }

    fun ownerToken(videoId: String): String? = prefs.getString("owner_token_$videoId", null)

    fun isLiked(videoId: String): Boolean {
        val set = prefs.getStringSet("liked_ids", mutableSetOf()) ?: mutableSetOf()
        return set.contains(videoId)
    }

    fun markLiked(videoId: String) {
        val set = (prefs.getStringSet("liked_ids", mutableSetOf()) ?: mutableSetOf()).toMutableSet()
        set.add(videoId)
        prefs.edit().putStringSet("liked_ids", set).apply()
    }

    fun unmarkLiked(videoId: String) {
        val set = (prefs.getStringSet("liked_ids", mutableSetOf()) ?: mutableSetOf()).toMutableSet()
        set.remove(videoId)
        prefs.edit().putStringSet("liked_ids", set).apply()
    }

    fun isShared(videoId: String): Boolean {
        val set = prefs.getStringSet("shared_ids", mutableSetOf()) ?: mutableSetOf()
        return set.contains(videoId)
    }

    fun markShared(videoId: String) {
        val set = (prefs.getStringSet("shared_ids", mutableSetOf()) ?: mutableSetOf()).toMutableSet()
        set.add(videoId)
        prefs.edit().putStringSet("shared_ids", set).apply()
    }

    suspend fun health(): Result<HealthResponse> = runCatching {
        val resp = api.health()
        resp.body() ?: error("Health check failed")
    }

    suspend fun getFeed(page: Int = 1, perPage: Int = 10, category: String? = null): Result<FeedResponse> =
        runCatching {
            val userId = loggedUserId() ?: "unknown"
            val resp = api.getFeed(userId, page, perPage, category)
            if (!resp.isSuccessful) error("Feed request failed: ${resp.code()}")
            resp.body() ?: error("Empty feed response")
        }

    suspend fun getVideo(id: String): Result<ReelVideo> = runCatching {
        val userId = loggedUserId() ?: "unknown"
        val resp = api.getVideo(id, userId)
        resp.body()?.data ?: error("Video not found")
    }

    suspend fun recordView(id: String) {
        val userId = loggedUserId() ?: return
        runCatching { api.recordView(id, userId) }
    }

    fun loggedUserId(): String? {
        if (!sessionManager.isLoggedIn || sessionManager.isGuest) return null
        return sessionManager.email?.takeIf { it.isNotBlank() }
    }

    fun isAdminUser(): Boolean = loggedUserId()?.lowercase() in adminUserIds

    fun requireAdminUserId(): String {
        val userId = loggedUserId() ?: error("Please log in to continue")
        if (userId.lowercase() !in adminUserIds) error("Admin access is required")
        return userId
    }

    fun requireLoggedUserId(): String = requireAdminUserId()

    suspend fun like(id: String): Result<CountedResponse> = runCatching {
        val userId = requireLoggedUserId()
        val body = InteractionRequest(deviceId(), userId)
        api.likeVideo(id, deviceId(), userId, body).body() ?: error("Like failed")
    }

    suspend fun share(id: String): Result<CountedResponse> = runCatching {
        val userId = requireLoggedUserId()
        val body = InteractionRequest(deviceId(), userId)
        api.shareVideo(id, deviceId(), userId, body).body() ?: error("Share failed")
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        val userId = loggedUserId() ?: error("Please log in to continue")
        val token = ownerToken(id)
        val resp = api.deleteVideo(id, userId, token ?: "")
        if (!resp.isSuccessful) error("Delete failed: ${resp.code()}")
    }

    suspend fun getComments(id: String, page: Int = 1, perPage: Int = 20): Result<CommentsResponse> =
        runCatching {
            val userId = loggedUserId() ?: "unknown"
            api.getComments(id, userId, page, perPage).body() ?: error("No comments response")
        }

    suspend fun addComment(id: String, author: String, text: String): Result<Comment> = runCatching {
        val userId = requireLoggedUserId()
        val body = AddCommentRequest(author, text, deviceId(), userId)
        api.addComment(id, deviceId(), userId, body).body()?.data ?: error("Comment failed")
    }

    suspend fun uploadVideo(
        file: File,
        title: String,
        category: String,
        uploader: String,
        description: String? = null,
        hashtagsCsv: String? = null,
        language: String? = null
    ): Result<UploadVideoResult> = runCatching {
        val videoPart = MultipartBody.Part.createFormData(
            "video", file.name, file.asRequestBody("video/mp4".toMediaTypeOrNull())
        )
        fun text(v: String) = v.toRequestBody("text/plain".toMediaTypeOrNull())

        val userId = requireLoggedUserId()
        val resp = api.uploadVideo(
            userId = userId,
            video = videoPart,
            thumbnail = null,
            title = text(title),
            category = text(category),
            uploader = text(uploader),
            description = description?.let { text(it) },
            hashtags = hashtagsCsv?.let { text(it) },
            language = language?.let { text(it) }
        )
        val result = resp.body()?.data ?: error("Upload failed: ${resp.code()}")
        saveOwnerToken(result.id, result.ownerToken)
        result
    }

    suspend fun uploadVideoChunked(
        file: File,
        title: String,
        category: String,
        uploader: String,
        description: String? = null,
        hashtagsCsv: String? = null,
        onProgress: (percentage: Int, speedMBs: Double) -> Unit
    ): Result<UploadVideoResult> = runCatching {
        val userId = requireLoggedUserId()
        val totalSize = file.length()
        val chunkSize = 24 * 1024 * 1024L // 24 MB
        val totalChunks = if (totalSize == 0L) 1 else Math.ceil(totalSize.toDouble() / chunkSize).toInt()

        // 1. Init upload
        val initResp = api.initUpload(
            userId = userId,
            body = InitUploadRequest(
                title = title,
                description = description,
                uploader = uploader,
                category = category,
                hashtags = hashtagsCsv,
                fileName = file.name,
                fileSize = totalSize,
                mimeType = "video/mp4",
                totalChunks = totalChunks
            )
        )
        if (!initResp.isSuccessful) {
            error("Failed to initialize chunked upload: ${initResp.errorBody()?.string() ?: initResp.code()}")
        }
        val uploadId = initResp.body()?.uploadId ?: error("UploadId not returned")

        // 2. Upload chunks in parallel with progress tracking
        val bytesSentMap = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        val startTime = System.currentTimeMillis()

        // Use a Semaphore to control concurrency of uploads (e.g., 4 concurrent chunk uploads max)
        val semaphore = Semaphore(4)

        fun updateProgress() {
            val totalSent = bytesSentMap.values.sum()
            val percentage = ((totalSent.toDouble() / totalSize) * 100).toInt().coerceIn(0, 100)
            val timeElapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
            val speedMBs = if (timeElapsedSec > 0) (totalSent / 1024.0 / 1024.0) / timeElapsedSec else 0.0
            onProgress(percentage, speedMBs)
        }

        coroutineScope {
            val deferreds = (0 until totalChunks).map { index ->
                async {
                    semaphore.withPermit {
                        val offset = index * chunkSize
                        val length = Math.min(chunkSize, totalSize - offset).toInt()
                        val chunkBytes = ByteArray(length)

                        java.io.RandomAccessFile(file, "r").use { raf ->
                            raf.seek(offset)
                            raf.readFully(chunkBytes)
                        }

                        val requestBody = object : okhttp3.RequestBody() {
                            override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                            override fun contentLength() = length.toLong()
                            override fun writeTo(sink: okio.BufferedSink) {
                                var uploaded = 0L
                                val bufferSize = 4096
                                val buffer = ByteArray(bufferSize)
                                var read: Int
                                val ins = java.io.ByteArrayInputStream(chunkBytes)
                                while (ins.read(buffer).also { read = it } != -1) {
                                    sink.write(buffer, 0, read)
                                    uploaded += read
                                    bytesSentMap[index] = uploaded
                                    updateProgress()
                                }
                            }
                        }

                        val resp = api.uploadChunk(
                            userId = userId,
                            uploadId = uploadId,
                            index = index,
                            body = requestBody
                        )
                        if (!resp.isSuccessful) {
                            error("Failed to upload chunk $index: ${resp.errorBody()?.string() ?: resp.code()}")
                        }
                    }
                }
            }
            deferreds.awaitAll()
        }

        // 3. Complete upload
        val completeResp = api.completeUpload(
            userId = userId,
            body = CompleteUploadRequest(uploadId = uploadId)
        )
        val result = completeResp.body()?.data ?: error("Failed to complete upload: ${completeResp.errorBody()?.string() ?: completeResp.code()}")
        saveOwnerToken(result.id, result.ownerToken)
        result
    }

    /** Blocking wrapper for Java callers. */
    fun getFeedBlocking(page: Int = 1, perPage: Int = 10, category: String? = null): FeedResponse = runBlocking {
        getFeed(page, perPage, category).getOrElse { throw it }
    }

    /** Blocking wrapper for Java callers (e.g. UploadWallpaperActivity). */
    fun uploadVideoBlocking(
        file: File,
        title: String,
        category: String,
        uploader: String,
        description: String? = null,
        hashtagsCsv: String? = null,
        language: String? = null
    ): UploadVideoResult = runBlocking {
        uploadVideo(file, title, category, uploader, description, hashtagsCsv, language)
            .getOrElse { throw it }
    }
}
