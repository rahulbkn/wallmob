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

    private val api = RetrofitClient.api
    private val prefs: SharedPreferences =
        context.getSharedPreferences("reels_prefs", Context.MODE_PRIVATE)

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
            val resp = api.getFeed(page, perPage, category)
            if (!resp.isSuccessful) error("Feed request failed: ${resp.code()}")
            resp.body() ?: error("Empty feed response")
        }

    suspend fun getVideo(id: String): Result<ReelVideo> = runCatching {
        val resp = api.getVideo(id)
        resp.body()?.data ?: error("Video not found")
    }

    suspend fun recordView(id: String) {
        runCatching { api.recordView(id) }
    }

    suspend fun like(id: String): Result<CountedResponse> = runCatching {
        val body = InteractionRequest(deviceId())
        api.likeVideo(id, deviceId(), body).body() ?: error("Like failed")
    }

    suspend fun share(id: String): Result<CountedResponse> = runCatching {
        val body = InteractionRequest(deviceId())
        api.shareVideo(id, deviceId(), body).body() ?: error("Share failed")
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        val token = ownerToken(id) ?: error("No owner token for this device")
        val resp = api.deleteVideo(id, token)
        if (!resp.isSuccessful) error("Delete failed: ${resp.code()}")
    }

    suspend fun getComments(id: String, page: Int = 1, perPage: Int = 20): Result<CommentsResponse> =
        runCatching { api.getComments(id, page, perPage).body() ?: error("No comments response") }

    suspend fun addComment(id: String, author: String, text: String): Result<Comment> = runCatching {
        val body = AddCommentRequest(author, text, deviceId())
        api.addComment(id, deviceId(), body).body()?.data ?: error("Comment failed")
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

        val resp = api.uploadVideo(
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
