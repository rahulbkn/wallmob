package com.wall.mob;

import android.content.Context;
import android.util.Log;
import com.android.volley.RequestQueue;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ApiManager {
    private static final String TAG = "ApiManager";

    private RequestQueue requestQueue;
    private ApiCallback callback;
    private Context context;
    private Random random;
    private DeviceUtils deviceUtils;

    private static final String UNIFIED_API_URL = "https://api-server.rahulkumarbknv.workers.dev/";
    private static final int DEFAULT_PAGE_SIZE = 100;

    private String currentQuery;
    private int currentPage = 1;
    private boolean isLoadingMore = false;
    private String currentSourceFilter = "all"; 

    private static final String[] SEARCH_QUERIES = {
            "nature", "abstract", "landscape", "animals", "city",
            "space", "beach", "minimal", "dark", "colorful"
    };

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
        String url = UNIFIED_API_URL + "?query=" + query.replace(" ", "+") + "&page=" + page + "&per_page=" + perPage;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONObject meta = response.optJSONObject("meta");
                        if (meta != null && !meta.optBoolean("success", false)) {
                            handleError("API error: " + response.optString("error", "Unknown error"));
                            return;
                        }

                        List<Wallpaper> newWallpapers = parseUnifiedAPIResponse(response);
                        if (newWallpapers.isEmpty() && !isPagination) {
                            handleError("No wallpapers found.");
                            return;
                        }

                        if (isPagination) {
                            isLoadingMore = false;
                            if (callback != null) callback.onMoreWallpapersLoaded(newWallpapers);
                        } else {
                            if (callback != null) callback.onWallpapersLoaded(newWallpapers);
                        }
                    } catch (Exception e) {
                        handleError("Failed to load wallpapers.");
                    }
                },
                error -> handleError("Network error. Check your connection.")
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Cache-Control", "no-cache");
                headers.put("Accept", "application/json");
                return headers;
            }
        };
        requestQueue.add(request);
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
                    rawUrl = urls.optString("raw", urls.optString("full", ""));
                    // 🔥 FIXED: Prioritize 'regular' size to fix low-quality thumbnail issues
                    thumbUrl = urls.optString("regular", urls.optString("small", rawUrl)); 
                }

                if (rawUrl.isEmpty()) continue;

                JSONObject info = item.optJSONObject("info");
                String title = info != null ? info.optString("title", "Wallpaper") : "Wallpaper";
                String author = info != null ? info.optString("author", "Unknown") : "Unknown";
                String apiCategory = item.optString("category", "");
                String category = !apiCategory.isEmpty() ? apiCategory : determineCategoryFromTitle(title, source);

                if (!currentSourceFilter.equals("all") && !source.toLowerCase().equals(currentSourceFilter)) continue;

                wallpapers.add(new Wallpaper(
                        source.toLowerCase() + "_" + id,
                        rawUrl,         
                        thumbUrl,       
                        title, category, source, author, false
                ));
            } catch (JSONException e) { Log.e(TAG, "Parse error", e); }
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
