package com.wall.mob;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PopularTabFragment extends Fragment implements WallpaperAdapter.OnWallpaperClickListener {

    private static final String TAG = "PopularTabFragment";

    private RecyclerView recyclerView;
    private WallpaperAdapter wallpaperAdapter;
    private List<Wallpaper> popularWallpapers = new ArrayList<>();
    private ProgressBar progressBar;
    private View errorView;
    private Button retryButton;
    private TextView errorMessage;
    
    private ApiManager apiManager;
    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Layout newly_tab ko hi reuse kar rahe hain kyunki IDs ek jaisi hain
        View view = inflater.inflate(R.layout.newly_tab, container, false);
        initializeViews(view);
        loadInitialData();
        return view;
    }

    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        errorView = view.findViewById(R.id.error_view);
        retryButton = view.findViewById(R.id.retry_button);
        errorMessage = view.findViewById(R.id.error_message);
        progressBar = view.findViewById(R.id.progress_bar);

        // NAYA: ApiManager setup, backend api me humne sort=popular implement kiya tha, wahi yahan set kar rahe hain.
        apiManager = new ApiManager(requireContext());
        apiManager.setSortOrder("popular");
        apiManager.setOrientation("portrait");

        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        recyclerView.setLayoutManager(layoutManager);
        wallpaperAdapter = new WallpaperAdapter(requireContext(), popularWallpapers, this);
        recyclerView.setAdapter(wallpaperAdapter);

        // NAYA: Scroll pagination
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int pastVisiblesItems = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + pastVisiblesItems) >= totalItemCount) {
                        apiManager.loadNextPage();
                    }
                }
            }
        });

        retryButton.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            loadInitialData();
        });
    }

    private void loadInitialData() {
        if (isLoading) return;
        isLoading = true;
        showLoading();
        
        apiManager.loadWallpapersFromAllSources(new ApiManager.ApiCallback() {
            @Override
            public void onWallpapersLoaded(List<Wallpaper> wallpapers) {
                popularWallpapers.clear();
                popularWallpapers.addAll(wallpapers);
                wallpaperAdapter.updateData(popularWallpapers);
                
                isLoading = false;
                hideLoading();
                
                if (popularWallpapers.isEmpty()) {
                    showError(getString(R.string.no_wallpapers_found));
                } else {
                    showContent();
                }
            }

            @Override
            public void onError(String message) {
                isLoading = false;
                showError(message);
            }

            @Override
            public void onMoreWallpapersLoaded(List<Wallpaper> newWallpapers) {
                int startPos = popularWallpapers.size();
                popularWallpapers.addAll(newWallpapers);
                wallpaperAdapter.notifyItemRangeInserted(startPos, newWallpapers.size());
            }
        });
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    private void showContent() {
        errorView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        hideLoading();
        errorMessage.setText(message);
        errorView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    @Override
    public void onWallpaperClick(Wallpaper wallpaper) {
        WallpaperDetailsActivity.start(requireContext(), wallpaper);
    }

    @Override
    public void onWallpaperLongClick(Wallpaper wallpaper, int position) {
        // Handle long click if needed
    }
    
    public void refreshData() {
        loadInitialData();
    }
}
