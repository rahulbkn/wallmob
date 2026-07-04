package com.wall.mob;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RecentWallpapersManager {

    private static final String PREF_NAME = "recent_wallpapers";
    private static final String KEY_RECENT = "recent";
    private static final int MAX_RECENT = 20;

    public static void addRecent(Context context, Wallpaper wallpaper) {
        List<Wallpaper> recent = getRecents(context);
        recent.removeIf(w -> w.getId().equals(wallpaper.getId()));
        recent.add(0, wallpaper);
        if (recent.size() > MAX_RECENT) {
            recent = recent.subList(0, MAX_RECENT);
        }
        saveRecents(context, recent);
    }

    public static List<Wallpaper> getRecents(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_RECENT, null);
        if (json == null) return new ArrayList<>();
        Gson gson = new Gson();
        Type type = new TypeToken<List<Wallpaper>>() {}.getType();
        List<Wallpaper> recents = gson.fromJson(json, type);
        return recents != null ? recents : new ArrayList<>();
    }

    public static void clearRecents(Context context) {
        saveRecents(context, new ArrayList<>());
    }

    private static void saveRecents(Context context, List<Wallpaper> recents) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        prefs.edit().putString(KEY_RECENT, gson.toJson(recents)).apply();
    }
}
