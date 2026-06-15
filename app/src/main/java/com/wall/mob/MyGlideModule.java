package com.wall.mob;

import android.content.Context;
import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;

@GlideModule
public class MyGlideModule extends AppGlideModule {
    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        // Increase memory cache size (default is 24MB for high-end devices)
        int memoryCacheSizeBytes = 1024 * 1024 * 40; // 40MB
        builder.setMemoryCache(new LruResourceCache(memoryCacheSizeBytes));

        // Increase disk cache size (default is 250MB)
        int diskCacheSizeBytes = 1024 * 1024 * 500; // 500MB
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, diskCacheSizeBytes));
    }

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        // You can register custom components here if needed
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}