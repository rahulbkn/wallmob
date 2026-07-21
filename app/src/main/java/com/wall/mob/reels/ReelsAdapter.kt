package com.wall.mob.reels

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.wall.mob.R
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReelsAdapter(
    private val items: MutableList<ReelVideo>,
    private val repo: ReelsRepository,
    private val scope: CoroutineScope,
    private val onCommentClick: (ReelVideo) -> Unit
) : RecyclerView.Adapter<ReelsAdapter.ReelViewHolder>() {

    inner class ReelViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val playerView: PlayerView = view.findViewById(R.id.playerView)
        val playPauseIcon: android.widget.ImageView = view.findViewById(R.id.playPauseIcon)
        val likeButton: android.widget.ImageButton = view.findViewById(R.id.likeButton)
        val likeCount: android.widget.TextView = view.findViewById(R.id.likeCount)
        val commentButton: android.widget.ImageButton = view.findViewById(R.id.commentButton)
        val commentCount: android.widget.TextView = view.findViewById(R.id.commentCount)
        val shareButton: android.widget.ImageButton = view.findViewById(R.id.shareButton)
        val shareCount: android.widget.TextView = view.findViewById(R.id.shareCount)
        val qualityButton: android.widget.ImageButton = view.findViewById(R.id.qualityButton)
        val qualityText: android.widget.TextView = view.findViewById(R.id.qualityText)
        val uploaderHandle: android.widget.TextView = view.findViewById(R.id.uploaderHandle)
        val reelTitle: android.widget.TextView = view.findViewById(R.id.reelTitle)
        val reelDescription: android.widget.TextView = view.findViewById(R.id.reelDescription)
        val deleteButton: android.widget.ImageButton = view.findViewById(R.id.deleteButton)
        val deleteText: android.widget.TextView = view.findViewById(R.id.deleteText)
        var player: ExoPlayer? = null
        /** User manually paused this item (tap). Cleared when leaving the item. */
        var userPaused: Boolean = false
        var boundVideoId: String? = null
        var currentVideoUrl: String? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recyclerView: RecyclerView? = null

    /** Index of the snap-centered reel that is allowed to play. */
    private var activePosition: Int = RecyclerView.NO_POSITION

    /** False while fragment is paused, hidden, or off-screen — no audio/video. */
    private var playbackAllowed: Boolean = false

    /** True while the user is dragging the list — keep everything paused. */
    private var isScrolling: Boolean = false

    /** Per-video quality choice: videoId -> "Auto" | "720p" | "480" etc. */
    private val qualityByVideoId = mutableMapOf<String, String>()

    /** Preloaded players for next videos to enable instant playback */
    private val preloadedPlayers = mutableMapOf<String, ExoPlayer>()
    private val PRELOAD_COUNT = 2 // Preload next 2 videos

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reel, parent, false)
        return ReelViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ReelViewHolder, position: Int) {
        val reel = items[position]
        releasePlayer(holder)

        holder.boundVideoId = reel.id
        holder.userPaused = false
        holder.playPauseIcon.visibility = android.view.View.GONE

        holder.uploaderHandle.text = "@${reel.uploader}"
        holder.reelTitle.text = reel.title.ifBlank { "Untitled reel" }
        val description = reel.description.orEmpty().trim()
        holder.reelDescription.text = description
        holder.reelDescription.visibility =
            if (description.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        holder.likeCount.text = reel.likes.toString()
        holder.commentCount.text = reel.comments.toString()
        holder.shareCount.text = reel.shares.toString()

        val canDelete = repo.ownerToken(reel.id) != null
        holder.deleteButton.visibility =
            if (canDelete) android.view.View.VISIBLE else android.view.View.GONE
        holder.deleteText.visibility =
            if (canDelete) android.view.View.VISIBLE else android.view.View.GONE

        holder.likeButton.setImageResource(
            if (repo.isLiked(reel.id)) R.drawable.ic_reel_like_filled
            else R.drawable.ic_reel_like_outline
        )

        val selectedQuality = qualityByVideoId[reel.id] ?: "Auto"
        updateQualityLabel(holder, selectedQuality, reel)

        val available = availableQualityLabels(reel)
        // Hide quality control when this video has no alternate renditions
        val hasChoices = available.size > 1
        holder.qualityButton.visibility =
            if (hasChoices) android.view.View.VISIBLE else android.view.View.GONE
        holder.qualityText.visibility =
            if (hasChoices) android.view.View.VISIBLE else android.view.View.GONE

        val videoUrl = getVideoUrlForQuality(reel, selectedQuality)
        holder.currentVideoUrl = videoUrl

        // Use preloaded player if available, otherwise create new one
        val player = preloadedPlayers.remove(reel.id)?.apply {
            volume = 1f // Restore volume (was muted during preload)
        } ?: ExoPlayerFactory.create(holder.itemView.context).also {
            it.setMediaItem(MediaItem.fromUri(videoUrl))
            it.repeatMode = Player.REPEAT_MODE_ONE
            it.volume = 1f
            // Never auto-start from bind — only syncPlayback() starts the active item.
            it.playWhenReady = false
            it.prepare()
        }
        holder.playerView.player = player
        holder.player = player

        holder.playerView.setOnClickListener { togglePlayPause(holder) }
        holder.qualityButton.setOnClickListener { showQualityDialog(holder, reel) }

        scope.launch { repo.recordView(reel.id) }

        holder.likeButton.setOnClickListener {
            val wasLiked = repo.isLiked(reel.id)
            holder.likeButton.setImageResource(
                if (wasLiked) R.drawable.ic_reel_like_outline else R.drawable.ic_reel_like_filled
            )
            scope.launch(Dispatchers.Main) {
                repo.like(reel.id).onSuccess { result ->
                    val liked = result.liked ?: !wasLiked
                    if (liked) {
                        repo.markLiked(reel.id)
                        if (!wasLiked) reel.likes += 1
                    } else {
                        repo.unmarkLiked(reel.id)
                        if (wasLiked) reel.likes = maxOf(0, reel.likes - 1)
                    }
                    holder.likeButton.setImageResource(
                        if (liked) R.drawable.ic_reel_like_filled else R.drawable.ic_reel_like_outline
                    )
                    holder.likeCount.text = reel.likes.toString()
                }.onFailure {
                    holder.likeButton.setImageResource(
                        if (wasLiked) R.drawable.ic_reel_like_filled else R.drawable.ic_reel_like_outline
                    )
                    Toast.makeText(
                        holder.itemView.context,
                        it.message ?: "Admin access is required to like reels",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        holder.shareButton.setOnClickListener {
            if (!repo.isAdminUser()) {
                Toast.makeText(
                    holder.itemView.context,
                    "Admin access is required to share reels",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            if (repo.isShared(reel.id)) {
                Toast.makeText(holder.itemView.context, "Already shared", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scope.launch(Dispatchers.Main) {
                repo.share(reel.id).onSuccess { result ->
                    repo.markShared(reel.id)
                    if (result.counted) {
                        reel.shares += 1
                        holder.shareCount.text = reel.shares.toString()
                    }
                }
            }
        }

        holder.commentButton.setOnClickListener { onCommentClick(reel) }

        holder.deleteButton.setOnClickListener {
            val token = repo.ownerToken(reel.id)
            if (token == null) {
                Toast.makeText(
                    holder.itemView.context,
                    "Delete only works for reels you uploaded on this device",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            scope.launch {
                repo.delete(reel.id).onSuccess {
                    Toast.makeText(holder.itemView.context, "Reel deleted", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(
                        holder.itemView.context,
                        "Delete failed: ${it.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // After bind, enforce single-player rule for currently visible holders.
        syncPlayback()
    }

    /**
     * Labels actually present for this reel.
     * Always includes Auto when a default URL exists; other entries come only from qualities map.
     */
    private fun availableQualityLabels(reel: ReelVideo): List<String> {
        val labels = linkedSetOf<String>()
        labels.add("Auto")
        val map = reel.qualities
        if (!map.isNullOrEmpty()) {
            // Prefer higher quality first for nicer dialog order
            val sorted = map.keys.sortedByDescending { key ->
                key.filter { it.isDigit() }.toIntOrNull() ?: 0
            }
            for (key in sorted) {
                labels.add(formatQualityLabel(key))
            }
        }
        return labels.toList()
    }

    private fun formatQualityLabel(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.equals("Auto", ignoreCase = true)) return "Auto"
        return if (trimmed.endsWith("p", ignoreCase = true)) trimmed else "${trimmed}p"
    }

    private fun normalizeQualityKey(label: String): String {
        return label.trim().removeSuffix("p").removeSuffix("P")
    }

    private fun getVideoUrlForQuality(reel: ReelVideo, quality: String): String {
        // Use HLS adaptive streaming when available and quality is Auto
        if (quality.equals("Auto", ignoreCase = true) || quality.isBlank()) {
            val masterPlaylistUrl = reel.masterPlaylistUrl
            if (!masterPlaylistUrl.isNullOrBlank()) {
                // HLS master playlist enables automatic quality switching
                return masterPlaylistUrl
            }
            return reel.videoUrl
        }
        val map = reel.qualities ?: return reel.videoUrl
        val key = normalizeQualityKey(quality)
        // Match either "720" or "720p" style keys from the API
        return map[key]
            ?: map["${key}p"]
            ?: map[quality]
            ?: map.entries.firstOrNull {
                normalizeQualityKey(it.key).equals(key, ignoreCase = true)
            }?.value
            ?: reel.videoUrl
    }

    private fun updateQualityLabel(holder: ReelViewHolder, quality: String, reel: ReelVideo) {
        val available = availableQualityLabels(reel)
        val resolved = if (available.any { it.equals(quality, ignoreCase = true) }) {
            available.first { it.equals(quality, ignoreCase = true) }
        } else {
            "Auto"
        }
        holder.qualityText.text = resolved
    }

    private fun showQualityDialog(holder: ReelViewHolder, reel: ReelVideo) {
        val available = availableQualityLabels(reel)
        if (available.size <= 1) {
            Toast.makeText(
                holder.itemView.context,
                "Only default quality available",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val current = qualityByVideoId[reel.id] ?: "Auto"
        val checked = available.indexOfFirst { it.equals(current, ignoreCase = true) }
            .let { if (it >= 0) it else 0 }

        android.app.AlertDialog.Builder(holder.itemView.context)
            .setTitle("Video quality")
            .setSingleChoiceItems(available.toTypedArray(), checked) { dialog, which ->
                val quality = available[which]
                applyQualityForVideo(holder, reel, quality)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyQualityForVideo(holder: ReelViewHolder, reel: ReelVideo, quality: String) {
        qualityByVideoId[reel.id] = quality
        updateQualityLabel(holder, quality, reel)

        val newUrl = getVideoUrlForQuality(reel, quality)
        if (holder.currentVideoUrl == newUrl) return

        holder.currentVideoUrl = newUrl
        val player = holder.player ?: return
        val positionMs = player.currentPosition
        val shouldResume =
            holder.bindingAdapterPosition == activePosition &&
                playbackAllowed &&
                !isScrolling &&
                !holder.userPaused

        player.playWhenReady = false
        player.stop()
        player.setMediaItem(MediaItem.fromUri(newUrl))
        player.prepare()
        player.seekTo(positionMs)

        if (shouldResume) {
            player.playWhenReady = true
            player.play()
        } else {
            player.pause()
            player.playWhenReady = false
        }
        // Ensure siblings stay silent after quality swap
        syncPlayback()
    }

    private fun togglePlayPause(holder: ReelViewHolder) {
        val player = holder.player ?: return
        val pos = holder.bindingAdapterPosition
        if (pos != activePosition || !playbackAllowed || isScrolling) return

        if (player.isPlaying) {
            holder.userPaused = true
            player.playWhenReady = false
            player.pause()
            flashIcon(holder, R.drawable.ic_reel_play)
        } else {
            holder.userPaused = false
            // Ensure volume is at full before playing
            player.volume = 1f
            // Only this item may play
            pauseAllExcept(pos)
            player.playWhenReady = true
            player.play()
            flashIcon(holder, R.drawable.ic_reel_pause)
        }
    }

    private fun flashIcon(holder: ReelViewHolder, resId: Int) {
        holder.playPauseIcon.setImageResource(resId)
        holder.playPauseIcon.visibility = android.view.View.VISIBLE
        holder.playPauseIcon.alpha = 1f
        mainHandler.postDelayed({
            holder.playPauseIcon.visibility = android.view.View.GONE
        }, 300)
    }

    private fun releasePlayer(holder: ReelViewHolder) {
        holder.playerView.player = null
        holder.player?.let { player ->
            player.playWhenReady = false
            player.stop()
            player.release()
        }
        holder.player = null
        holder.currentVideoUrl = null
        holder.boundVideoId = null
        holder.userPaused = false
    }

    override fun onViewRecycled(holder: ReelViewHolder) {
        releasePlayer(holder)
        super.onViewRecycled(holder)
    }

    override fun onViewAttachedToWindow(holder: ReelViewHolder) {
        super.onViewAttachedToWindow(holder)
        syncPlayback()
    }

    override fun onViewDetachedFromWindow(holder: ReelViewHolder) {
        // Leaving the screen: always stop this player so nothing plays off-screen.
        holder.player?.let { player ->
            player.playWhenReady = false
            player.pause()
            player.volume = 0f // Mute to prevent any audio leakage
        }
        super.onViewDetachedFromWindow(holder)
    }

    /**
     * Mark which reel is the snap-centered one. Only that item may play.
     */
    fun setActivePosition(position: Int) {
        if (position < 0 || position >= items.size) return
        if (activePosition != position) {
            // Reset manual pause when user swipes to a different reel
            findHolder(activePosition)?.userPaused = false
            activePosition = position
        } else {
            activePosition = position
        }
        // Ensure the new active player has full volume
        findHolder(position)?.player?.volume = 1f
        syncPlayback()
        preloadNextVideos(position)
    }

    /**
     * Preload next videos for instant playback when user swipes.
     */
    private fun preloadNextVideos(currentPosition: Int) {
        // Clean up old preloaded players that are too far away
        val toRemove = preloadedPlayers.keys.filter { videoId ->
            val idx = items.indexOfFirst { it.id == videoId }
            idx < 0 || idx < currentPosition - 1 || idx > currentPosition + PRELOAD_COUNT + 1
        }
        toRemove.forEach { videoId ->
            preloadedPlayers.remove(videoId)?.release()
        }

        // Preload next videos
        for (i in 1..PRELOAD_COUNT) {
            val nextPos = currentPosition + i
            if (nextPos >= items.size) break
            
            val nextReel = items[nextPos]
            if (preloadedPlayers.containsKey(nextReel.id)) continue

            val quality = qualityByVideoId[nextReel.id] ?: "Auto"
            val videoUrl = getVideoUrlForQuality(nextReel, quality)
            
            try {
                val player = ExoPlayerFactory.create(recyclerView?.context ?: return).apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 0f // Muted during preload
                    playWhenReady = false
                    prepare()
                }
                preloadedPlayers[nextReel.id] = player
            } catch (e: Exception) {
                // Ignore preload failures
            }
        }
    }

    fun setScrolling(scrolling: Boolean) {
        isScrolling = scrolling
        if (scrolling) {
            // While dragging, mute all players immediately (no double audio mid-swipe)
            pauseAllPlayers()
        } else {
            syncPlayback()
        }
    }

    /**
     * Called from fragment lifecycle. When false, nothing plays (background / other tab).
     */
    fun setPlaybackAllowed(allowed: Boolean) {
        playbackAllowed = allowed
        if (!allowed) {
            // Completely stop all players and preloaded players when going to background
            pauseAllPlayers()
            // Also mute and pause all preloaded players
            preloadedPlayers.values.forEach { player ->
                player.playWhenReady = false
                player.pause()
                player.volume = 0f
            }
        } else {
            syncPlayback()
        }
    }

    /**
     * Single source of truth: at most one ExoPlayer has playWhenReady=true.
     */
    private fun syncPlayback() {
        val rv = recyclerView ?: return
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i) ?: continue
            val holder = rv.getChildViewHolder(child) as? ReelViewHolder ?: continue
            val player = holder.player ?: continue
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) {
                player.playWhenReady = false
                player.pause()
                continue
            }

            val shouldPlay =
                playbackAllowed &&
                    !isScrolling &&
                    pos == activePosition &&
                    !holder.userPaused

            if (shouldPlay) {
                // Ensure volume is always at full for active player
                player.volume = 1f
                if (!player.isPlaying) {
                    player.playWhenReady = true
                    player.play()
                }
            } else {
                if (player.playWhenReady || player.isPlaying) {
                    player.playWhenReady = false
                    player.pause()
                }
            }
        }
    }

    private fun pauseAllPlayers() {
        val rv = recyclerView
        if (rv != null) {
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i) ?: continue
                val holder = rv.getChildViewHolder(child) as? ReelViewHolder ?: continue
                holder.player?.let { player ->
                    player.playWhenReady = false
                    player.pause()
                }
            }
        }
    }

    private fun pauseAllExcept(exceptPosition: Int) {
        val rv = recyclerView ?: return
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i) ?: continue
            val holder = rv.getChildViewHolder(child) as? ReelViewHolder ?: continue
            val pos = holder.bindingAdapterPosition
            if (pos != exceptPosition) {
                holder.player?.let { player ->
                    player.playWhenReady = false
                    player.pause()
                }
            }
        }
    }

    private fun findHolder(position: Int): ReelViewHolder? {
        if (position < 0) return null
        return recyclerView?.findViewHolderForAdapterPosition(position) as? ReelViewHolder
    }

    fun pauseAll() {
        pauseAllPlayers()
        // Also stop all preloaded players
        preloadedPlayers.values.forEach { player ->
            player.playWhenReady = false
            player.pause()
            player.volume = 0f
        }
    }

    fun releaseAll() {
        val rv = recyclerView
        if (rv != null) {
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i) ?: continue
                val holder = rv.getChildViewHolder(child) as? ReelViewHolder ?: continue
                releasePlayer(holder)
            }
        }
        // Release all preloaded players
        preloadedPlayers.values.forEach { it.release() }
        preloadedPlayers.clear()
        activePosition = RecyclerView.NO_POSITION
    }

    fun submitList(newItems: List<ReelVideo>) {
        releaseAll()
        items.clear()
        items.addAll(newItems)
        // Keep quality choices for videos that remain; drop orphaned ids lazily on next open
        notifyDataSetChanged()
    }

    fun addItems(newItems: List<ReelVideo>) {
        val startIndex = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(startIndex, newItems.size)
    }
}
