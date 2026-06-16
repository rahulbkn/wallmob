package com.wall.mob;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<NotificationItem> notifications;

    public NotificationAdapter(List<NotificationItem> notifications) {
        this.notifications = notifications;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem item = notifications.get(position);
        
        holder.titleText.setText(item.title);
        holder.messageText.setText(item.message);
        holder.timeText.setText(item.getFormattedTime());
        holder.typeText.setText(item.getTypeLabel());
        
        // Set type indicator color
        int colorResId;
        switch (item.type) {
            case "new_wallpaper":
                colorResId = R.color.notification_wallpaper;
                break;
            case "special_offer":
                colorResId = R.color.notification_offer;
                break;
            case "favorites_update":
                colorResId = R.color.notification_favorites;
                break;
            default:
                colorResId = R.color.notification_default;
                break;
        }
        
        try {
            holder.typeIndicator.setBackgroundColor(
                holder.itemView.getContext().getColor(colorResId)
            );
        } catch (Exception e) {
            // Fallback color if resource not found
            holder.typeIndicator.setBackgroundColor(0xFF2196F3);
        }
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleText, messageText, timeText, typeText;
        View typeIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.notification_title);
            messageText = itemView.findViewById(R.id.notification_message);
            timeText = itemView.findViewById(R.id.notification_time);
            typeText = itemView.findViewById(R.id.notification_type);
            typeIndicator = itemView.findViewById(R.id.type_indicator);
        }
    }
}

// test
