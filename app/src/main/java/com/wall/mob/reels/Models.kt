package com.wall.mob.reels

import com.google.gson.annotations.SerializedName

/**
 * Mirrors ClientVideoView in src/types/video.ts.
 * Note: videoUrl/thumbnailUrl are already fully-resolved absolute URLs
 * pointing at this server's own /stream endpoint — never a raw
 * Telegram/R2/Supabase URL. Just hand them straight to ExoPlayer/Glide.
 */
data class ReelVideo(
    val id: String,
    val title: String,
    val description: String? = null,
    val hashtags: List<String> = emptyList(),
    val category: String,
    val language: String? = null,
    val duration: Int,
    val width: Int,
    val height: Int,
    val uploader: String,
    val uploadDate: Long,
    val views: Int,
    var likes: Int,
    var comments: Int,
    var shares: Int,
    val videoUrl: String,
    val thumbnailUrl: String,
    val qualities: Map<String, String>? = null,
    val qualityMeta: Map<String, QualityMeta>? = null,
    val hasHls: Boolean? = false
)

data class QualityMeta(
    val bandwidth: Int,
    val width: Int,
    val height: Int
)

data class UploadVideoResult(
    val id: String,
    val title: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val ownerToken: String
)

data class Comment(
    val id: String,
    val videoId: String,
    val author: String,
    val text: String,
    val createdAt: Long
)

// ---- Envelope shapes the backend wraps every response in -----------------

data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)

data class FeedResponse(
    val success: Boolean,
    val items: List<ReelVideo> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val perPage: Int = 0
)

data class CommentsResponse(
    val success: Boolean,
    val items: List<Comment> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val perPage: Int = 0
)

data class CountedResponse(
    val success: Boolean,
    val counted: Boolean = false,
    val alreadyCounted: Boolean = false,
    val liked: Boolean? = null
)

data class SimpleSuccess(val success: Boolean)

data class AddCommentRequest(
    val author: String,
    val text: String,
    val deviceId: String,
    val userId: String
)

data class InteractionRequest(
    val deviceId: String,
    val userId: String
)

data class HealthResponse(
    val success: Boolean,
    val provider: String? = null
)
