package com.wall.mob;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ColorToneAdapter extends RecyclerView.Adapter<ColorToneAdapter.ViewHolder> {

    private Context context;
    private List<String> colors;
    private OnColorClickListener listener;

    public interface OnColorClickListener {
        void onColorClick(String color);
    }

    public ColorToneAdapter(Context context, List<String> colors, OnColorClickListener listener) {
        this.context = context;
        this.colors = colors != null ? colors : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_color_tone, parent, false);
        return new ViewHolder(view);
    }
    
@Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String color = colors.get(position);
        
        try {
            holder.colorView.setBackgroundColor(Color.parseColor(color));
        } catch (IllegalArgumentException e) {
            holder.colorView.setBackgroundColor(Color.GRAY);
        }
        
        // 1. Create a reusable click listener
        View.OnClickListener clickListener = v -> {
            if (listener != null) {
                listener.onColorClick(color);
            }
        };

        // 2. Attach it to BOTH the root item and the specific color view
        holder.itemView.setOnClickListener(clickListener);
        holder.colorView.setOnClickListener(clickListener);
    }

    @Override
    public int getItemCount() {
        return colors.size();
    }

    public void updateData(List<String> newColors) {
        this.colors = newColors != null ? newColors : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View colorView;

        ViewHolder(View itemView) {
            super(itemView);
            colorView = itemView.findViewById(R.id.color_view);
        }
    }
}