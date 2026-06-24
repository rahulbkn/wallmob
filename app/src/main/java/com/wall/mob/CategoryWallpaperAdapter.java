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

public class CategoryWallpaperAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_WALLPAPER = 1;
    private static final int VIEW_TYPE_FOOTER = 2;

    private Context context;
    private List<Wallpaper> wallpapers = new ArrayList<>();
    private WallpaperAdapter.OnWallpaperClickListener listener;

    private String headerText = "";
    private boolean showFooter = false;

    public CategoryWallpaperAdapter(Context context, List<Wallpaper> wallpapers,
                                    WallpaperAdapter.OnWallpaperClickListener listener) {
        this.context = context;
        this.wallpapers = wallpapers != null ? new ArrayList<>(wallpapers) : new ArrayList<>();
        this.listener = listener;
    }

    // Called by Activity's SpanSizeLookup
    public boolean isHeader(int position) {
        return position == 0;
    }

    public void setHeaderText(String text) {
        this.headerText = text != null ? text : "";
        notifyItemChanged(0);
    }

    public void showFooterLoading(boolean show) {
        if (this.showFooter == show) return;
        this.showFooter = show;
        if (show) {
            notifyItemInserted(getItemCount() - 1);
        } else {
            notifyItemRemoved(getItemCount()); // was last item before removal
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return VIEW_TYPE_HEADER;
        if (showFooter && position == getItemCount() - 1) return VIEW_TYPE_FOOTER;
        return VIEW_TYPE_WALLPAPER;
    }

    @Override
    public int getItemCount() {
        return 1 + wallpapers.size() + (showFooter ? 1 : 0);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_category_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == VIEW_TYPE_FOOTER) {
            View view = inflater.inflate(R.layout.item_footer_loading, parent, false);
            return new FooterViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_category_wallpaper, parent, false);
            return new WallpaperViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(headerText, wallpapers.size());
        } else if (holder instanceof WallpaperViewHolder) {
            int dataIndex = position - 1; // offset for header
            Wallpaper wallpaper = wallpapers.get(dataIndex);
            ((WallpaperViewHolder) holder).bind(wallpaper, dataIndex);
        }
        // FooterViewHolder needs no binding — just a spinner
    }

    public void updateData(List<Wallpaper> newWallpapers) {
        if (newWallpapers == null) newWallpapers = new ArrayList<>();

        final List<Wallpaper> finalNew = new ArrayList<>(newWallpapers);
        final List<Wallpaper> finalOld = new ArrayList<>(wallpapers);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return finalOld.size(); }
            @Override public int getNewListSize() { return finalNew.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return finalOld.get(oldPos).getId().equals(finalNew.get(newPos).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return finalOld.get(oldPos).equals(finalNew.get(newPos));
            }
        });

        wallpapers.clear();
        wallpapers.addAll(finalNew);
        // +1 offset because DiffUtil doesn't know about header — notify manually
        // Easiest: just notify full range after header
        notifyItemRangeChanged(1, wallpapers.size());
        // Update header count too
        notifyItemChanged(0);
    }

    public void addData(List<Wallpaper> newWallpapers) {
        if (newWallpapers == null || newWallpapers.isEmpty()) return;
        int startPosition = wallpapers.size(); // before adding
        wallpapers.addAll(newWallpapers);
        notifyItemRangeInserted(startPosition + 1, newWallpapers.size()); // +1 for header
        notifyItemChanged(0); // update count in header
    }

    public void clear() {
        int size = wallpapers.size();
        wallpapers.clear();
        notifyItemRangeRemoved(1, size); // +1 for header
    }

    // ---- ViewHolders ----

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView categoryNameText;
        TextView wallpaperCountText;

        HeaderViewHolder(View itemView) {
            super(itemView);
            categoryNameText = itemView.findViewById(R.id.category_name);
            wallpaperCountText = itemView.findViewById(R.id.wallpaper_count);
        }

        void bind(String name, int count) {
            if (categoryNameText != null) categoryNameText.setText(name);
            if (wallpaperCountText != null) {
                wallpaperCountText.setText(itemView.getContext().getResources().getQuantityString(R.plurals.wallpaper_count, count, count));
            }
        }
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        FooterViewHolder(View itemView) { super(itemView); }
    }

    class WallpaperViewHolder extends RecyclerView.ViewHolder {
        ImageView wallpaperImage;
        ImageView premiumBadge;
        LinearLayout infoOverlay;
        TextView wallpaperTitle;
        TextView wallpaperAuthor;

        WallpaperViewHolder(View itemView) {
            super(itemView);
            wallpaperImage = itemView.findViewById(R.id.wallpaper_image);
            premiumBadge = itemView.findViewById(R.id.premium_badge);
            infoOverlay = itemView.findViewById(R.id.info_overlay);
            wallpaperTitle = itemView.findViewById(R.id.wallpaper_title);
            wallpaperAuthor = itemView.findViewById(R.id.wallpaper_author);
        }

        void bind(Wallpaper wallpaper, int dataIndex) {
            int cornerPx = dpToPx(10);
            int targetW = wallpaperImage.getWidth();
            int targetH = wallpaperImage.getHeight();
            if (targetW <= 0 || targetH <= 0) {
                targetW = dpToPx(152);
                targetH = dpToPx(200);
            }
            RequestOptions reqOptions = new RequestOptions()
                    .centerCrop()
                    .override(targetW, targetH)
                    .transform(new RoundedCorners(cornerPx))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.bg)
                    .error(R.drawable.error_image);

            String urlToLoad = (wallpaper.getThumbnailUrl() != null && !wallpaper.getThumbnailUrl().isEmpty())
                    ? wallpaper.getThumbnailUrl()
                    : wallpaper.getImageUrl();

            Glide.with(context)
                    .load(urlToLoad)
                    .apply(reqOptions)
                    .transition(DrawableTransitionOptions.withCrossFade(250))
                    .into(wallpaperImage);

            premiumBadge.setVisibility(wallpaper.isPremium() ? View.VISIBLE : View.GONE);
            infoOverlay.setVisibility(View.GONE);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onWallpaperClick(wallpaper);
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onWallpaperLongClick(wallpaper, dataIndex);
                return true;
            });
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
