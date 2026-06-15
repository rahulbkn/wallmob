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
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

public class LandscapeAdapter extends RecyclerView.Adapter<LandscapeAdapter.ViewHolder> {

    private Context context;
    private List<Wallpaper> wallpapers;
    private BestMonthAdapter.OnItemClickListener listener;

    public LandscapeAdapter(Context context, List<Wallpaper> wallpapers, BestMonthAdapter.OnItemClickListener listener) {
        this.context = context;
        this.wallpapers = wallpapers != null ? wallpapers : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_landscape, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Wallpaper wallpaper = wallpapers.get(position);
        
        holder.title.setText(wallpaper.getTitle());

        String urlToLoad = wallpaper.getThumbnailUrl();
        if (urlToLoad == null || urlToLoad.isEmpty()) {
            urlToLoad = wallpaper.getImageUrl();
        }
        
        Glide.with(context)
                .load(urlToLoad)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.bg)
                .error(R.drawable.error_image)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(holder.image);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(wallpaper);
            }
        });
    }

    @Override
    public int getItemCount() {
        return wallpapers.size();
    }

    public void updateData(List<Wallpaper> newWallpapers) {
        this.wallpapers.clear();
        if (newWallpapers != null) {
            this.wallpapers.addAll(newWallpapers);
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title;

        ViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.wallpaper_image);
            title = itemView.findViewById(R.id.wallpaper_title);
        }
    }
}
