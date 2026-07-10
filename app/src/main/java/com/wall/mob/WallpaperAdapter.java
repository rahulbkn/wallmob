package com.wall.mob;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.WallpaperViewHolder> {

    private static final String TAG = "WallpaperAdapter";

    private List<Wallpaper> wallpaperList;
    private Set<String> displayedIds = new HashSet<>();
    private Set<String> favoriteIds = new HashSet<>();
    private OnWallpaperClickListener listener;
    private Context context;
    private boolean forceLowQuality = false;

    public interface OnWallpaperClickListener {
        void onWallpaperClick(Wallpaper wallpaper);
        void onWallpaperLongClick(Wallpaper wallpaper, int position);
        default void onFavoriteClick(Wallpaper wallpaper, boolean isFavorite) {}
    }

    public WallpaperAdapter(Context context, List<Wallpaper> wallpaperList, OnWallpaperClickListener listener) {
        this(context, wallpaperList, listener, false);
    }

    public WallpaperAdapter(Context context, List<Wallpaper> wallpaperList, OnWallpaperClickListener listener, boolean forceLowQuality) {
        this.context = context.getApplicationContext();
        this.wallpaperList = new ArrayList<>();
        this.forceLowQuality = forceLowQuality;
        this.listener = listener;
        updateData(wallpaperList != null ? wallpaperList : new ArrayList<>());
    }

    @NonNull
    @Override
    public WallpaperViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View wallpaperView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallpaper, parent, false);
        return new WallpaperViewHolder(wallpaperView);
    }

    @Override
    public void onBindViewHolder(@NonNull WallpaperViewHolder holder, int position) {
        holder.bindWallpaper(wallpaperList.get(position));
    }

    @Override
    public int getItemCount() {
        return wallpaperList.size();
    }

    public void updateData(List<Wallpaper> newWallpapers) {
        refreshFavoriteIds();
        displayedIds.clear();
        wallpaperList.clear();
        if (newWallpapers != null) {
            for (Wallpaper w : newWallpapers) {
                if (w != null && displayedIds.add(w.getId())) wallpaperList.add(w);
            }
        }
        notifyDataSetChanged();
    }

    public void refreshFavoriteIds() {
        SketchApplication.getIoExecutor().execute(() -> {
            Set<String> ids = FavoriteManager.getFavoriteIds(context);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                favoriteIds.clear();
                favoriteIds.addAll(ids);
                notifyDataSetChanged();
            });
        });
    }

    public void addData(List<Wallpaper> moreWallpapers) {
        if (moreWallpapers == null || moreWallpapers.isEmpty()) return;
        int startPosition = wallpaperList.size();
        List<Wallpaper> filtered = new ArrayList<>();
        for (Wallpaper w : moreWallpapers) {
            if (w != null && displayedIds.add(w.getId())) filtered.add(w);
        }
        wallpaperList.addAll(filtered);
        notifyItemRangeInserted(startPosition, filtered.size());
    }

    // 🔥 FIXED: Added missing method for FavoriteFragment
    public void removeItem(int position) {
        if (position >= 0 && position < wallpaperList.size()) {
            String id = wallpaperList.get(position).getId();
            wallpaperList.remove(position);
            displayedIds.remove(id);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, getItemCount() - position);
        }
    }

    private boolean isFavorite(Wallpaper wallpaper) {
        return favoriteIds.contains(wallpaper.getId());
    }

    private void toggleFavorite(Wallpaper wallpaper) {
        boolean wasAdded;
        if (favoriteIds.contains(wallpaper.getId())) {
            FavoriteManager.removeFromFavorites(context, wallpaper);
            favoriteIds.remove(wallpaper.getId());
            wasAdded = false;
        } else {
            FavoriteManager.addToFavorites(context, wallpaper);
            favoriteIds.add(wallpaper.getId());
            wasAdded = true;
        }
        if (listener != null) listener.onFavoriteClick(wallpaper, wasAdded);
    }

    private String generateThumbnailUrl(String originalUrl, String source) {
        if (originalUrl == null || originalUrl.isEmpty()) return originalUrl;
        if (source == null) return originalUrl;
        switch (source.toLowerCase()) {
            case "pexels":
                if (originalUrl.contains("pexels.com")) return originalUrl.replace("/original/", "/large/").replace("/large2x/", "/large/").replace("/small/", "/large/");
                break;
            case "unsplash":
                if (originalUrl.contains("unsplash.com")) {
                    String url = originalUrl.replaceAll("[?&]w=\\d+", "").replaceAll("[?&]h=\\d+", "").replaceAll("[?&]q=\\d+", "");
                    return url + (url.contains("?") ? "&" : "?") + "w=800&h=1200&q=80";
                }
                break;
            case "pixabay":
                if (originalUrl.contains("pixabay.com")) return originalUrl.replaceAll("_(\\d+)\\.", "_960.");
                break;
            case "wallhaven":
                if (originalUrl.contains("wallhaven.cc")) return originalUrl.replace("/small/", "/lg/");
                break;
            case "firebase":
            case "custom":
                if (originalUrl.contains("cloudinary.com")) return originalUrl.replaceAll("w_\\d+", "w_800");
                break;
        }
        return originalUrl;
    }

    private String generateBlurPreviewUrl(String originalUrl, String source) {
        if (originalUrl == null || originalUrl.isEmpty() || source == null) return originalUrl;
        switch (source.toLowerCase()) {
            case "pexels": return originalUrl.replace("/large/", "/tiny/");
            case "unsplash": return originalUrl + "&blur=8";
            case "wallhaven": return originalUrl.replace("/lg/", "/small/").replace("/full/", "/small/");
            case "firebase":
            case "custom": if (originalUrl.contains("cloudinary.com")) return originalUrl.replaceAll("w_\\d+", "w_80").replaceAll("q_\\d+", "q_20");
        }
        return originalUrl;
    }

    private void applyBlurLikeFilter(ImageView imageView) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0.3f);
        imageView.setColorFilter(new ColorMatrixColorFilter(matrix));
        imageView.setAlpha(0.7f);
    }

    private void removeBlurLikeFilter(ImageView imageView) {
        imageView.setColorFilter(null);
        imageView.setAlpha(1.0f);
    }

    class WallpaperViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView title;
        LottieAnimationView lottieLoader;
        ImageView premiumBadge, favoriteIcon;
        TextView resolutionBadge;

        public WallpaperViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.wallpaper_image);
            title = itemView.findViewById(R.id.wallpaper_title);
            lottieLoader = itemView.findViewById(R.id.lottie_loader);
            premiumBadge = itemView.findViewById(R.id.premium_badge);
            favoriteIcon = itemView.findViewById(R.id.favorite_icon);
            resolutionBadge = itemView.findViewById(R.id.resolution_badge);
        }

        void bindWallpaper(Wallpaper wallpaper) {
            if (wallpaper == null) return;
            if (premiumBadge != null) premiumBadge.setVisibility(wallpaper.isPremium() ? View.VISIBLE : View.GONE);
            favoriteIcon.setImageResource(R.drawable.ic_favorite_selector);
            favoriteIcon.setSelected(isFavorite(wallpaper));
            favoriteIcon.setOnClickListener(v -> { toggleFavorite(wallpaper); favoriteIcon.setSelected(isFavorite(wallpaper)); });

            // Clear previous Glide load to prevent memory leaks
            Glide.with(context).clear(imageView);
            
            lottieLoader.setVisibility(View.VISIBLE); lottieLoader.playAnimation();

            String imageUrl = wallpaper.getImageUrl();
            if (imageUrl == null || imageUrl.isEmpty()) { lottieLoader.setVisibility(View.GONE); return; }
        
            // Ensure thumbnail URL is never null
            if (wallpaper.getThumbnailUrl() == null) {
                wallpaper.setThumbnailUrl(imageUrl);
            }

            String storedThumb = wallpaper.getThumbnailUrl();
            String thumbnailUrl = (storedThumb != null && !storedThumb.trim().isEmpty()) ? storedThumb : generateThumbnailUrl(imageUrl, wallpaper.getSource());
            String blurPreviewUrl = generateBlurPreviewUrl(thumbnailUrl, wallpaper.getSource());

            NetworkUtils.ConnectionSpeed connectionSpeed = NetworkUtils.getConnectionSpeed(context);
            RequestOptions thumbnailOptions = new RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL).centerCrop().format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565);
            RequestOptions fullOptions = new RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL).centerCrop();

            // Force low quality for search and see all activities
            if (forceLowQuality) {
                thumbnailOptions = thumbnailOptions.override(400, 600);
                fullOptions = fullOptions.override(400, 600).priority(com.bumptech.glide.Priority.LOW);
            } else {
                switch (connectionSpeed) {
                    case SLOW:
                        thumbnailOptions = thumbnailOptions.override(300, 450);
                        fullOptions = fullOptions.override(600, 900).priority(com.bumptech.glide.Priority.LOW);
                        break;
                    case MEDIUM:
                        thumbnailOptions = thumbnailOptions.override(600, 900);
                        fullOptions = fullOptions.override(800, 1200).priority(com.bumptech.glide.Priority.NORMAL);
                        break;
                    case FAST:
                        thumbnailOptions = thumbnailOptions.override(800, 1200);
                        fullOptions = fullOptions.priority(com.bumptech.glide.Priority.HIGH);
                        break;
                    default:
                        // Default to medium speed settings
                        thumbnailOptions = thumbnailOptions.override(600, 900);
                        fullOptions = fullOptions.override(800, 1200).priority(com.bumptech.glide.Priority.NORMAL);
                        break;
                }
            }

            RequestBuilder<Drawable> blurPlaceholder = Glide.with(context).load(blurPreviewUrl)
                    .apply(new RequestOptions().override(80, 120))
                    .listener(new RequestListener<Drawable>() {
                        @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) { return false; }
                        @Override public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) { applyBlurLikeFilter(imageView); return false; }
                    });

            RequestBuilder<Drawable> thumbnailWithBlur = Glide.with(context).load(thumbnailUrl)
                    .apply(thumbnailOptions).thumbnail(blurPlaceholder)
                    .listener(new RequestListener<Drawable>() {
                        @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) { return false; }
                        @Override public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) { removeBlurLikeFilter(imageView); return false; }
                    });

            Glide.with(context).load(imageUrl).apply(fullOptions).thumbnail(thumbnailWithBlur)
                    .transition(DrawableTransitionOptions.withCrossFade(800)).error(R.drawable.error_image)
                    .listener(new RequestListener<Drawable>() {
                        @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) { lottieLoader.setVisibility(View.GONE); removeBlurLikeFilter(imageView); return false; }
                        @Override public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) { lottieLoader.setVisibility(View.GONE); removeBlurLikeFilter(imageView); return false; }
                    }).into(imageView);

            if (resolutionBadge != null) {
                int w = wallpaper.getWidth();
                int h = wallpaper.getHeight();
                if (w > 0 && h > 0) {
                    resolutionBadge.setText(w + "×" + h);
                    resolutionBadge.setVisibility(View.VISIBLE);
                } else {
                    resolutionBadge.setVisibility(View.GONE);
                }
            }

            if (title != null) title.setText(wallpaper.getTitle() == null ? context.getString(R.string.wallpaper) : wallpaper.getTitle());
            itemView.setOnClickListener(v -> { if (listener != null) listener.onWallpaperClick(wallpaper); });
            itemView.setOnLongClickListener(v -> { if (listener != null) { listener.onWallpaperLongClick(wallpaper, getAdapterPosition()); return true; } return false; });
        }
    }

    public static Set<String> getFavoriteWallpaperIds(Context context) {
        List<Wallpaper> favorites = FavoriteManager.getFavorites(context);
        Set<String> favoriteIds = new HashSet<>();
        for (Wallpaper wallpaper : favorites) {
            favoriteIds.add(wallpaper.getId());
        }
        return favoriteIds;
    }

    public static void clearAllFavorites(Context context) {
        FavoriteManager.saveFavorites(context, new ArrayList<>());
    }
}
// test
