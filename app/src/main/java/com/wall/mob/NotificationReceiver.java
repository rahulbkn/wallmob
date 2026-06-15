package com.wall.mob;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "NotificationReceiver triggered");
        
        // Extract notification data
        Bundle extras = intent.getExtras();
        if (extras != null) {
            String title = extras.getString("title");
            String message = extras.getString("message");
            String type = extras.getString("notification_type");
            String extraData = extras.getString("extra_data");
            
            // Check for standard FCM fields
            if (title == null) title = extras.getString("gcm.notification.title");
            if (message == null) message = extras.getString("gcm.notification.body");
            
            // Use defaults if null
            if (title == null) title = "WallMob Notification";
            if (message == null) message = "New notification";
            if (type == null) type = "public_message";
            if (extraData == null) extraData = "";
            
            // Store notification in history
            NotificationHelper.storeNotificationInHistory(context, title, message, type, extraData);
            
            Log.d(TAG, "✓ Notification stored from background:");
            Log.d(TAG, "  Title: " + title);
            Log.d(TAG, "  Message: " + message);
        }
    }
}