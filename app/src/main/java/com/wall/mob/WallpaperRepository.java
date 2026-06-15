package com.wall.mob;

public class WallpaperRepository {

    private static WallpaperRepository instance;

    public static WallpaperRepository getInstance() {
        if (instance == null) {
            instance = new WallpaperRepository();
        }
        return instance;
    }

    public Wallpaper getWallpaperById(String id) {
        // TODO:
        // 1. API call
        // 2. Firebase
        // 3. Local cache
        // 4. Pexels API

        return null; // return actual Wallpaper object
    }
}