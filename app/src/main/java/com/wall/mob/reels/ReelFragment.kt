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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch

class ReelFragment : Fragment() {

    private var _repo: ReelsRepository? = null

    private lateinit var adapter: ReelsAdapter
    private lateinit var pager: ViewPager2
    private lateinit var statusText: TextView
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var loadingSpinner: ProgressBar

    private var startPosition: Int = 0

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
        pager = view.findViewById(R.id.reelsPager)
        pager.orientation = ViewPager2.ORIENTATION_VERTICAL
        statusText = view.findViewById(R.id.statusText)
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer)
        loadingSpinner = view.findViewById(R.id.loadingSpinner)

        applyTopInsets()

        adapter = ReelsAdapter(
            items = mutableListOf(),
            repo = _repo!!,
            scope = lifecycleScope
        ) { reel -> openComments(reel) }

        pager.adapter = adapter

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                adapter.setActivePosition(position)
            }
        })

        loadFeed()
        checkHealth()
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
        if (::adapter.isInitialized) adapter.pauseAll()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized && !isHidden) adapter.resumeAll()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (::adapter.isInitialized) {
            if (hidden) {
                adapter.pauseAll()
            } else {
                adapter.resumeAll()
            }
        }
    }

    override fun onDestroyView() {
        if (::adapter.isInitialized) adapter.releaseAll()
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
            pager.post { pager.setCurrentItem(position, false) }
        }
    }

    fun loadFeed() {
        loadingSpinner.visibility = View.VISIBLE
        emptyStateContainer.visibility = View.GONE

        lifecycleScope.launch {
            _repo!!.getFeed(page = 1, perPage = 20)
                .onSuccess { feed ->
                    loadingSpinner.visibility = View.GONE
                    if (feed.items.isEmpty()) {
                        emptyStateContainer.visibility = View.VISIBLE
                        pager.visibility = View.GONE
                    } else {
                        emptyStateContainer.visibility = View.GONE
                        pager.visibility = View.VISIBLE
                        adapter.submitList(feed.items)
                        if (startPosition > 0 && startPosition < feed.items.size) {
                            pager.post { pager.setCurrentItem(startPosition, false) }
                        }
                    }
                }
                .onFailure { err ->
                    loadingSpinner.visibility = View.GONE
                    emptyStateContainer.visibility = View.VISIBLE
                    Toast.makeText(
                        requireContext(),
                        "Couldn't load feed: ${err.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun openComments(reel: ReelVideo) {
        CommentsBottomSheet.show(parentFragmentManager, reel.id)
    }

    companion object {
        private const val ARG_START_POSITION = "start_position"

        @JvmStatic fun newInstance(startPosition: Int = 0): ReelFragment {
            val args = Bundle()
            args.putInt(ARG_START_POSITION, startPosition)
            val fragment = ReelFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
