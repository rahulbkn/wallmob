package com.wall.mob

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.wall.mob.R
import com.wall.mob.ReelVideo
import com.wall.mob.ReelsRepository
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
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
        val deleteButton: android.widget.ImageButton = view.findViewById(R.id.deleteButton)
        val deleteText: android.widget.TextView = view.findViewById(R.id.deleteText)
        val uploaderHandle: android.widget.TextView = view.findViewById(R.id.uploaderHandle)
        val reelTitle: android.widget.TextView = view.findViewById(R.id.reelTitle)
        val reelDescription: android.widget.TextView = view.findViewById(R.id.reelDescription)
        var player: ExoPlayer? = null
        var isPaused: Boolean = false
    }

    private val activePlayers = mutableListOf<ExoPlayer>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recyclerView: RecyclerView? = null

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

        holder.player?.let { oldPlayer ->
            activePlayers.remove(oldPlayer)
            oldPlayer.release()
        }
        holder.player = null
        holder.isPaused = false
        holder.playPauseIcon.visibility = android.view.View.GONE

        holder.uploaderHandle.text = "@${reel.uploader}"
        holder.reelTitle.text = reel.title.ifBlank { "Untitled reel" }
        val description = reel.description.orEmpty().trim()
        holder.reelDescription.text = description
        holder.reelDescription.visibility = if (description.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        holder.likeCount.text = reel.likes.toString()
        holder.commentCount.text = reel.comments.toString()
        holder.shareCount.text = reel.shares.toString()

        val hasAlternateQualities = !reel.qualities.isNullOrEmpty()
        holder.qualityButton.isEnabled = hasAlternateQualities
        holder.qualityButton.alpha = if (hasAlternateQualities) 1f else 0.45f
        holder.qualityText.text = if (hasAlternateQualities) "Auto" else "HD"

        val canDelete = repo.ownerToken(reel.id) != null
        holder.deleteButton.visibility = if (canDelete) android.view.View.VISIBLE else android.view.View.GONE
        holder.deleteText.visibility = if (canDelete) android.view.View.VISIBLE else android.view.View.GONE

        holder.likeButton.setImageResource(
            if (repo.isLiked(reel.id)) R.drawable.ic_reel_like_filled
            else R.drawable.ic_reel_like_outline
        )

        val player = ExoPlayer.Builder(holder.itemView.context).build()
        player.setMediaItem(MediaItem.fromUri(reel.videoUrl))
        player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        player.prepare()
        player.play()
        holder.playerView.player = player
        holder.player = player
        activePlayers.add(player)

        holder.playerView.setOnClickListener {
            togglePlayPause(holder)
        }

        scope.launch { repo.recordView(reel.id) }

        holder.likeButton.setOnClickListener {
            if (repo.isLiked(reel.id)) {
                Toast.makeText(holder.itemView.context, "Already liked", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            holder.likeButton.setImageResource(R.drawable.ic_reel_like_filled)
            scope.launch(Dispatchers.Main) {
                repo.like(reel.id).onSuccess { result ->
                    repo.markLiked(reel.id)
                    if (result.counted) {
                        reel.likes += 1
                        holder.likeCount.text = reel.likes.toString()
                    }
                }.onFailure {
                    holder.likeButton.setImageResource(R.drawable.ic_reel_like_outline)
                }
            }
        }

        holder.shareButton.setOnClickListener {
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

        holder.qualityButton.setOnClickListener {
            val qualities = reel.qualities
            if (!qualities.isNullOrEmpty()) {
                val labels = qualities.keys.toTypedArray()
                android.app.AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Select Quality")
                    .setItems(labels) { _, which ->
                        val selected = labels[which]
                        holder.qualityText.text = selected
                        val url = qualities[selected] ?: reel.videoUrl
                        holder.player?.stop()
                        holder.player?.setMediaItem(MediaItem.fromUri(url))
                        holder.player?.prepare()
                        holder.player?.play()
                        Toast.makeText(holder.itemView.context, "Quality: $selected", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            } else {
                Toast.makeText(holder.itemView.context, "No alternate qualities available", Toast.LENGTH_SHORT).show()
            }
        }

        holder.deleteButton.setOnClickListener {
            val token = repo.ownerToken(reel.id)
            if (token == null) {
                Toast.makeText(holder.itemView.context, "Delete only works for reels you uploaded on this device", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scope.launch {
                repo.delete(reel.id).onSuccess {
                    Toast.makeText(holder.itemView.context, "Reel deleted", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(holder.itemView.context, "Delete failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun togglePlayPause(holder: ReelViewHolder) {
        val player = holder.player ?: return
        if (player.isPlaying) {
            player.pause()
            holder.isPaused = true
            holder.playPauseIcon.setImageResource(R.drawable.ic_reel_play)
            holder.playPauseIcon.visibility = android.view.View.VISIBLE
            holder.playPauseIcon.alpha = 1f
            mainHandler.postDelayed({
                holder.playPauseIcon.visibility = android.view.View.GONE
            }, 300)
        } else {
            player.play()
            holder.isPaused = false
            holder.playPauseIcon.setImageResource(R.drawable.ic_reel_pause)
            holder.playPauseIcon.visibility = android.view.View.VISIBLE
            holder.playPauseIcon.alpha = 1f
            mainHandler.postDelayed({
                holder.playPauseIcon.visibility = android.view.View.GONE
            }, 300)
        }
    }

    override fun onViewRecycled(holder: ReelViewHolder) {
        holder.player?.let {
            activePlayers.remove(it)
            it.release()
        }
        holder.player = null
        super.onViewRecycled(holder)
    }

    fun setActivePosition(position: Int) {
        val rv = recyclerView ?: return
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val holder = rv.getChildViewHolder(child) as? ReelViewHolder ?: continue
            val player = holder.player ?: continue
            if (holder.bindingAdapterPosition == position) {
                if (!player.isPlaying) player.play()
            } else {
                if (player.isPlaying) player.pause()
            }
        }
    }

    fun pauseAll() {
        for (player in activePlayers.toList()) {
            if (player.isPlaying) player.pause()
        }
    }

    fun resumeAll() {
        for (player in activePlayers.toList()) {
            if (!player.isPlaying) player.play()
        }
    }

    fun releaseAll() {
        for (player in activePlayers.toList()) {
            player.release()
        }
        activePlayers.clear()
    }

    fun submitList(newItems: List<ReelVideo>) {
        releaseAll()
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
