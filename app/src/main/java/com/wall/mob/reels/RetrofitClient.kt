package com.wall.mob.reels

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Point this at your deployed Worker, e.g. from wrangler.jsonc's
    // PUBLIC_BASE_URL: "https://reels-backend.rahulkumarbknv.workers.dev/"
    // Must end with a trailing slash for Retrofit's relative @GET/@POST paths.
    const val BASE_URL = "https://reels-backend.rahulkumarbknv.workers.dev/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // video streams can take a while
        .writeTimeout(120, TimeUnit.SECONDS) // uploads can be big
        .build()

    val api: ReelsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReelsApiService::class.java)
    }
}
