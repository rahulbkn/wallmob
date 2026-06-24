package com.wall.mob;

import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PremiumFragment extends Fragment implements WallpaperAdapter.OnWallpaperClickListener {

    private static final String TAG = "PremiumFragment";

    private RecyclerView recyclerView;
    private WallpaperAdapter wallpaperAdapter;
    private List<Wallpaper> premiumWallpapers = new ArrayList<>();
    private ProgressBar progressBar;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerViewContainer;
    private View errorView;
    private Button retryButton;
    private TextView errorMessage;
    private DatabaseReference firebasePremiumRef;

    private boolean isLoading = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.premium, container, false);
        initializeViews(view);
        loadInitialData();
        return view;
    }

    private void applyHeaderPadding(View view) {
        View header = view.findViewById(R.id.premium_header);
        if (header == null) return;
        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        int actionBarHeight = 0;
        TypedValue tv = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
        }
        header.setPadding(
                header.getPaddingStart(),
                statusBarHeight + actionBarHeight,
                header.getPaddingEnd(),
                header.getPaddingBottom()
        );
    }

    private void initializeViews(View view) {
        applyHeaderPadding(view);

        recyclerView = view.findViewById(R.id.recyclerView);
        errorView = view.findViewById(R.id.error_view);
        retryButton = view.findViewById(R.id.retry_button);
        errorMessage = view.findViewById(R.id.error_message);
        progressBar = view.findViewById(R.id.progress_bar);
        shimmerViewContainer = view.findViewById(R.id.shimmer_view_container);

        firebasePremiumRef = FirebaseDatabase.getInstance().getReference("wallpapers/premium");

        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        recyclerView.setLayoutManager(layoutManager);
        wallpaperAdapter = new WallpaperAdapter(requireContext(), premiumWallpapers, this);
        recyclerView.setAdapter(wallpaperAdapter);

        retryButton.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            loadInitialData();
        });
    }

    private void loadInitialData() {
        if (isLoading) return;
        isLoading = true;
        showMainLoading();
        premiumWallpapers.clear();

        // 🔥 FIXED: Direct single-fetch logic for data to ensure it populates
        firebasePremiumRef.orderByChild("addedAt").limitToLast(100).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> uniqueIds = new HashSet<>();
                List<Wallpaper> tempList = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Wallpaper w = parseWallpaperManually(child);
                    if (w != null && !uniqueIds.contains(w.getId())) {
                        uniqueIds.add(w.getId());
                        tempList.add(0, w); // Add Latest at the top
                    }
                }
                
                premiumWallpapers.addAll(tempList);
                wallpaperAdapter.updateData(premiumWallpapers);
                
                isLoading = false;
                hideMainLoading();
                
                if (premiumWallpapers.isEmpty()) {
                    showError("No premium wallpapers available right now.");
                } else {
                    showContent();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isLoading = false;
                showError("Failed to load premium wallpapers. Check your connection.");
            }
        });
    }

    private Wallpaper parseWallpaperManually(DataSnapshot snapshot) {
        try {
            Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
            if (map == null) return null;

            String id = snapshot.getKey();
            String imageUrl = (String) map.get("imageUrl");
            String thumbnailUrl = (String) map.get("thumbnailUrl");
            
            if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
                thumbnailUrl = imageUrl; 
            }

            return new Wallpaper(
                id, 
                imageUrl, 
                thumbnailUrl, 
                (String) map.get("title"),
                (String) map.get("category"), 
                (String) map.get("source"), 
                (String) map.get("photographer"), 
                true
            );
        } catch (Exception e) {
            Log.e(TAG, "Error parsing wallpaper", e);
            return null;
        }
    }

    private void showMainLoading() {
        if (shimmerViewContainer != null) {
            shimmerViewContainer.setVisibility(View.VISIBLE);
            shimmerViewContainer.startShimmer();
        }
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
    }

    private void hideMainLoading() {
        if (shimmerViewContainer != null) {
            shimmerViewContainer.stopShimmer();
            shimmerViewContainer.setVisibility(View.GONE);
        }
        progressBar.setVisibility(View.GONE);
    }

    private void showContent() {
        errorView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        hideMainLoading();
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
        Toast.makeText(requireContext(), getString(R.string.long_clicked, wallpaper.getTitle()), Toast.LENGTH_SHORT).show();
    }

    public void refreshData() {
        loadInitialData();
    }

    public boolean isLoadingData() {
        return isLoading;
    }
}

// test
