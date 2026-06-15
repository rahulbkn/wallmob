package com.wall.mob;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class FavoriteManager {

    private static final String PREF_NAME = "favorites_prefs";
    private static final String KEY_FAVORITES = "favorites";

    public static void addToFavorites(Context context, Wallpaper wallpaper) {
        List<Wallpaper> favorites = getFavorites(context);
        
        // Check if already in favorites
        for (Wallpaper fav : favorites) {
            if (fav.getId().equals(wallpaper.getId())) {
                return; // Already in favorites
            }
        }
        
        favorites.add(wallpaper);
        saveFavorites(context, favorites);
    }

    public static void removeFromFavorites(Context context, Wallpaper wallpaper) {
        List<Wallpaper> favorites = getFavorites(context);
        
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).getId().equals(wallpaper.getId())) {
                favorites.remove(i);
                saveFavorites(context, favorites);
                break;
            }
        }
    }

    public static boolean isFavorite(Context context, Wallpaper wallpaper) {
        List<Wallpaper> favorites = getFavorites(context);
        
        for (Wallpaper fav : favorites) {
            if (fav.getId().equals(wallpaper.getId())) {
                return true;
            }
        }
        return false;
    }

    public static List<Wallpaper> getFavorites(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String favoritesJson = prefs.getString(KEY_FAVORITES, null);
        
        if (favoritesJson == null) {
            return new ArrayList<>();
        }
        
        Gson gson = new Gson();
        Type type = new TypeToken<List<Wallpaper>>() {}.getType();
        List<Wallpaper> favorites = gson.fromJson(favoritesJson, type);
        
        return favorites != null ? favorites : new ArrayList<>();
    }

    public static String getFavoritesJson(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_FAVORITES, null);
    }

    // Change from private to public
    public static void saveFavorites(Context context, List<Wallpaper> favorites) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String favoritesJson = gson.toJson(favorites);
        prefs.edit().putString(KEY_FAVORITES, favoritesJson).apply();
    }

    // Add this method to get favorite IDs as Set
    public static Set<String> getFavoriteIds(Context context) {
        List<Wallpaper> favorites = getFavorites(context);
        Set<String> ids = new HashSet<>();
        for (Wallpaper wallpaper : favorites) {
            ids.add(wallpaper.getId());
        }
        return ids;
    }

    // Add this method to clear all favorites
    public static void clearAllFavorites(Context context) {
        saveFavorites(context, new ArrayList<>());
    }
}