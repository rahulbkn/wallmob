package com.wall.mob;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class WallpaperChangerScheduler {

    private static final String TAG = "ChangerScheduler";
    private static final String WORK_NAME = "wallpaper_changer";
    private static final String PREFS_NAME = "wallpaper_changer";
    private static final String KEY_INTERVAL = "changer_interval_hours";

    public static void schedule(Context context) {
        int intervalHours = getIntervalHours(context);
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                WallpaperChangerWorker.class,
                intervalHours,
                TimeUnit.HOURS)
                .addTag(WORK_NAME)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    public static boolean isScheduled(Context context) {
        try {
            return WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WORK_NAME)
                    .get()
                    .stream()
                    .anyMatch(info -> info.getState() == androidx.work.WorkInfo.State.ENQUEUED
                            || info.getState() == androidx.work.WorkInfo.State.RUNNING);
        } catch (Exception e) {
            return false;
        }
    }

    public static int getIntervalHours(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_INTERVAL, 24);
    }

    public static void setIntervalHours(Context context, int hours) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_INTERVAL, hours).apply();
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean("changer_enabled", enabled).apply();
        if (enabled) schedule(context);
        else cancel(context);
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean("changer_enabled", false);
    }
}
