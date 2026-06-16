package com.wall.mob;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

public class CategoryGridAdapter extends RecyclerView.Adapter<CategoryGridAdapter.ViewHolder> {

    private Context context;
    private List<CategoryItem> categories;
    private OnCategoryClickListener listener;

    public interface OnCategoryClickListener {
        void onCategoryClick(CategoryItem category);
    }

    public CategoryGridAdapter(Context context, List<CategoryItem> categories, OnCategoryClickListener listener) {
        this.context = context;
        this.categories = categories != null ? categories : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryItem category = categories.get(position);

        holder.categoryName.setText(category.getName());
        // Use theme attribute for text color so it adapts to day/night
        int textColor;
        TypedValue tv = new TypedValue();
        boolean resolved = context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true);
        if (resolved) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                textColor = tv.data;
            } else {
                // attribute references a color resource
                textColor = ContextCompat.getColor(context, tv.resourceId);
            }
        } else {
            // fallback to app-defined color or Android default
            try {
                textColor = ContextCompat.getColor(context, R.color.black);
            } catch (Exception e) {
                textColor = 0xFF000000; // black
            }
        }
        holder.categoryName.setTextColor(textColor);

        // Glide options
        RequestOptions options = new RequestOptions()
                .centerCrop()
                .placeholder(R.drawable.bg)
                .error(R.drawable.error_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL);

        Glide.with(context)
                .load(category.getImageUrl())
                .apply(options)
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(holder.categoryImage);

        holder.container.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public void updateData(List<CategoryItem> newCategories) {
        this.categories = newCategories != null ? newCategories : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView categoryImage;
        TextView categoryName;
        CardView container;

        ViewHolder(View itemView) {
            super(itemView);
            container = (CardView) itemView;
            categoryImage = itemView.findViewById(R.id.category_image);
            categoryName = itemView.findViewById(R.id.category_name);
        }
    }
}

// test
