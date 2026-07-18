package com.wall.mob

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentsBottomSheet : BottomSheetDialogFragment() {

    private var videoId: String? = null
    private var commentsRecyclerView: RecyclerView? = null
    private var adapter: CommentsAdapter? = null
    private var progressBar: ProgressBar? = null
    private var noCommentsText: TextView? = null
    private var authorInput: EditText? = null
    private var textInput: EditText? = null
    private var submitButton: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoId = arguments?.getString("videoId")
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
        noCommentsText = view.findViewById(R.id.noCommentsText)
        authorInput = view.findViewById(R.id.commentAuthorInput)
        textInput = view.findViewById(R.id.commentTextInput)
        repo()?.loggedUserId()?.let { userId ->
            authorInput?.setText(userId)
            authorInput?.isEnabled = false
        }
        submitButton = view.findViewById(R.id.commentSubmitButton)

        view.findViewById<ImageButton>(R.id.closeCommentsButton).setOnClickListener {
            dismiss()
        }

        commentsRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        adapter = CommentsAdapter()
        commentsRecyclerView?.adapter = adapter

        submitButton?.setOnClickListener {
            postComment()
        }

        loadComments()
    }

    private fun repo(): ReelsRepository? {
        val act = activity as? ReelActivity ?: return null
        return act.getRepo()
    }

    private fun loadComments() {
        val id = videoId ?: return
        val repo = repo() ?: return
        progressBar?.visibility = View.VISIBLE
        noCommentsText?.visibility = View.GONE
        lifecycleScope.launch {
            repo.getComments(id)
                .onSuccess { commentsResp ->
                    progressBar?.visibility = View.GONE
                    val items = commentsResp.items
                    if (items.isEmpty()) {
                        noCommentsText?.visibility = View.VISIBLE
                    } else {
                        adapter?.submitList(items)
                    }
                }
                .onFailure {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(context, "Failed to load comments", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun postComment() {
        val id = videoId ?: return
        val repo = repo() ?: return
        if (!repo.isAdminUser()) {
            Toast.makeText(context, "Admin access is required to comment", Toast.LENGTH_SHORT).show()
            return
        }
        val author = authorInput?.text?.toString()?.trim() ?: return
        val text = textInput?.text?.toString()?.trim() ?: return

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
                    noCommentsText?.visibility = View.GONE
                    adapter?.addComment(comment)
                    commentsRecyclerView?.smoothScrollToPosition(0)
                }
                .onFailure { err ->
                    submitButton?.isEnabled = true
                    Toast.makeText(context, "Failed to post comment: ${err.message}", Toast.LENGTH_SHORT).show()
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
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
            return CommentViewHolder(view)
        }

        override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
            val comment = list[position]
            holder.author.text = comment.author
            holder.text.text = comment.text

            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            holder.time.text = sdf.format(Date(comment.createdAt))
        }

        override fun getItemCount() = list.size

        inner class CommentViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val author: TextView = v.findViewById(R.id.commentAuthor)
            val time: TextView = v.findViewById(R.id.commentTime)
            val text: TextView = v.findViewById(R.id.commentText)
        }
    }
}
