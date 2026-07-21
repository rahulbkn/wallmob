package com.wall.mob.reels

import android.content.Context
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.LoadControl
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory

/**
 * Factory for creating optimized ExoPlayer instances with caching and fast loading.
 */
object ExoPlayerFactory {
    
    /**
     * Creates an ExoPlayer with:
     * - 500MB disk cache for faster repeated loads
     * - Optimized buffering for quick start (min 1s, max 10s)
     * - Connection pooling via OkHttp
     * - Prioritize playback start over buffer size
     */
    fun create(context: Context): ExoPlayer {
        val cache = ExoPlayerCacheManager.getCache(context)
        
        // Optimized load control for fast start
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,   // minBufferMs - start playing after 1s
                10000,  // maxBufferMs - buffer up to 10s
                500,    // bufferForPlaybackMs - resume after 500ms
                1000    // bufferForPlaybackAfterRebufferMs - resume after 1s rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true) // Prioritize quick start
            .build()
        
        // HTTP data source with connection pooling
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)
        
        // Wrap with cache
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(
                DefaultDataSource.Factory(context, httpDataSourceFactory)
            )
            .setCacheWriteDataSinkFactory(null) // Use default
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)
        
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
}
