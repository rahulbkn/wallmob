package com.wall.mob;

import android.net.Uri;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Handles deep links like:
 * https://wallmob.pages.dev/w/{id}
 * https://wallmob.pages.dev/wallpaper/{id}
 */
public class WallpaperDeepLinkHandler {

    private static final String TAG = "WallpaperDeepLink";

    private final WallpaperDetailsActivity activity;
    private final TextView wallpaperTitle;
    private final TextView wallpaperAuthor;
    private final TextView wallpaperSource;
    private final WallpaperImageLoader imageLoader;

    public WallpaperDeepLinkHandler(
            WallpaperDetailsActivity activity,
            TextView wallpaperTitle,
            TextView wallpaperAuthor,
            TextView wallpaperSource,
            WallpaperImageLoader imageLoader
    ) {
        this.activity = activity;
        this.wallpaperTitle = wallpaperTitle;
        this.wallpaperAuthor = wallpaperAuthor;
        this.wallpaperSource = wallpaperSource;
        this.imageLoader = imageLoader;
    }

    /* ============================
       HANDLE DEEP LINK (ENTRY POINT)
       ============================ */
    public Wallpaper handleDeepLink(Uri uri) {
        if (uri == null) return null;

        Log.d(TAG, "Deep link received: " + uri);

        List<String> segments = uri.getPathSegments();
        if (segments == null || segments.size() < 2) {
            Log.e(TAG, "Invalid deep link path: " + uri.getPath());
            return null;
        }

        // Supports:
        // /w/{id}
        // /wallpaper/{id}
        String id = segments.get(1);

        Log.d(TAG, "Extracted wallpaper id: " + id);

        loadWallpaperById(id, new Callback() {
            @Override
            public void onSuccess(Wallpaper wallpaper) {
                updateUIWithWallpaper(wallpaper);
            }

            @Override
            public void onError() {
                Toast.makeText(activity,
                        "Wallpaper not found",
                        Toast.LENGTH_SHORT).show();
                activity.finish();
            }
        });

        // Loaded async → return null for now
        return null;
    }

    /* ============================
       CALLBACK
       ============================ */
    public interface Callback {
        void onSuccess(Wallpaper wallpaper);
        void onError();
    }

    /* ============================
       LOAD WALLPAPER BY ID
       ============================ */
    public void loadWallpaperById(String id, Callback callback) {
        ExecutorService executor = activity.getExecutorService();

        executor.execute(() -> {
            try {
                Wallpaper wallpaper = WallpaperRepository
                        .getInstance()
                        .getWallpaperById(id);

                activity.runOnUiThread(() -> {
                    if (wallpaper != null) {
                        callback.onSuccess(wallpaper);
                    } else {
                        Log.e(TAG, "Wallpaper not found for id: " + id);
                        callback.onError();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to load wallpaper", e);
                activity.runOnUiThread(callback::onError);
            }
        });
    }

    /* ============================
       UPDATE UI
       ============================ */
    public void updateUIWithWallpaper(Wallpaper wallpaper) {
        if (wallpaper == null || activity.isDestroyedOrFinishing()) return;

        // Title
                wallpaperTitle.setText(
                        wallpaper.getTitle() != null
                                ? wallpaper.getTitle()
                                : activity.getString(R.string.wallpaper)
                );

        // Author / Category
        if (wallpaper.getPhotographer() != null && !wallpaper.getPhotographer().isEmpty()) {
            wallpaperAuthor.setText(activity.getString(R.string.by_author, wallpaper.getPhotographer()));
        } else if (wallpaper.getCategory() != null && !wallpaper.getCategory().isEmpty()) {
            wallpaperAuthor.setText(activity.getString(R.string.category_value, wallpaper.getCategory()));
        } else {
            wallpaperAuthor.setText(R.string.unknown_author);
        }

        // Source
        if (wallpaper.getSource() != null && !wallpaper.getSource().isEmpty()) {
            wallpaperSource.setText(activity.getString(R.string.source_value, wallpaper.getSource()));
        } else {
            wallpaperSource.setText(R.string.unknown_source);
        }

        // Custom font (safe)
        try {
            wallpaperTitle.setTypeface(
                    ResourcesCompat.getFont(activity, R.font.myfont)
            );
        } catch (Exception e) {
            Log.w(TAG, "Custom font not applied", e);
        }

        // Image
        if (wallpaper.getImageUrl() != null) {
            imageLoader.loadImage(wallpaper.getImageUrl());
        } else {
            Toast.makeText(activity, activity.getString(R.string.image_not_available), Toast.LENGTH_SHORT).show();
        }
    }
}
// test
