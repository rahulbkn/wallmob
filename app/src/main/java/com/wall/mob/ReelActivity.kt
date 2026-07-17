package com.wall.mob

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.wall.mob.ReelVideo
import com.wall.mob.ReelsRepository
import com.wall.mob.ReelsAdapter
import com.wall.mob.R
import kotlinx.coroutines.launch

class ReelActivity : AppCompatActivity() {

    private var _repo: ReelsRepository? = null
    fun getRepo(): ReelsRepository? = _repo

    private lateinit var adapter: ReelsAdapter
    private lateinit var pager: ViewPager2
    private lateinit var uploadButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reel)

        _repo = ReelsRepository(applicationContext)
        pager = findViewById(R.id.reelsPager)
        pager.orientation = ViewPager2.ORIENTATION_VERTICAL
        uploadButton = findViewById(R.id.uploadButton)
        statusText = findViewById(R.id.statusText)

        uploadButton.setOnClickListener {
            UploadBottomSheet.show(supportFragmentManager) { loadFeed() }
        }

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

    override fun onPause() {
        super.onPause()
        if (::adapter.isInitialized) adapter.pauseAll()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) adapter.resumeAll()
    }

    override fun onDestroy() {
        if (::adapter.isInitialized) adapter.releaseAll()
        super.onDestroy()
    }

    private fun checkHealth() {
        lifecycleScope.launch {
            _repo!!.health().onSuccess {
                statusText.text = "Online · ${it.provider ?: "API"}"
            }.onFailure {
                statusText.text = "Offline"
            }
        }
    }

    fun loadFeed() {
        val spinner = findViewById<ProgressBar>(R.id.loadingSpinner)
        val empty = findViewById<TextView>(R.id.emptyStateText)
        spinner.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            _repo!!.getFeed(page = 1, perPage = 20)
                .onSuccess { feed ->
                    spinner.visibility = android.view.View.GONE
                    if (feed.items.isEmpty()) {
                        empty.visibility = android.view.View.VISIBLE
                    } else {
                        empty.visibility = android.view.View.GONE
                        adapter.submitList(feed.items)
                    }
                }
                .onFailure { err ->
                    spinner.visibility = android.view.View.GONE
                    Toast.makeText(this@ReelActivity, "Couldn't load feed: ${err.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun openComments(reel: ReelVideo) {
        CommentsBottomSheet.show(supportFragmentManager, reel.id)
    }
}
