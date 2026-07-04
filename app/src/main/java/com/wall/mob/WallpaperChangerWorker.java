package com.wall.mob;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class WallpaperChangerWorker extends Worker {

    private static final String TAG = "WallpaperChanger";
    private static final String PREFS_NAME = "wallpaper_changer";
    private static final String KEY_TARGET = "target_screen";
    private static final String KEY_ENABLED = "changer_enabled";
    private static final String CHANNEL_ID = "wallpaper_changer";
    private static final int NOTIFICATION_ID = 2001;

    public WallpaperChangerWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Wallpaper changer running...");
        Context context = getApplicationContext();

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ENABLED, true)) {
            Log.d(TAG, "Wallpaper changer disabled");
            return Result.success();
        }

        Wallpaper wallpaper = pickRandomWallpaper(context);
        if (wallpaper == null) {
            Log.w(TAG, "No wallpapers available");
            return Result.retry();
        }

        try {
            Bitmap bitmap = Glide.with(context)
                    .asBitmap()
                    .load(wallpaper.getImageUrl())
                    .submit(1080, 1920)
                    .get();

            if (bitmap != null) {
                int targetFlag = prefs.getInt(KEY_TARGET, WallpaperManager.FLAG_SYSTEM);
                WallpaperManager wm = WallpaperManager.getInstance(context);
                try {
                    wm.setBitmap(bitmap, null, true, targetFlag);
                    Log.d(TAG, "Wallpaper set: " + wallpaper.getTitle());
                    showNotification(context, wallpaper);
                    return Result.success();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to set wallpaper", e);
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Failed to load/set wallpaper", e);
        }

        return Result.retry();
    }

    private Wallpaper pickRandomWallpaper(Context context) {
        List<Wallpaper> favorites = FavoriteManager.getFavorites(context);
        if (!favorites.isEmpty()) {
            int index = (int) (System.currentTimeMillis() % favorites.size());
            return favorites.get(index);
        }
        Wallpaper wotd = WallpaperOfTheDayManager.getDailyWallpaper(context);
        if (wotd != null) return wotd;
        return null;
    }

    private void showNotification(Context context, Wallpaper wallpaper) {
        createChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("Wallpaper Changed")
                .setContentText(wallpaper.getTitle() != null ? wallpaper.getTitle() : "New wallpaper applied")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, builder.build());
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Wallpaper Changer",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notifications from auto wallpaper changer");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
