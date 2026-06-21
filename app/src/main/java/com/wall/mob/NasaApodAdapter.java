package com.wall.mob;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

public class NasaApodAdapter extends RecyclerView.Adapter<NasaApodAdapter.ViewHolder> {

    public interface OnWallpaperClickListener {
        void onWallpaperClick(Wallpaper wallpaper);
    }

    private final Context context;
    private List<Wallpaper> items;
    private final OnWallpaperClickListener listener;

    public NasaApodAdapter(Context context, List<Wallpaper> items, OnWallpaperClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    public void updateData(List<Wallpaper> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // CHANGED: R.layout.item_wallpaper_card to R.layout.item_wallpaper
        View view = LayoutInflater.from(context).inflate(R.layout.item_wallpaper, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Wallpaper wallpaper = items.get(position);

        Glide.with(context)
                .load(wallpaper.getThumbnailUrl() != null ? wallpaper.getThumbnailUrl() : wallpaper.getImageUrl())
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        // CHANGED: Use a drawable that actually exists in your project, or remove the placeholder
                        .error(R.drawable.error_image)) 
                .thumbnail(
                        Glide.with(context)
                                .load(wallpaper.getThumbnailUrl())
                                .apply(new RequestOptions()
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .centerCrop())
                )
                .into(holder.imageView);
        
       


        if (holder.titleView != null) {
            holder.titleView.setText(wallpaper.getTitle());
            holder.titleView.setVisibility(View.VISIBLE);
        }

        if (holder.authorView != null) {
            String credit = wallpaper.getPhotographer() != null
                    ? "© " + wallpaper.getPhotographer()
                    : context.getString(R.string.nasa_credit);
            holder.authorView.setText(credit);
            holder.authorView.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onWallpaperClick(wallpaper);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;
        final TextView titleView;
        final TextView authorView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.wallpaper_image);
            titleView = itemView.findViewById(R.id.wallpaper_title);
            authorView = itemView.findViewById(R.id.wallpaper_author);
        }
    }
}