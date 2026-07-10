package com.wall.mob;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DownloadsFragment extends Fragment implements WallpaperAdapter.OnWallpaperClickListener {

    private RecyclerView recyclerView;
    private WallpaperAdapter adapter;
    private List<Wallpaper> downloadedWallpapers;

    private LinearLayout emptyStateView;
    private ProgressBar progressBar;
    private View selectionBar;
    private Button btnSelectAll, btnDeleteSelected, btnCancelSelection;

    private boolean isSelectionMode = false;
    private Set<Integer> selectedPositions = new HashSet<>();

    private static final String DOWNLOAD_FOLDER_NAME = "WallMob";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.downloads, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_downloads);
        emptyStateView = view.findViewById(R.id.empty_state_view);
        progressBar = view.findViewById(R.id.progress_bar);
        selectionBar = view.findViewById(R.id.selection_bar);
        btnSelectAll = view.findViewById(R.id.btn_select_all);
        btnDeleteSelected = view.findViewById(R.id.btn_delete_selected);
        btnCancelSelection = view.findViewById(R.id.btn_cancel_selection);

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        downloadedWallpapers = new ArrayList<>();

        adapter = new WallpaperAdapter(requireContext(), downloadedWallpapers, this);
        recyclerView.setAdapter(adapter);

        if (btnSelectAll != null) btnSelectAll.setOnClickListener(v -> toggleSelectAll());
        if (btnDeleteSelected != null) btnDeleteSelected.setOnClickListener(v -> deleteSelected());
        if (btnCancelSelection != null) btnCancelSelection.setOnClickListener(v -> exitSelectionMode());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDownloadedFiles();
    }

    public void refreshData() {
        loadDownloadedFiles();
    }

    private void loadDownloadedFiles() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        SketchApplication.getIoExecutor().execute(() -> {
            File storageDir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    DOWNLOAD_FOLDER_NAME
            );

            List<Wallpaper> loaded = new ArrayList<>();

            if (storageDir.exists() && storageDir.isDirectory()) {
                File[] files = storageDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().toLowerCase().endsWith(".jpg") ||
                            file.getName().toLowerCase().endsWith(".png") ||
                            file.getName().toLowerCase().endsWith(".jpeg")) {

                            String localUri = Uri.fromFile(file).toString();
                            Wallpaper localWallpaper = new Wallpaper(
                                    file.getName(),
                                    localUri,
                                    localUri,
                                    file.getName(),
                                    "Downloads",
                                    "Device",
                                    false
                            );
                            loaded.add(localWallpaper);
                        }
                    }
                }
            }

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                downloadedWallpapers.clear();
                downloadedWallpapers.addAll(loaded);

                if (adapter != null) {
                    adapter.updateData(downloadedWallpapers);
                }
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                if (downloadedWallpapers.isEmpty()) {
                    if (recyclerView != null) recyclerView.setVisibility(View.GONE);
                    if (emptyStateView != null) emptyStateView.setVisibility(View.VISIBLE);
                } else {
                    if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
                    if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
                }
            });
        });
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
        if (selectedPositions.size() == downloadedWallpapers.size()) {
            selectedPositions.clear();
        } else {
            selectedPositions.clear();
            for (int i = 0; i < downloadedWallpapers.size(); i++) {
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
            Toast.makeText(requireContext(), "Select wallpapers to delete", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Integer> toRemove = new ArrayList<>(selectedPositions);
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            int pos = toRemove.get(i);
            if (pos >= 0 && pos < downloadedWallpapers.size()) {
                Wallpaper wp = downloadedWallpapers.get(pos);
                try {
                    Uri fileUri = Uri.parse(wp.getImageUrl());
                    File fileToDelete = new File(fileUri.getPath());
                    if (fileToDelete.exists()) fileToDelete.delete();
                } catch (Exception ignored) {}
            }
        }

        loadDownloadedFiles();
        exitSelectionMode();
        Toast.makeText(requireContext(), "Deleted " + toRemove.size() + " wallpapers", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onWallpaperClick(Wallpaper wallpaper) {
        if (isSelectionMode) {
            int pos = downloadedWallpapers.indexOf(wallpaper);
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
        android.util.Log.d("DownloadsFragment", "onWallpaperLongClick called. Current isSelectionMode: " + isSelectionMode);
        if (!isSelectionMode) {
            toggleSelectionMode();
        }
        android.util.Log.d("DownloadsFragment", "After toggle, isSelectionMode: " + isSelectionMode);
        if (isSelectionMode) {
            if (selectedPositions.contains(position)) selectedPositions.remove(position);
            else selectedPositions.add(position);
            updateSelectionButtonText();
            if (selectedPositions.isEmpty()) exitSelectionMode();
        }
    }
}
