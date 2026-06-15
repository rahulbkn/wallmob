
package com.wall.mob;

public class NotificationItem {
    public String title;
    public String message;
    public long timestamp;
    public String type;
    public String extraData;
    
    public NotificationItem(String title, String message, long timestamp, String type, String extraData) {
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.extraData = extraData;
    }
    
    public String getFormattedTime() {
        long diff = System.currentTimeMillis() - timestamp;
        
        if (diff < 60000) { // Less than 1 minute
            return "Just now";
        } else if (diff < 3600000) { // Less than 1 hour
            int minutes = (int) (diff / 60000);
            return minutes + " min ago";
        } else if (diff < 86400000) { // Less than 24 hours
            int hours = (int) (diff / 3600000);
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, HH:mm");
            return sdf.format(new java.util.Date(timestamp));
        }
    }
    
    public String getTypeLabel() {
        switch (type) {
            case "new_wallpaper":
                return "New Wallpaper";
            case "special_offer":
                return "Special Offer";
            case "favorites_update":
                return "Favorites";
            case "public_message":
                return "Message";
            default:
                return "Notification";
        }
    }
}
