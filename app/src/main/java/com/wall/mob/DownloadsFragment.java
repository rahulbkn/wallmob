package com.wall.mob;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;

public class DownloadsFragment extends Fragment implements WallpaperAdapter.OnWallpaperClickListener {

    private RecyclerView recyclerView;
    private WallpaperAdapter adapter;
    private List<Wallpaper> downloadedWallpapers;
    
    private LinearLayout emptyStateView;
    private ProgressBar progressBar;

    private static final String DOWNLOAD_FOLDER_NAME = "WallMob"; 

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.downloads, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Using the IDs from your fragment_downloads.xml
        recyclerView = view.findViewById(R.id.recycler_downloads);
        emptyStateView = view.findViewById(R.id.empty_state_view);
        progressBar = view.findViewById(R.id.progress_bar);

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        downloadedWallpapers = new ArrayList<>();
        
        // Reusing your main WallpaperAdapter!
        adapter = new WallpaperAdapter(requireContext(), downloadedWallpapers, this);
        recyclerView.setAdapter(adapter);

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
        progressBar.setVisibility(View.VISIBLE);
        downloadedWallpapers.clear();

        File storageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                DOWNLOAD_FOLDER_NAME
        );

        if (storageDir.exists() && storageDir.isDirectory()) {
            File[] files = storageDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().toLowerCase().endsWith(".jpg") || 
                        file.getName().toLowerCase().endsWith(".png") || 
                        file.getName().toLowerCase().endsWith(".jpeg")) {
                        
                        // Convert File to a Wallpaper object so the adapter can read it
                        String localUri = Uri.fromFile(file).toString();
                        Wallpaper localWallpaper = new Wallpaper(
                                file.getName(), // ID
                                localUri,       // Image URL (Local File URI)
                                file.getName(), // Title
                                "Local",        // Category
                                "Downloads",    // Source
                                "Device",       // Author
                                false           // isPremium
                        );
                        
                        downloadedWallpapers.add(localWallpaper);
                    }
                }
            }
        }

        adapter.updateData(downloadedWallpapers);
        progressBar.setVisibility(View.GONE);

        // Show empty state if no data
        if (downloadedWallpapers.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onWallpaperClick(Wallpaper wallpaper) {
        // Open local file in detail view
        WallpaperDetailsActivity.start(requireContext(), wallpaper);
    }

    @Override
    public void onWallpaperLongClick(Wallpaper wallpaper, int position) {
        // Find the actual file path using the URI we saved in the Wallpaper object
        try {
            Uri fileUri = Uri.parse(wallpaper.getImageUrl());
            File fileToDelete = new File(fileUri.getPath());
            
            if (fileToDelete.exists() && fileToDelete.delete()) {
                downloadedWallpapers.remove(position);
                adapter.removeItem(position);

                if (downloadedWallpapers.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyStateView.setVisibility(View.VISIBLE);
                }

                Toast.makeText(requireContext(), getString(R.string.deleted_successfully), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), getString(R.string.failed_delete_file), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.error_deleting_file), Toast.LENGTH_SHORT).show();
        }
    }
}

// test
