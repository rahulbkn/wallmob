package com.wall.mob

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import com.wall.mob.*

/**
 * One-to-one mapping to src/routes/router.ts. Base URL is whatever
 * PUBLIC_BASE_URL / your deployed Worker URL is (e.g.
 * "https://reels-backend.<you>.workers.dev/") — set in RetrofitClient.
 */
interface ReelsApiService {

    // GET /api/videos?page=&perPage=&category=&uploader=
    @GET("api/videos")
    suspend fun getFeed(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 10,
        @Query("category") category: String? = null,
        @Query("uploader") uploader: String? = null
    ): Response<FeedResponse>

    // GET /api/videos/:id
    @GET("api/videos/{id}")
    suspend fun getVideo(@Path("id") id: String): Response<ApiEnvelope<ReelVideo>>

    // POST /api/videos/:id/view
    @POST("api/videos/{id}/view")
    suspend fun recordView(@Path("id") id: String): Response<SimpleSuccess>

    // POST /api/videos/:id/like  (body + X-Device-Id header, backend accepts either)
    @POST("api/videos/{id}/like")
    suspend fun likeVideo(
        @Path("id") id: String,
        @Header("X-Device-Id") deviceId: String,
        @Header("X-User-Id") userId: String,
        @Body body: InteractionRequest
    ): Response<CountedResponse>

    // POST /api/videos/:id/share
    @POST("api/videos/{id}/share")
    suspend fun shareVideo(
        @Path("id") id: String,
        @Header("X-Device-Id") deviceId: String,
        @Header("X-User-Id") userId: String,
        @Body body: InteractionRequest
    ): Response<CountedResponse>

    // DELETE /api/videos/:id?ownerToken=...
    @DELETE("api/videos/{id}")
    suspend fun deleteVideo(
        @Path("id") id: String,
        @Query("ownerToken") ownerToken: String
    ): Response<SimpleSuccess>

    // GET /api/videos/:id/comments?page=&perPage=
    @GET("api/videos/{id}/comments")
    suspend fun getComments(
        @Path("id") id: String,
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 20
    ): Response<CommentsResponse>

    // POST /api/videos/:id/comments
    @POST("api/videos/{id}/comments")
    suspend fun addComment(
        @Path("id") id: String,
        @Header("X-Device-Id") deviceId: String,
        @Header("X-User-Id") userId: String,
        @Body body: AddCommentRequest
    ): Response<ApiEnvelope<Comment>>

    // POST /api/videos  (multipart: video[, thumbnail], title, category, uploader, description, hashtags, language)
    @Multipart
    @POST("api/videos")
    suspend fun uploadVideo(
        @Header("X-User-Id") userId: String,
        @Part video: MultipartBody.Part,
        @Part thumbnail: MultipartBody.Part? = null,
        @Part("title") title: RequestBody,
        @Part("category") category: RequestBody,
        @Part("uploader") uploader: RequestBody,
        @Part("description") description: RequestBody? = null,
        @Part("hashtags") hashtags: RequestBody? = null,
        @Part("language") language: RequestBody? = null
    ): Response<ApiEnvelope<UploadVideoResult>>

    // GET /health
    @GET("health")
    suspend fun health(): Response<HealthResponse>

    // NOTE: /stream, /hls/master and /hls/variant are NOT called through
    // Retrofit — they're just URLs. Pass reel.videoUrl straight to
    // ExoPlayer's MediaItem.fromUri(), and it will honor Range requests
    // for scrubbing automatically. Same for reel.thumbnailUrl with
    // Glide/Coil for the poster image.
}
