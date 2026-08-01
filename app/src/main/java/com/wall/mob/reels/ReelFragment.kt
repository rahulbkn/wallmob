package com.wall.mob.reels

import com.wall.mob.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ReelFragment : Fragment() {

    private var _repo: ReelsRepository? = null

    private lateinit var adapter: ReelsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var loadingMoreProgress: ProgressBar
    private lateinit var toolbar: Toolbar
    private lateinit var snapHelper: PagerSnapHelper
    private lateinit var layoutManager: LinearLayoutManager

    private var startPosition: Int = 0
    private var currentPage = 1
    private var isLoading = false
    private var hasMoreData = true
    private var loadMoreJob: Job? = null
    private var isInitialLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startPosition = arguments?.getInt(ARG_START_POSITION, 0) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reel, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _repo = ReelsRepository(requireContext().applicationContext)

        recyclerView = view.findViewById(R.id.reelsRecyclerView)
        statusText = view.findViewById(R.id.statusText)
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer)
        view.findViewById<View>(R.id.emptyRefreshButton).setOnClickListener { loadFeed() }
        loadingSpinner = view.findViewById(R.id.loadingSpinner)
        loadingMoreProgress = view.findViewById(R.id.loadingMoreProgress)
        toolbar = view.findViewById(R.id.toolbar)

        setupToolbar()
        applyTopInsets()

        layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        recyclerView.layoutManager = layoutManager

        snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)

        adapter = ReelsAdapter(
            items = mutableListOf(),
            repo = _repo!!,
            scope = lifecycleScope
        ) { reel -> openComments(reel) }

        recyclerView.adapter = adapter

        // Pagination near end of list
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && hasMoreData && !isInitialLoad) {
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 3) {
                        loadMoreItems()
                    }
                }
            }
        })

        // Single active player: pause while dragging, play only snapped item when idle
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING,
                    RecyclerView.SCROLL_STATE_SETTLING -> {
                        adapter.setScrolling(true)
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        adapter.setScrolling(false)
                        activateSnappedItem()
                    }
                }
            }
        })

        loadFeed()
        checkHealth()
    }

    private fun setupToolbar() {
        toolbar.title = "Reels"
        toolbar.setTitleTextColor(resources.getColor(android.R.color.white, null))
    }

    private fun activateSnappedItem() {
        if (!::adapter.isInitialized || !::snapHelper.isInitialized) return
        val snapView = snapHelper.findSnapView(layoutManager)
        val position = snapView?.let { layoutManager.getPosition(it) }
            ?: layoutManager.findFirstCompletelyVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }
            ?: layoutManager.findFirstVisibleItemPosition()
        if (position != RecyclerView.NO_POSITION) {
            adapter.setActivePosition(position)
        }
    }

    private fun resumePlaybackWhenReady() {
        if (!::adapter.isInitialized || !::recyclerView.isInitialized) return
        recyclerView.post {
            if (!isAdded || isHidden || !isResumed) return@post
            adapter.setPlaybackAllowed(true)
            activateSnappedItem()
        }
    }

    private fun applyTopInsets() {
        val topBar = requireView().findViewById<View>(R.id.topBar)
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top + 8, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    override fun onPause() {
        super.onPause()
        // App background / another activity — stop all reels
        if (::adapter.isInitialized) {
            adapter.setPlaybackAllowed(false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized && !isHidden) {
            resumePlaybackWhenReady()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!::adapter.isInitialized) return
        if (hidden) {
            // Switched away from Reels tab — no background playback
            adapter.setPlaybackAllowed(false)
        } else {
            resumePlaybackWhenReady()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::adapter.isInitialized) {
            adapter.setPlaybackAllowed(false)
        }
    }

    override fun onDestroyView() {
        if (::adapter.isInitialized) {
            adapter.setPlaybackAllowed(false)
            adapter.releaseAll()
        }
        loadMoreJob?.cancel()
        _repo = null
        super.onDestroyView()
    }

    private fun checkHealth() {
        lifecycleScope.launch {
            _repo!!.health().onSuccess {
                statusText.text = "\u25CF Online"
                statusText.setTextColor(0xFFE8FFF0.toInt())
            }.onFailure {
                statusText.text = "\u25CF Offline"
                statusText.setTextColor(0xFFFFCDD2.toInt())
            }
        }
    }

    fun scrollToPosition(position: Int) {
        if (::adapter.isInitialized && position >= 0 && position < adapter.itemCount) {
            startPosition = position
            recyclerView.post {
                recyclerView.scrollToPosition(position)
                adapter.setActivePosition(position)
            }
        }
    }

    fun loadFeed() {
        currentPage = 1
        hasMoreData = true
        isLoading = false
        isInitialLoad = true

        loadingSpinner.visibility = View.VISIBLE
        emptyStateContainer.visibility = View.GONE
        recyclerView.visibility = View.GONE
        loadingMoreProgress.visibility = View.GONE

        lifecycleScope.launch {
            _repo!!.getFeed(page = currentPage, perPage = 10)
                .onSuccess { feed ->
                    loadingSpinner.visibility = View.GONE
                    isInitialLoad = false

                    if (feed.items.isEmpty()) {
                        emptyStateContainer.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                        hasMoreData = false
                    } else {
                        emptyStateContainer.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        adapter.submitList(feed.items)

                        val target = if (startPosition > 0 && startPosition < feed.items.size) {
                            startPosition
                        } else {
                            0
                        }
                        recyclerView.post {
                            if (target > 0) recyclerView.scrollToPosition(target)
                            // Only play once fragment is visible/resumed
                            if (!isHidden && isResumed) {
                                adapter.setPlaybackAllowed(true)
                                adapter.setActivePosition(target)
                            }
                        }
                    }
                }
                .onFailure { err ->
                    loadingSpinner.visibility = View.GONE
                    emptyStateContainer.visibility = View.VISIBLE
                    isInitialLoad = false
                    Toast.makeText(
                        requireContext(),
                        "Couldn't load feed: ${err.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun loadMoreItems() {
        if (isLoading || !hasMoreData) return

        isLoading = true
        loadingMoreProgress.visibility = View.VISIBLE

        loadMoreJob?.cancel()
        loadMoreJob = lifecycleScope.launch {
            val nextPage = currentPage + 1
            _repo!!.getFeed(page = nextPage, perPage = 10)
                .onSuccess { feed ->
                    isLoading = false
                    loadingMoreProgress.visibility = View.GONE

                    if (feed.items.isEmpty()) {
                        hasMoreData = false
                    } else {
                        currentPage = nextPage
                        adapter.addItems(feed.items)
                        hasMoreData = feed.items.size >= 10
                    }
                }
                .onFailure { err ->
                    isLoading = false
                    loadingMoreProgress.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Failed to load more: ${err.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun openComments(reel: ReelVideo) {
        CommentsBottomSheet.show(parentFragmentManager, reel.id)
    }

    companion object {
        private const val ARG_START_POSITION = "start_position"

        @JvmStatic
        fun newInstance(startPosition: Int = 0): ReelFragment {
            val args = Bundle()
            args.putInt(ARG_START_POSITION, startPosition)
            val fragment = ReelFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
