package com.wall.mob.reels

import android.content.Context
import com.google.android.exoplayer2.ExoPlayer

class ExoPlayerPool(private val maxSize: Int = 4) {
    private val available = ArrayDeque<ExoPlayer>(maxSize)

    fun acquire(context: Context): ExoPlayer {
        return available.removeLastOrNull()?.also {
            it.stop()
            it.playWhenReady = false
            it.volume = 1f
        } ?: ExoPlayerFactory.create(context)
    }

    fun recycle(player: ExoPlayer) {
        player.stop()
        player.playWhenReady = false
        if (available.size < maxSize) {
            available.addLast(player)
        } else {
            player.release()
        }
    }

    fun releaseAll() {
        available.forEach { it.release() }
        available.clear()
    }
}
