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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NewlyTabFragment extends Fragment implements WallpaperAdapter.OnWallpaperClickListener {

    private static final String TAG = "NewlyTabFragment";

    private RecyclerView recyclerView;
    private WallpaperAdapter wallpaperAdapter;
    private List<Wallpaper> NewlyWallpapers = new ArrayList<>();
    private ProgressBar progressBar;
    private View errorView;
    private Button retryButton;
    private TextView errorMessage;
    private DatabaseReference firebaseNewlyRef;

    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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

        firebaseNewlyRef = FirebaseDatabase.getInstance().getReference("wallpapers/newly_added");

        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        recyclerView.setLayoutManager(layoutManager);
        wallpaperAdapter = new WallpaperAdapter(requireContext(), NewlyWallpapers, this);
        recyclerView.setAdapter(wallpaperAdapter);

        retryButton.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            loadInitialData();
        });
    }

    private void loadInitialData() {
        if (isLoading) return;
        isLoading = true;
        showLoading();
        NewlyWallpapers.clear();

        firebaseNewlyRef.orderByChild("addedAt").limitToLast(100).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> uniqueIds = new HashSet<>();
                List<Wallpaper> tempList = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Wallpaper w = parseWallpaperManually(child);
                    if (w != null && !uniqueIds.contains(w.getId())) {
                        uniqueIds.add(w.getId());
                        tempList.add(0, w);
                    }
                }

                NewlyWallpapers.addAll(tempList);
                wallpaperAdapter.updateData(NewlyWallpapers);

                isLoading = false;
                hideLoading();

                if (NewlyWallpapers.isEmpty()) {
                    showError(getString(R.string.no_wallpapers_found));
                } else {
                    showContent();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isLoading = false;
                showError(getString(R.string.failed_to_load_wallpapers));
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

            Boolean isPremium = (Boolean) map.get("premium");
            if (isPremium == null) isPremium = false;

            return new Wallpaper(
                id,
                imageUrl,
                thumbnailUrl,
                (String) map.get("title"),
                (String) map.get("category"),
                (String) map.get("source"),
                (String) map.get("photographer"),
                isPremium
            );
        } catch (Exception e) {
            Log.e(TAG, "Error parsing wallpaper", e);
            return null;
        }
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
    }

    public void refreshData() {
        loadInitialData();
    }
}
