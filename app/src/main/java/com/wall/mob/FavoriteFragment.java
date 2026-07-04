package com.wall.mob;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavoriteFragment extends Fragment implements WallpaperAdapter.OnWallpaperClickListener {

    private RecyclerView recyclerView;
    private WallpaperAdapter adapter;
    private List<Wallpaper> favoriteWallpapers;
    private View emptyStateView;
    private View selectionBar;
    private Button btnSelectAll, btnDeleteSelected, btnCancelSelection;

    private boolean isSelectionMode = false;
    private Set<Integer> selectedPositions = new HashSet<>();

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
        selectionBar = view.findViewById(R.id.selection_bar);
        btnSelectAll = view.findViewById(R.id.btn_select_all);
        btnDeleteSelected = view.findViewById(R.id.btn_delete_selected);
        btnCancelSelection = view.findViewById(R.id.btn_cancel_selection);

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        favoriteWallpapers = new ArrayList<>();
        adapter = new WallpaperAdapter(requireContext(), favoriteWallpapers, this);
        recyclerView.setAdapter(adapter);

        if (btnSelectAll != null) btnSelectAll.setOnClickListener(v -> toggleSelectAll());
        if (btnDeleteSelected != null) btnDeleteSelected.setOnClickListener(v -> deleteSelected());
        if (btnCancelSelection != null) btnCancelSelection.setOnClickListener(v -> exitSelectionMode());

        loadFavorites();
    }

    public void refreshData() {
        loadFavorites();
    }

    private void loadFavorites() {
        favoriteWallpapers.clear();

        List<Wallpaper> favorites = FavoriteManager.getFavorites(requireContext());
        if (favorites != null) {
            favoriteWallpapers.addAll(favorites);
        }

        adapter.updateData(favoriteWallpapers);

        if (favoriteWallpapers.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateView.setVisibility(View.GONE);
        }
    }

    private void toggleSelectionMode() {
        isSelectionMode = !isSelectionMode;
        if (!isSelectionMode) exitSelectionMode();
        if (selectionBar != null) selectionBar.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedPositions.clear();
        if (selectionBar != null) selectionBar.setVisibility(View.GONE);
    }

    private void toggleSelectAll() {
        if (selectedPositions.size() == favoriteWallpapers.size()) {
            selectedPositions.clear();
        } else {
            selectedPositions.clear();
            for (int i = 0; i < favoriteWallpapers.size(); i++) {
                selectedPositions.add(i);
            }
        }
        updateSelectionButtonText();
    }

    private void updateSelectionButtonText() {
        if (btnDeleteSelected != null) {
            btnDeleteSelected.setText("Delete (" + selectedPositions.size() + ")");
        }
    }

    private void deleteSelected() {
        if (selectedPositions.isEmpty()) {
            Toast.makeText(requireContext(), "Select favorites to remove", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Integer> toRemove = new ArrayList<>(selectedPositions);
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            int pos = toRemove.get(i);
            if (pos >= 0 && pos < favoriteWallpapers.size()) {
                Wallpaper wp = favoriteWallpapers.get(pos);
                FavoriteManager.removeFromFavorites(requireContext(), wp);
            }
        }

        loadFavorites();
        exitSelectionMode();
        Toast.makeText(requireContext(), "Removed " + toRemove.size() + " favorites", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onWallpaperClick(Wallpaper wallpaper) {
        if (isSelectionMode) {
            int pos = favoriteWallpapers.indexOf(wallpaper);
            if (pos >= 0) {
                if (selectedPositions.contains(pos)) selectedPositions.remove(pos);
                else selectedPositions.add(pos);
                updateSelectionButtonText();
            }
        } else {
            WallpaperDetailsActivity.start(requireContext(), wallpaper);
        }
    }

    @Override
    public void onWallpaperLongClick(Wallpaper wallpaper, int position) {
        if (!isSelectionMode) {
            toggleSelectionMode();
        }
        if (isSelectionMode) {
            if (selectedPositions.contains(position)) selectedPositions.remove(position);
            else selectedPositions.add(position);
            updateSelectionButtonText();
            if (selectedPositions.isEmpty()) exitSelectionMode();
        }
    }
}
