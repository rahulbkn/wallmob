package com.wall.mob;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {
    
    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "wallmob_notifications";
    private static final String CHANNEL_NAME = "WallMob Notifications";
    private static final String CHANNEL_DESCRIPTION = "Notifications for WallMob app";
    private static final int NOTIFICATION_ID_BASE = 1000;
    
    /**
     * Creates the notification channel for Android O and above
     * This should be called when the app starts
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager notificationManager = 
                    context.getSystemService(NotificationManager.class);
                
                if (notificationManager == null) {
                    Log.e(TAG, "NotificationManager is null");
                    return;
                }
                
                // Check if channel already exists
                NotificationChannel existingChannel = 
                    notificationManager.getNotificationChannel(CHANNEL_ID);
                
                if (existingChannel != null) {
                    Log.d(TAG, "Notification channel already exists: " + CHANNEL_ID);
                    return;
                }
                
                // Create channel
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH // Changed to HIGH for better visibility
                );
                
                channel.setDescription(CHANNEL_DESCRIPTION);
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 250, 250, 250});
                channel.enableLights(true);
                channel.setShowBadge(true);
                channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                
                notificationManager.createNotificationChannel(channel);
                
                Log.d(TAG, "✓ Notification channel created successfully: " + CHANNEL_ID);
                Log.d(TAG, "  Name: " + CHANNEL_NAME);
                Log.d(TAG, "  Importance: HIGH");
                
            } catch (Exception e) {
                Log.e(TAG, "✗ Error creating notification channel", e);
            }
        } else {
            Log.d(TAG, "Android version < O, no channel needed");
        }
    }
    
    /**
     * Shows a local notification
     */
    public static void showLocalNotification(Context context, String title, String message) {
        showLocalNotification(context, title, message, "public_message", "");
    }
    
    /**
     * Shows a local notification with type and extra data
     */
    public static void showLocalNotification(Context context, String title, String message, 
                                            String type, String extraData) {
        try {
            Log.d(TAG, "Showing local notification: " + title);
            
            // Ensure channel exists
            createNotificationChannel(context);
            
            // Store notification in history
            storeNotificationInHistory(context, title, message, type, extraData);
            
            // Create intent for when notification is tapped
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("notification_type", type);
            intent.putExtra("from_notification", true);
            
            if (extraData != null && !extraData.isEmpty()) {
                intent.putExtra("extra_data", extraData);
            }
            
            // Create pending intent
            int requestCode = (int) System.currentTimeMillis();
            PendingIntent pendingIntent;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getActivity(
                    context, 
                    requestCode, 
                    intent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
            } else {
                pendingIntent = PendingIntent.getActivity(
                    context, 
                    requestCode, 
                    intent, 
                    PendingIntent.FLAG_UPDATE_CURRENT
                );
            }
            
            // Build notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 250, 250, 250})
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            
            // Show notification
            NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (notificationManager != null) {
                int notificationId = NOTIFICATION_ID_BASE + (int) (System.currentTimeMillis() % 1000);
                notificationManager.notify(notificationId, builder.build());
                Log.d(TAG, "✓ Local notification shown with ID: " + notificationId);
            } else {
                Log.e(TAG, "✗ NotificationManager is null");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "✗ Error showing local notification", e);
            e.printStackTrace();
        }
    }
    
    /**
     * Stores notification in SharedPreferences history
     * Made public so FCM service can access it
     */
    public static void storeNotificationInHistory(Context context, String title, 
                                                  String message, String type, String extraData) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE);
            long timestamp = System.currentTimeMillis();
            String key = "notification_" + timestamp;
            
            // Format: title|message|timestamp|type|extraData
            String value = title + "|" + 
                          message + "|" + 
                          timestamp + "|" + 
                          type + "|" + 
                          (extraData != null ? extraData : "");
            
            prefs.edit().putString(key, value).apply();
            
            Log.d(TAG, "✓ Notification stored in history: " + key);
            
            // Keep only last 100 notifications
            cleanupOldNotifications(prefs);
            
        } catch (Exception e) {
            Log.e(TAG, "✗ Error storing notification in history", e);
        }
    }
    
    /**
     * Removes old notifications keeping only the latest 100
     */
    private static void cleanupOldNotifications(SharedPreferences prefs) {
        try {
            java.util.Map<String, ?> allNotifications = prefs.getAll();
            
            if (allNotifications.size() > 100) {
                java.util.List<String> keys = new java.util.ArrayList<>();
                
                for (String key : allNotifications.keySet()) {
                    if (key.startsWith("notification_")) {
                        keys.add(key);
                    }
                }
                
                // Sort by timestamp (extract from key)
                java.util.Collections.sort(keys, (a, b) -> {
                    try {
                        long timeA = Long.parseLong(a.substring(a.lastIndexOf("_") + 1));
                        long timeB = Long.parseLong(b.substring(b.lastIndexOf("_") + 1));
                        return Long.compare(timeA, timeB);
                    } catch (Exception e) {
                        return 0;
                    }
                });
                
                // Remove oldest notifications
                SharedPreferences.Editor editor = prefs.edit();
                int toRemove = keys.size() - 100;
                for (int i = 0; i < toRemove; i++) {
                    editor.remove(keys.get(i));
                }
                editor.apply();
                
                Log.d(TAG, "✓ Cleaned up " + toRemove + " old notifications");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "✗ Error cleaning up notifications", e);
        }
    }
    
    /**
     * Gets all notifications from history
     */
    public static java.util.List<String> getAllNotifications(Context context) {
        java.util.List<String> notifications = new java.util.ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE);
            java.util.Map<String, ?> allNotifications = prefs.getAll();
            
            java.util.List<String> keys = new java.util.ArrayList<>();
            for (String key : allNotifications.keySet()) {
                if (key.startsWith("notification_")) {
                    keys.add(key);
                }
            }
            
            // Sort by timestamp (newest first)
            java.util.Collections.sort(keys, (a, b) -> {
                try {
                    long timeA = Long.parseLong(a.substring(a.lastIndexOf("_") + 1));
                    long timeB = Long.parseLong(b.substring(b.lastIndexOf("_") + 1));
                    return Long.compare(timeB, timeA); // Reverse order
                } catch (Exception e) {
                    return 0;
                }
            });
            
            for (String key : keys) {
                String value = prefs.getString(key, "");
                if (!value.isEmpty()) {
                    notifications.add(value);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting notifications", e);
        }
        return notifications;
    }
    
    /**
     * Clears all notifications from history
     */
    public static void clearAllNotifications(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            java.util.Map<String, ?> allNotifications = prefs.getAll();
            for (String key : allNotifications.keySet()) {
                if (key.startsWith("notification_")) {
                    editor.remove(key);
                }
            }
            editor.apply();
            
            Log.d(TAG, "✓ All notifications cleared");
        } catch (Exception e) {
            Log.e(TAG, "✗ Error clearing notifications", e);
        }
    }
}
// test
