package com.wall.mob.reels

import android.content.Context
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File

/**
 * Singleton cache manager for ExoPlayer video caching.
 * Implements a 500MB LRU cache to speed up reel loading.
 */
object ExoPlayerCacheManager {
    private const val CACHE_SIZE_BYTES = 500L * 1024 * 1024 // 500MB
    private const val CACHE_DIR_NAME = "exoplayer_cache"

    @Volatile
    private var instance: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: createCache(context).also { instance = it }
        }
    }

    private fun createCache(context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES)
        val databaseProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, evictor, databaseProvider)
    }

    fun release() {
        synchronized(this) {
            instance?.release()
            instance = null
        }
    }
}
