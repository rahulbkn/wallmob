package com.wall.mob;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.ArrayList;
import java.util.List;

public class CategoryWallpaperAdapter extends RecyclerView.Adapter<CategoryWallpaperAdapter.ViewHolder> {

    private Context context;
    private List<Wallpaper> wallpapers = new ArrayList<>();
    private WallpaperAdapter.OnWallpaperClickListener listener;

    public CategoryWallpaperAdapter(Context context, List<Wallpaper> wallpapers,
                                    WallpaperAdapter.OnWallpaperClickListener listener) {
        this.context = context;
        this.wallpapers = wallpapers != null ? new ArrayList<>(wallpapers) : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_wallpaper, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Wallpaper wallpaper = wallpapers.get(position);

        int cornerPx = dpToPx(10);
        RequestOptions reqOptions = new RequestOptions()
                .centerCrop()
                .transform(new RoundedCorners(cornerPx))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC) // Replaced ALL to handle high definition sampling seamlessly
                .placeholder(R.drawable.bg)
                .error(R.drawable.error_image);

        String urlToLoad = (wallpaper.getThumbnailUrl() != null && !wallpaper.getThumbnailUrl().isEmpty())
                ? wallpaper.getThumbnailUrl()
                : wallpaper.getImageUrl();

        Glide.with(context)
                .load(urlToLoad)
                .apply(reqOptions)
                .transition(DrawableTransitionOptions.withCrossFade(250))
                .into(holder.wallpaperImage);

        if (wallpaper.isPremium()) {
            holder.premiumBadge.setVisibility(View.VISIBLE);
        } else {
            holder.premiumBadge.setVisibility(View.GONE);
        }

        holder.infoOverlay.setVisibility(View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onWallpaperClick(wallpaper);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onWallpaperLongClick(wallpaper, position);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return wallpapers.size();
    }

    public void updateData(List<Wallpaper> newWallpapers) {
        if (newWallpapers == null) {
            newWallpapers = new ArrayList<>();
        }

        final List<Wallpaper> finalNewWallpapers = new ArrayList<>(newWallpapers);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return wallpapers.size();
            }

            @Override
            public int getNewListSize() {
                return finalNewWallpapers.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return wallpapers.get(oldItemPosition).getId().equals(finalNewWallpapers.get(newItemPosition).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return wallpapers.get(oldItemPosition).equals(finalNewWallpapers.get(newItemPosition));
            }
        });

        wallpapers.clear();
        wallpapers.addAll(finalNewWallpapers);
        diffResult.dispatchUpdatesTo(this);
    }

    public void addData(List<Wallpaper> newWallpapers) {
        if (newWallpapers == null || newWallpapers.isEmpty()) return;
        int startPosition = wallpapers.size();
        wallpapers.addAll(newWallpapers);
        notifyItemRangeInserted(startPosition, newWallpapers.size());
    }

    public void clear() {
        int size = wallpapers.size();
        wallpapers.clear();
        notifyItemRangeRemoved(0, size);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView wallpaperImage;
        ImageView premiumBadge;
        LinearLayout infoOverlay;
        TextView wallpaperTitle;
        TextView wallpaperAuthor;

        ViewHolder(View itemView) {
            super(itemView);
            wallpaperImage = itemView.findViewById(R.id.wallpaper_image);
            premiumBadge = itemView.findViewById(R.id.premium_badge);
            infoOverlay = itemView.findViewById(R.id.info_overlay);
            wallpaperTitle = itemView.findViewById(R.id.wallpaper_title);
            wallpaperAuthor = itemView.findViewById(R.id.wallpaper_author);
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

// test
