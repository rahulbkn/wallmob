package com.wall.mob;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import com.android.volley.RequestQueue;
import com.android.volley.Request;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ApiManager {
    private static final String TAG = "ApiManager";

    private RequestQueue requestQueue;
    private ApiCallback callback;
    private Context context;
    private Random random;
    private DeviceUtils deviceUtils;

    private static final String UNIFIED_API_URL = "https://api-server.rahulkumarbknv.workers.dev/";
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int CACHE_MAX_AGE_SEC = 600;

    private String currentQuery;
    private int currentPage = 1;
    private boolean isLoadingMore = false;
    private String currentSourceFilter = "all";

    private static final String[] SEARCH_QUERIES = {
            "nature", "abstract", "landscape", "animals", "city",
            "space", "beach", "minimal", "dark", "colorful",
            "mountain", "ocean", "forest", "sunset", "flowers",
            "architecture", "galaxy", "neon", "aesthetic", "vintage"
    };

    private LruCache<String, CachedResponse> responseCache;
    private static final int CACHE_SIZE = 50;

    private static class CachedResponse {
        final List<Wallpaper> wallpapers;
        final long timestamp;

        CachedResponse(List<Wallpaper> wallpapers) {
            this.wallpapers = wallpapers;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_MAX_AGE_SEC * 1000L;
        }
    }

    public interface ApiCallback {
        void onWallpapersLoaded(List<Wallpaper> wallpapers);
        void onError(String message);
        void onMoreWallpapersLoaded(List<Wallpaper> newWallpapers);
    }

    public ApiManager(Context context) {
        this.context = context.getApplicationContext();
        this.requestQueue = Volley.newRequestQueue(this.context);
        this.random = new Random();
        this.deviceUtils = new DeviceUtils(context);
        this.responseCache = new LruCache<>(CACHE_SIZE);
    }

    public void loadWallpapersFromAllSources(ApiCallback callback) {
        this.callback = callback;
        resetPagination();
        currentQuery = SEARCH_QUERIES[random.nextInt(SEARCH_QUERIES.length)];
        loadFromUnifiedAPI(currentQuery, 1, DEFAULT_PAGE_SIZE, false);
    }

    public void loadWallpapersByQuery(String query, ApiCallback callback) {
        loadWallpapersByQuery(query, DEFAULT_PAGE_SIZE, 1, callback);
    }

    public void loadWallpapersByQuery(String query, int pageSize, int page, ApiCallback callback) {
        this.callback = callback;
        if (page == 1) resetPagination();
        currentQuery = query;
        currentPage = page;
        loadFromUnifiedAPI(query, page, pageSize, page > 1);
    }

    public void loadWallpapersBySource(String source, ApiCallback callback) {
        this.callback = callback;
        resetPagination();
        currentSourceFilter = source.toLowerCase();

        if (currentQuery == null || currentQuery.isEmpty()) {
            currentQuery = SEARCH_QUERIES[random.nextInt(SEARCH_QUERIES.length)];
        }
        loadFromUnifiedAPI(currentQuery, 1, DEFAULT_PAGE_SIZE, false);
    }

    public void setSourceFilter(String source) {
        this.currentSourceFilter = source != null ? source.toLowerCase() : "all";
    }

    public void loadNextPage() {
        if (isLoadingMore || currentQuery == null || currentQuery.isEmpty()) return;
        isLoadingMore = true;
        currentPage++;
        loadFromUnifiedAPI(currentQuery, currentPage, DEFAULT_PAGE_SIZE, true);
    }

    private void resetPagination() {
        currentPage = 1;
        isLoadingMore = false;
        currentSourceFilter = "all";
    }

    private void loadFromUnifiedAPI(String query, int page, int perPage, boolean isPagination) {
        String cacheKey = query + "|" + page + "|" + perPage + "|" + currentSourceFilter;

        if (!isPagination) {
            CachedResponse cached = responseCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                Log.d(TAG, "Using cached response for: " + cacheKey);
                if (callback != null) callback.onWallpapersLoaded(new ArrayList<>(cached.wallpapers));
                return;
            }
        }

        String url = UNIFIED_API_URL
                + "?query=" + query.replace(" ", "+")
                + "&page=" + page
                + "&per_page=" + perPage
                + "&source=" + currentSourceFilter;

        Log.d(TAG, "Fetching URL: " + url);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONObject meta = response.optJSONObject("meta");
                        if (meta != null && !meta.optBoolean("success", false)) {
                            String errMsg = response.optString("error", context.getString(R.string.unknown_error));
                            handleError(context.getString(R.string.api_error_format, errMsg));
                            return;
                        }

                        List<Wallpaper> newWallpapers = parseUnifiedAPIResponse(response);

                        List<Wallpaper> deduped = deduplicateByUrl(newWallpapers);

                        if (deduped.isEmpty() && !isPagination) {
                            handleError(context.getString(R.string.no_wallpapers_found));
                            return;
                        }

                        if (!isPagination) {
                            responseCache.put(cacheKey, new CachedResponse(new ArrayList<>(deduped)));
                        }

                        if (isPagination) {
                            isLoadingMore = false;
                            if (callback != null) callback.onMoreWallpapersLoaded(deduped);
                        } else {
                            if (callback != null) callback.onWallpapersLoaded(deduped);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Response parse error", e);
                        handleError(context.getString(R.string.failed_to_load_wallpapers));
                    }
                },
                error -> {
                    Log.e(TAG, "Network error: " + (error.getMessage() != null ? error.getMessage() : "unknown"));

                    if (!isPagination) {
                        CachedResponse stale = responseCache.get(cacheKey);
                        if (stale != null) {
                            Log.d(TAG, "Falling back to stale cache for: " + cacheKey);
                            if (callback != null) callback.onWallpapersLoaded(new ArrayList<>(stale.wallpapers));
                            return;
                        }
                    }

                    handleError(context.getString(R.string.network_error_check_connection));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                10000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    private List<Wallpaper> deduplicateByUrl(List<Wallpaper> wallpapers) {
        Set<String> seen = new HashSet<>();
        List<Wallpaper> result = new ArrayList<>();
        for (Wallpaper wp : wallpapers) {
            String url = wp.getImageUrl();
            if (url != null && !seen.contains(url)) {
                seen.add(url);
                result.add(wp);
            }
        }
        return result;
    }

    private List<Wallpaper> parseUnifiedAPIResponse(JSONObject response) {
        List<Wallpaper> wallpapers = new ArrayList<>();
        JSONArray dataArray = response.optJSONArray("data");
        if (dataArray == null) return wallpapers;

        for (int i = 0; i < dataArray.length(); i++) {
            try {
                JSONObject item = dataArray.getJSONObject(i);

                String id = item.optString("id", "unknown_" + i);
                String source = item.optString("source", "Unknown");

                JSONObject urls = item.optJSONObject("urls");
                String rawUrl = "";
                String thumbUrl = "";
                if (urls != null) {
                    rawUrl = urls.optString("raw", urls.optString("regular", ""));
                    thumbUrl = urls.optString("regular", urls.optString("small", rawUrl));
                }

                if (rawUrl.isEmpty()) continue;

                JSONObject info = item.optJSONObject("info");
                String title = info != null ? info.optString("title", "Wallpaper") : "Wallpaper";
                String author = info != null ? info.optString("author", "Unknown") : "Unknown";

                String apiCategory = item.optString("category", "");
                String category = !apiCategory.isEmpty() ? apiCategory : determineCategoryFromTitle(title, source);

                JSONObject meta = item.optJSONObject("meta");
                int width = 0;
                int height = 0;
                if (meta != null) {
                    width = meta.optInt("width", 0);
                    height = meta.optInt("height", 0);
                }

                if (!currentSourceFilter.equals("all") && !source.toLowerCase().equals(currentSourceFilter)) continue;

                Wallpaper wallpaper = new Wallpaper(
                        id,
                        rawUrl,
                        thumbUrl,
                        title, category, source, author, false
                );

                wallpaper.setWidth(width);
                wallpaper.setHeight(height);

                wallpapers.add(wallpaper);

            } catch (JSONException e) {
                Log.e(TAG, "Parse error at index " + i, e);
            }
        }
        return wallpapers;
    }

    private String determineCategoryFromTitle(String title, String source) {
        if (title == null || title.isEmpty()) return "General";
        String lower = title.toLowerCase();
        if (lower.contains("nature") || lower.contains("tree") || lower.contains("mountain") || lower.contains("landscape")) return "Nature";
        if (lower.contains("animal") || lower.contains("wildlife") || lower.contains("cat") || lower.contains("dog")) return "Animals";
        if (lower.contains("abstract") || lower.contains("pattern")) return "Abstract";
        if (lower.contains("city") || lower.contains("urban")) return "Cities";
        if (lower.contains("space") || lower.contains("stars")) return "Space";
        if (lower.contains("beach") || lower.contains("ocean")) return "Beaches";
        if (source != null && source.equalsIgnoreCase("Wallhaven")) return "Wallhaven";
        return "General";
    }

    public void loadWallpapersByColor(String colorHex, ApiCallback callback) {
        this.callback = callback;
        resetPagination();
        currentQuery = convertColorToQuery(colorHex);
        currentSourceFilter = "all";
        loadFromUnifiedAPI(currentQuery, 1, DEFAULT_PAGE_SIZE, false);
    }

    private String convertColorToQuery(String colorHex) {
        switch (colorHex.toUpperCase()) {
            case "#FFB6D9":
            case "#FF1493": return "pink";
            case "#4169E1": return "blue";
            case "#8B00FF": return "purple";
            case "#40E0D0": return "turquoise";
            case "#2C2C2C": return "black";
            case "#FF8C00": return "orange";
            case "#32CD32": return "green";
            default: return "colorful";
        }
    }

    private void handleError(String message) {
        isLoadingMore = false;
        if (callback != null) callback.onError(message);
    }
}
