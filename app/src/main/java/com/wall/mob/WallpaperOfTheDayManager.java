package com.wall.mob;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WallpaperOfTheDayManager {

    private static final String PREF_NAME = "wotd_prefs";
    private static final String KEY_WALLPAPER = "wotd_wallpaper";
    private static final String KEY_DATE = "wotd_date";

    public static Wallpaper getDailyWallpaper(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String today = getTodayDate();
        String savedDate = prefs.getString(KEY_DATE, "");

        if (today.equals(savedDate)) {
            String json = prefs.getString(KEY_WALLPAPER, null);
            if (json != null) {
                try {
                    return new Gson().fromJson(json, Wallpaper.class);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public static void setDailyWallpaper(Context context, Wallpaper wallpaper) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_WALLPAPER, new Gson().toJson(wallpaper))
                .putString(KEY_DATE, getTodayDate())
                .apply();
    }

    public static boolean isExpired(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedDate = prefs.getString(KEY_DATE, "");
        return !getTodayDate().equals(savedDate);
    }

    private static String getTodayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
