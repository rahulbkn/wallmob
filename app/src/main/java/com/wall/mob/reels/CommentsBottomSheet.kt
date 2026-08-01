package com.wall.mob.reels

import com.wall.mob.R
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class CommentsBottomSheet : BottomSheetDialogFragment() {

    private var videoId: String? = null
    private var _repo: ReelsRepository? = null
    private var commentsRecyclerView: RecyclerView? = null
    private var adapter: CommentsAdapter? = null
    private var progressBar: ProgressBar? = null
    private var noCommentsContainer: View? = null
    private var noCommentsText: TextView? = null
    private var commentsSubtitle: TextView? = null
    private var authorInput: EditText? = null
    private var textInput: EditText? = null
    private var submitButton: ImageButton? = null

    override fun getTheme(): Int = R.style.ReelBottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoId = arguments?.getString(ARG_VIDEO_ID)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.setBackgroundResource(android.R.color.transparent)
            sheet?.let { bottomSheet ->
                val behavior = BottomSheetBehavior.from(bottomSheet)
                val height = (resources.displayMetrics.heightPixels * 0.78f).toInt()
                bottomSheet.layoutParams?.height = height
                bottomSheet.requestLayout()
                behavior.peekHeight = height
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        commentsRecyclerView = view.findViewById(R.id.commentsRecyclerView)
        progressBar = view.findViewById(R.id.commentsProgressBar)
        noCommentsContainer = view.findViewById(R.id.noCommentsContainer)
        noCommentsText = view.findViewById(R.id.noCommentsText)
        commentsSubtitle = view.findViewById(R.id.commentsSubtitle)
        authorInput = view.findViewById(R.id.commentAuthorInput)
        textInput = view.findViewById(R.id.commentTextInput)
        submitButton = view.findViewById(R.id.commentSubmitButton)

        repo()?.loggedUserId()?.let { userId ->
            authorInput?.setText(userId)
            authorInput?.isEnabled = false
        }

        view.findViewById<ImageButton>(R.id.closeCommentsButton).setOnClickListener {
            dismiss()
        }

        commentsRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        adapter = CommentsAdapter()
        commentsRecyclerView?.adapter = adapter

        submitButton?.setOnClickListener { postComment() }
        loadComments()
    }

    private fun repo(): ReelsRepository? {
        if (_repo == null) {
            _repo = context?.applicationContext?.let { ReelsRepository(it) }
        }
        return _repo
    }

    private fun loadComments() {
        val id = videoId ?: return
        val repo = repo() ?: return
        progressBar?.visibility = View.VISIBLE
        noCommentsContainer?.visibility = View.GONE
        lifecycleScope.launch {
            repo.getComments(id)
                .onSuccess { commentsResp ->
                    progressBar?.visibility = View.GONE
                    val items = commentsResp.items
                    updateCountLabel(items.size)
                    if (items.isEmpty()) {
                        noCommentsContainer?.visibility = View.VISIBLE
                        adapter?.submitList(emptyList())
                    } else {
                        noCommentsContainer?.visibility = View.GONE
                        adapter?.submitList(items)
                    }
                }
                .onFailure {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(context, "Failed to load comments", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateCountLabel(count: Int) {
        commentsSubtitle?.text = when (count) {
            0 -> "Join the conversation"
            1 -> "1 comment"
            else -> "$count comments"
        }
    }

    private fun postComment() {
        val id = videoId ?: return
        val repo = repo() ?: return
        if (!repo.isAdminUser()) {
            Toast.makeText(context, "Admin access is required to comment", Toast.LENGTH_SHORT).show()
            return
        }
        val author = authorInput?.text?.toString()?.trim().orEmpty()
        val text = textInput?.text?.toString()?.trim().orEmpty()

        if (author.isEmpty() || text.isEmpty()) {
            Toast.makeText(context, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        submitButton?.isEnabled = false
        lifecycleScope.launch {
            repo.addComment(id, author, text)
                .onSuccess { comment ->
                    submitButton?.isEnabled = true
                    textInput?.setText("")
                    noCommentsContainer?.visibility = View.GONE
                    adapter?.addComment(comment)
                    updateCountLabel(adapter?.itemCount ?: 0)
                    commentsRecyclerView?.smoothScrollToPosition(0)
                }
                .onFailure { err ->
                    submitButton?.isEnabled = true
                    Toast.makeText(
                        context,
                        "Failed to post comment: ${err.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    companion object {
        private const val ARG_VIDEO_ID = "videoId"

        fun show(fm: androidx.fragment.app.FragmentManager, videoId: String) {
            val args = Bundle()
            args.putString(ARG_VIDEO_ID, videoId)
            val fragment = CommentsBottomSheet()
            fragment.arguments = args
            fragment.show(fm, "comments")
        }
    }

    private inner class CommentsAdapter : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {
        private val list = mutableListOf<Comment>()

        fun submitList(newList: List<Comment>) {
            list.clear()
            list.addAll(newList.sortedByDescending { it.createdAt })
            notifyDataSetChanged()
        }

        fun addComment(comment: Comment) {
            list.add(0, comment)
            notifyItemInserted(0)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_comment, parent, false)
            return CommentViewHolder(view)
        }

        override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
            val comment = list[position]
            holder.author.text = comment.author
            holder.text.text = comment.text
            holder.time.text = relativeTime(comment.createdAt)
            val initial = comment.author.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.avatar.text = initial
        }

        override fun getItemCount() = list.size

        private fun relativeTime(createdAt: Long): String {
            val diff = System.currentTimeMillis() - createdAt
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            return when {
                minutes < 1 -> "now"
                minutes < 60 -> "${minutes}m"
                hours < 24 -> "${hours}h"
                days < 7 -> "${days}d"
                else -> "${days / 7}w"
            }
        }

        inner class CommentViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val author: TextView = v.findViewById(R.id.commentAuthor)
            val time: TextView = v.findViewById(R.id.commentTime)
            val text: TextView = v.findViewById(R.id.commentText)
            val avatar: TextView = v.findViewById(R.id.commentAvatar)
        }
    }
}
