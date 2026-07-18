package com.wall.mob

import android.content.Context
import android.content.SharedPreferences
import com.wall.mob.*
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
            val resp = api.getFeed(requireAdminUserId(), page, perPage, category)
            if (!resp.isSuccessful) error("Feed request failed: ${resp.code()}")
            resp.body() ?: error("Empty feed response")
        }

    suspend fun getVideo(id: String): Result<ReelVideo> = runCatching {
        val resp = api.getVideo(id, requireAdminUserId())
        resp.body()?.data ?: error("Video not found")
    }

    suspend fun recordView(id: String) {
        val userId = if (isAdminUser()) loggedUserId() ?: return else return
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
        val userId = requireLoggedUserId()
        val token = ownerToken(id) ?: error("No owner token for this device")
        val resp = api.deleteVideo(id, userId, token)
        if (!resp.isSuccessful) error("Delete failed: ${resp.code()}")
    }

    suspend fun getComments(id: String, page: Int = 1, perPage: Int = 20): Result<CommentsResponse> =
        runCatching { api.getComments(id, requireAdminUserId(), page, perPage).body() ?: error("No comments response") }

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
}
