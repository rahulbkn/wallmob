package com.wall.mob;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

public class WallpaperImageLoader {

    private static final String TAG = "WallpaperImageLoader";

    private final WallpaperDetailsActivity activity;
    private final ImageView wallpaperImage;

    public WallpaperImageLoader(WallpaperDetailsActivity activity, ImageView wallpaperImage) {
        this.activity = activity;
        this.wallpaperImage = wallpaperImage;
    }

    /**
     * ✅ FIXED: Loads image with blur-up effect.
     * Previously used CustomTarget with override(50,50) which caused race conditions.
     * Now uses Glide's built-in .thumbnail() chain — no race condition, no blank screen.
     *
     * @param imageUrl Full-quality image URL (can be a Cloudflare Worker proxy URL)
     */
    public void loadImage(String imageUrl) {
        if (activity.isDestroyedOrFinishing()) return;

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            Log.e(TAG, "imageUrl is null or empty, skipping load");
            return;
        }

        Log.d(TAG, "loadImage() called with: " + imageUrl);

        // ✅ Low-res thumbnail as instant blur placeholder
        RequestOptions thumbOptions = new RequestOptions()
                .override(80, 120)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop();

        // ✅ Full quality options
        RequestOptions fullOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop();

        Glide.with(activity)
                .load(imageUrl)
                .apply(fullOptions)
                .thumbnail(
                        Glide.with(activity)
                                .load(imageUrl)
                                .apply(thumbOptions)
                )
                .transition(DrawableTransitionOptions.withCrossFade(600))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e,
                                               Object model,
                                               Target<Drawable> target,
                                               boolean isFirstResource) {
                        Log.e(TAG, "Image load failed for URL: " + imageUrl
                                + " | Error: " + (e != null ? e.getMessage() : "unknown"), e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource,
                                                  Object model,
                                                  Target<Drawable> target,
                                                  DataSource dataSource,
                                                  boolean isFirstResource) {
                        Log.d(TAG, "Image ready. Source: " + dataSource.name());
                        return false;
                    }
                })
                .into(wallpaperImage);
    }

    /**
     * Overload: Load from Uri (e.g. after user edits the wallpaper).
     *
     * @param imageUri   Local file URI
     * @param skipCache  true = bypass Glide cache (use after editing)
     */
    public void loadImage(Uri imageUri, boolean skipCache) {
        if (activity.isDestroyedOrFinishing()) return;

        if (imageUri == null) {
            Log.e(TAG, "imageUri is null, skipping load");
            return;
        }

        try {
            Glide.with(activity)
                    .load(imageUri)
                    .diskCacheStrategy(skipCache ? DiskCacheStrategy.NONE : DiskCacheStrategy.ALL)
                    .skipMemoryCache(skipCache)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .into(wallpaperImage);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load image from URI: " + e.getMessage(), e);
        }
    }
}

// test
