package com.wall.mob;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class WallpaperRepository {

    private static final String TAG = "WallpaperRepository";
    private static final String UNIFIED_API_URL = "https://api-server.rahulkumarbknv.workers.dev/";

    private static WallpaperRepository instance;
    private RequestQueue requestQueue;
    private Context context;

    public static WallpaperRepository getInstance() {
        if (instance == null) {
            instance = new WallpaperRepository();
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
        this.requestQueue = Volley.newRequestQueue(this.context);
    }

    public Wallpaper getWallpaperById(String id) {
        if (id == null || id.isEmpty()) return null;

        Wallpaper fromFirebase = tryFirebase(id);
        if (fromFirebase != null) return fromFirebase;

        Wallpaper fromApi = tryApi(id);
        if (fromApi != null) return fromApi;

        return null;
    }

    private Wallpaper tryFirebase(String id) {
        try {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("wallpapers/premium");
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Wallpaper> result = new AtomicReference<>(null);

            ref.orderByKey().equalTo(id).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Wallpaper wp = parseFirebaseWallpaper(child);
                        if (wp != null) result.set(wp);
                    }
                    latch.countDown();
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);
            return result.get();
        } catch (Exception e) {
            Log.e(TAG, "Firebase lookup failed", e);
            return null;
        }
    }

    private Wallpaper tryApi(String id) {
        try {
            String url = UNIFIED_API_URL + "?query=" + id + "&page=1&per_page=10&source=all";
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Wallpaper> result = new AtomicReference<>(null);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                    response -> {
                        try {
                            JSONArray data = response.optJSONArray("data");
                            if (data != null) {
                                for (int i = 0; i < data.length(); i++) {
                                    JSONObject item = data.getJSONObject(i);
                                    String itemId = item.optString("id", "");
                                    if (itemId.equals(id)) {
                                        result.set(parseApiWallpaper(item, id));
                                        break;
                                    }
                                }
                                if (result.get() == null && data.length() > 0) {
                                    result.set(parseApiWallpaper(data.getJSONObject(0), id));
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "API parse error", e);
                        }
                        latch.countDown();
                    },
                    error -> {
                        Log.e(TAG, "API error", error);
                        latch.countDown();
                    });

            requestQueue.add(request);
            latch.await(10, TimeUnit.SECONDS);
            return result.get();
        } catch (Exception e) {
            Log.e(TAG, "API lookup failed", e);
            return null;
        }
    }

    private Wallpaper parseFirebaseWallpaper(DataSnapshot snapshot) {
        try {
            Object value = snapshot.getValue();
            if (value instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) value;
                String id = snapshot.getKey();
                String imageUrl = (String) map.get("imageUrl");
                String thumbnailUrl = (String) map.get("thumbnailUrl");
                String title = (String) map.get("title");
                String category = (String) map.get("category");
                String source = (String) map.get("source");
                String photographer = (String) map.get("photographer");

                if (imageUrl == null) imageUrl = "";
                if (thumbnailUrl == null || thumbnailUrl.isEmpty()) thumbnailUrl = imageUrl;
                if (title == null) title = "Premium Wallpaper";
                if (category == null) category = "Premium";
                if (source == null) source = "Firebase";

                return new Wallpaper(id, imageUrl, thumbnailUrl, title, category, source, photographer, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Firebase parse error", e);
        }
        return null;
    }

    private Wallpaper parseApiWallpaper(JSONObject item, String fallbackId) {
        try {
            String id = item.optString("id", fallbackId);
            JSONObject urls = item.optJSONObject("urls");
            String imageUrl = "";
            String thumbUrl = "";
            if (urls != null) {
                imageUrl = urls.optString("raw", urls.optString("regular", ""));
                thumbUrl = urls.optString("regular", urls.optString("small", imageUrl));
            }
            if (imageUrl.isEmpty()) return null;

            JSONObject info = item.optJSONObject("info");
            String title = info != null ? info.optString("title", "Wallpaper") : "Wallpaper";
            String author = info != null ? info.optString("author", "Unknown") : "Unknown";
            String category = item.optString("category", "General");
            String source = item.optString("source", "Unknown");

            return new Wallpaper(id, imageUrl, thumbUrl, title, category, source, author, false);
        } catch (Exception e) {
            Log.e(TAG, "API parse error", e);
            return null;
        }
    }
}
