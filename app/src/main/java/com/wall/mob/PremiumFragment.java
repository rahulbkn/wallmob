package com.wall.mob;

import android.os.Bundle;
import android.util.Log;
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

    private boolean isLoading = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.premium, container, false);
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
        shimmerViewContainer = view.findViewById(R.id.shimmer_view_container);

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

        DatabaseReference premiumRef = FirebaseDatabase.getInstance().getReference("wallpapers/premium");
        DatabaseReference newlyRef = FirebaseDatabase.getInstance().getReference("wallpapers/newly_added");

        // Use a counter to know when both are loaded
        final int[] completedTasks = {0};
        final Set<String> uniqueIds = new HashSet<>();
        final List<Wallpaper> tempList = new ArrayList<>();

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    Wallpaper w = parseWallpaperManually(child);
                    // Filter: Must be premium.
                    if (w != null && w.isPremium() && !uniqueIds.contains(w.getId())) {
                        uniqueIds.add(w.getId());
                        tempList.add(0, w); // Add Latest at the top
                    }
                }
                completedTasks[0]++;
                if (completedTasks[0] == 2) {
                    processMergedData(tempList);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                completedTasks[0]++;
                if (completedTasks[0] == 2) {
                    processMergedData(tempList);
                }
            }
        };

        premiumRef.orderByChild("addedAt").limitToLast(100).addListenerForSingleValueEvent(listener);
        newlyRef.orderByChild("addedAt").limitToLast(100).addListenerForSingleValueEvent(listener);
    }

    private void processMergedData(List<Wallpaper> mergedList) {
        premiumWallpapers.addAll(mergedList);
        wallpaperAdapter.updateData(premiumWallpapers);

        isLoading = false;
        hideMainLoading();

        if (premiumWallpapers.isEmpty()) {
            showError("No premium wallpapers available right now.");
        } else {
            showContent();
        }
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
