package com.wall.mob;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class FavoriteFragment extends Fragment implements WallpaperAdapter.OnWallpaperClickListener {

    private RecyclerView recyclerView;
    private WallpaperAdapter adapter;
    private List<Wallpaper> favoriteWallpapers;
    private View emptyStateView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.favorite, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_view);
        emptyStateView = view.findViewById(R.id.empty_view);

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        favoriteWallpapers = new ArrayList<>();
        adapter = new WallpaperAdapter(requireContext(), favoriteWallpapers, this);
        recyclerView.setAdapter(adapter);

        loadFavorites();
    }

    public void refreshData() {
        loadFavorites();
    }

    private void loadFavorites() {
        favoriteWallpapers.clear();

        // Load all favorite wallpapers (both regular and premium)
        List<Wallpaper> favorites = FavoriteManager.getFavorites(requireContext());
        if (favorites != null) {
            favoriteWallpapers.addAll(favorites);
        }

        adapter.updateData(favoriteWallpapers);

        // Show empty state if no data
        if (favoriteWallpapers.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onWallpaperClick(Wallpaper wallpaper) {
        WallpaperDetailsActivity.start(requireContext(), wallpaper);
    }

    @Override
    public void onWallpaperLongClick(Wallpaper wallpaper, int position) {
        FavoriteManager.removeFromFavorites(requireContext(), wallpaper);
        favoriteWallpapers.remove(position);
        adapter.removeItem(position);

        if (favoriteWallpapers.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.VISIBLE);
        }

        Toast.makeText(requireContext(), "Removed from favorites", Toast.LENGTH_SHORT).show();
    }
}
// test
