package com.wall.mob;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SeeAllActivity extends BaseActivity implements WallpaperAdapter.OnWallpaperClickListener {


    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_TYPE = "extra_type";

    public static final String TYPE_TRENDING = "type_trending";
    public static final String TYPE_PREMIUM = "type_premium";

    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private ProgressBar paginationProgressBar;
    private View emptyText;

    private WallpaperAdapter adapter;
    private List<Wallpaper> wallpapers = new ArrayList<>();

    private String title;
    private String type;
    private ApiManager apiManager;

    // Helper method to launch this activity easily
    public static void start(Context context, String title, String type) {
        Intent intent = new Intent(context, SeeAllActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_TYPE, type);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_see_all);

        // Get intent data
        title = getIntent().getStringExtra(EXTRA_TITLE);
        type = getIntent().getStringExtra(EXTRA_TYPE);

        initializeViews();
        setupStatusBar();
        setupRecyclerView();

        loadData();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recycler_see_all);
        progressBar = findViewById(R.id.progress_bar);
        paginationProgressBar = findViewById(R.id.pagination_progress_bar);
        emptyText = findViewById(R.id.empty_text);

        apiManager = new ApiManager(this);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

        private void setupStatusBar() {
    ThemeUtils.applySystemBars(this);
 }

    private void setupRecyclerView() {
        // Use 2 columns like trending section
        int spanCount = 2;
        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
        recyclerView.setLayoutManager(layoutManager);
        
        adapter = new WallpaperAdapter(this, wallpapers, this, true);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
                if (lm != null && !isLoading && lm.findLastVisibleItemPosition() >= lm.getItemCount() - 6) {
                    isLoading = true;
                    paginationProgressBar.setVisibility(View.VISIBLE);
                    apiManager.loadNextPage();
                }
            }
        });
    }

    private boolean isLoading = false;

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);

        if (TYPE_PREMIUM.equals(type)) {
            loadPremiumFromFirebase();
        } else {
            loadTrendingFromApi();
        }
    }

    private void loadTrendingFromApi() {
        // Uses the exact same method your HomeFragment uses to get the "Best of Month"
        apiManager.loadWallpapersFromAllSources(new ApiManager.ApiCallback() {
            @Override
            public void onWallpapersLoaded(List<Wallpaper> loadedWallpapers) {
                runOnUiThread(() -> {
                    paginationProgressBar.setVisibility(View.GONE);
                    handleDataLoaded(loadedWallpapers);
                });
            }

            @Override
            public void onMoreWallpapersLoaded(List<Wallpaper> newWallpapers) {
                runOnUiThread(() -> {
                    paginationProgressBar.setVisibility(View.GONE);
                    isLoading = false;
                    if (newWallpapers != null && !newWallpapers.isEmpty()) {
                        wallpapers.addAll(newWallpapers);
                        adapter.addData(newWallpapers);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    isLoading = false;
                    progressBar.setVisibility(View.GONE);
                    paginationProgressBar.setVisibility(View.GONE);
                    emptyText.setVisibility(View.VISIBLE);
                    TextView emptyTextView = emptyText.findViewById(R.id.empty_message_text);
                    if (emptyTextView != null) {
                        emptyTextView.setText(message);
                    }
                });
            }
        });
    }

    private void loadPremiumFromFirebase() {
        DatabaseReference firebasePremiumRef = FirebaseDatabase.getInstance().getReference("wallpapers/premium");
        firebasePremiumRef.orderByChild("addedAt").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Wallpaper> loadedPremium = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        Wallpaper wallpaper = parseWallpaperManually(child);
                        if (wallpaper != null && wallpaper.getImageUrl() != null) {
                            loadedPremium.add(wallpaper);
                        }
                    } catch (Exception ignored) {}
                }
                Collections.reverse(loadedPremium); // Newest first
                handleDataLoaded(loadedPremium);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                emptyText.setVisibility(View.VISIBLE);
                TextView emptyTextView = emptyText.findViewById(R.id.empty_message_text);
                if (emptyTextView != null) {
                    emptyTextView.setText(R.string.failed_load_premium_wallpapers_period);
                }
            }
        });
    }

    private Wallpaper parseWallpaperManually(DataSnapshot snapshot) {
        try {
            Object value = snapshot.getValue();
            if (value instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) value;
                String id = snapshot.getKey();
                String imageUrl = (String) map.get("thumbnailUrl");
                String title = (String) map.get("title");
                String category = (String) map.get("category");
                String source = (String) map.get("source");
                
                if (title == null) title = getString(R.string.premium);
                if (category == null) category = getString(R.string.premium);
                
                return new Wallpaper(id, imageUrl, title, category, source, "", true);
            }
        } catch (Exception e) { return null; }
        return null;
    }

    private void handleDataLoaded(List<Wallpaper> data) {
        progressBar.setVisibility(View.GONE);
        wallpapers.clear();
        wallpapers.addAll(data);

        if (wallpapers.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            adapter.updateData(wallpapers);
        }
    }

    @Override
    public void onWallpaperClick(Wallpaper wallpaper) {
        WallpaperDetailsActivity.start(this, wallpaper);
    }

    @Override
    public void onWallpaperLongClick(Wallpaper wallpaper, int position) {
        // Optional implementation
    }
}
// test
