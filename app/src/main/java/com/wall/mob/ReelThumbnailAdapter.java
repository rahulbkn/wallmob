package com.wall.mob;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.wall.mob.reels.ReelVideo;

import java.util.ArrayList;
import java.util.List;

public class ReelThumbnailAdapter extends RecyclerView.Adapter<ReelThumbnailAdapter.ViewHolder> {

    private final Context context;
    private List<ReelVideo> reels;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ReelVideo reel);
    }

    public ReelThumbnailAdapter(Context context, List<ReelVideo> reels, OnItemClickListener listener) {
        this.context = context;
        this.reels = reels != null ? reels : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_reel_thumbnail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReelVideo reel = reels.get(position);

        Glide.with(context)
                .load(reel.getThumbnailUrl())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.bg)
                .error(R.drawable.error_image)
                .centerCrop()
                .into(holder.image);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(reel);
        });
    }

    @Override
    public int getItemCount() {
        return reels.size();
    }

    public void updateData(List<ReelVideo> newReels) {
        this.reels.clear();
        if (newReels != null) this.reels.addAll(newReels);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;

        ViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.reel_thumbnail_image);
        }
    }
}
