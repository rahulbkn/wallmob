package com.wall.mob;

//
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;

import androidx.core.content.ContextCompat;
//
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends AppCompatActivity implements WallpaperAdapter.OnWallpaperClickListener {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    private RecyclerView searchResultsRecyclerView;
    private TextView noResultsText;
    private ProgressBar loadingProgressBar;
    private Toolbar toolbar;
    private SearchView searchView;

    private WallpaperAdapter wallpaperAdapter;
    private List<Wallpaper> premiumWallpapers = new ArrayList<>();
    private List<Wallpaper> apiWallpapers = new ArrayList<>();
    private List<Wallpaper> searchResults = new ArrayList<>();

    private DatabaseReference premiumDatabaseReference;
    private ValueEventListener premiumValueEventListener;
    private ApiManager apiManager;

    private ProgressDialog searchProgressDialog;
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;

    private boolean isSearchingApi = false;
    private boolean isLoadingMore = false;
    private boolean hasMorePages = true;
    private String currentSearchQuery = "";
    private static final long SEARCH_DEBOUNCE_DELAY = 600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);
        
        initializeViews();
        setupRecyclerView();
        loadPremiumWallpapersOnly();
    }

    // Replace the initializeViews() method in SearchActivity.java
private void initializeViews() {
    // Initialize all views
    toolbar = findViewById(R.id.toolbar);
    searchResultsRecyclerView = findViewById(R.id.search_results_recycler_view);
    noResultsText = findViewById(R.id.no_results_text);
    loadingProgressBar = findViewById(R.id.loading_progress_bar);
    searchView = findViewById(R.id.search_view);

    // Setup Firebase and API
    premiumDatabaseReference = FirebaseDatabase.getInstance().getReference("wallpapers/premium");
    apiManager = new ApiManager(this);

    // Setup toolbar
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Search Wallpapers");
    }

    // Fixed: Don't show keyboard immediately, wait for activity to be ready
    searchView.setIconifiedByDefault(false);
    searchView.setQueryHint("Search wallpapers...");
    
    // Request focus and show keyboard after a short delay
    searchView.postDelayed(() -> {
        searchView.requestFocus();
        showKeyboard();
    }, 300);

    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
        @Override
        public boolean onQueryTextSubmit(String query) {
            performSearch(query.trim());
            hideKeyboard();
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            scheduleSearch(newText.trim());
            return true;
        }
    });

    setupProgressDialog();
}

private void setupStatusBar() {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
Window window = getWindow();
window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
window.setNavigationBarColor(ContextCompat.getColor(this, android.R.color.white));

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {  
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);  
        }  

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
            int flags = window.getDecorView().getSystemUiVisibility();  
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;  
            window.getDecorView().setSystemUiVisibility(flags);  
        }  
    }  
}

    private void scheduleSearch(String query) {
        // Cancel previous pending search
        if (debounceRunnable != null) {
            debounceHandler.removeCallbacks(debounceRunnable);
        }

        // Schedule new search after debounce delay
        debounceRunnable = () -> performSearch(query);
        debounceHandler.postDelayed(debounceRunnable, SEARCH_DEBOUNCE_DELAY);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && searchView != null) {
            imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
        }
    }

    private void showKeyboard() {
        searchView.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void setupRecyclerView() {
        int spanCount = 2;
        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
        searchResultsRecyclerView.setLayoutManager(layoutManager);

        wallpaperAdapter = new WallpaperAdapter(this, new ArrayList<>(), this);
        searchResultsRecyclerView.setAdapter(wallpaperAdapter);

        // Infinite scroll
        searchResultsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                GridLayoutManager lm = (GridLayoutManager) searchResultsRecyclerView.getLayoutManager();
                if (dy > 0 && !isLoadingMore && hasMorePages && 
                    (lm.getChildCount() + lm.findFirstVisibleItemPosition()) >= lm.getItemCount() - 5) {
                    loadMoreApiResults();
                }
            }
        });
    }

    private void setupProgressDialog() {
        searchProgressDialog = new ProgressDialog(this);
        searchProgressDialog.setMessage("Searching online...");
        searchProgressDialog.setCancelable(false);
    }

    private void loadPremiumWallpapersOnly() {
        loadingProgressBar.setVisibility(View.VISIBLE);
        noResultsText.setVisibility(View.GONE);

        premiumValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                premiumWallpapers.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Wallpaper wallpaper = snapshot.getValue(Wallpaper.class);
                    if (wallpaper != null) {
                        premiumWallpapers.add(wallpaper);
                    }
                }

                runOnUiThread(() -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    showInitialState();
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                runOnUiThread(() -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    Toast.makeText(SearchActivity.this, "Failed to load premium wallpapers", Toast.LENGTH_SHORT).show();
                    showInitialState();
                });
            }
        };

        premiumDatabaseReference.addListenerForSingleValueEvent(premiumValueEventListener);
    }

    private void performSearch(String query) {
        if (query.length() < 2) {
            resetSearchState();
            return;
        }

        currentSearchQuery = query;
        String lowerQuery = query.toLowerCase(Locale.getDefault());

        // Instant local search (premium wallpapers)
        List<Wallpaper> localMatches = new ArrayList<>();
        for (Wallpaper w : premiumWallpapers) {
            if (matchesSearch(w, lowerQuery)) {
                localMatches.add(w);
            }
        }

        // Show local results immediately
        searchResults.clear();
        searchResults.addAll(localMatches);
        apiWallpapers.clear();
        hasMorePages = true;
        
        wallpaperAdapter.updateData(searchResults);
        updateNoResultsView(query, localMatches.isEmpty());

        // API search for meaningful queries only
        if (query.length() >= 3 && !isSearchingApi) {
            triggerApiSearch(query);
        }
    }

    private void triggerApiSearch(String query) {
        if (isSearchingApi) return;

        isSearchingApi = true;
        if (!searchProgressDialog.isShowing()) {
            searchProgressDialog.show();
        }

        apiManager.loadWallpapersByQuery(query, new ApiManager.ApiCallback() {
            @Override
            public void onWallpapersLoaded(List<Wallpaper> wallpapers) {
                runOnUiThread(() -> handleApiResults(wallpapers, true));
            }

            @Override
            public void onMoreWallpapersLoaded(List<Wallpaper> newWallpapers) {
                runOnUiThread(() -> handleApiResults(newWallpapers, false));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    searchProgressDialog.dismiss();
                    isSearchingApi = false;
                    Toast.makeText(SearchActivity.this, "Search failed: " + message, Toast.LENGTH_SHORT).show();
                    updateNoResultsView(currentSearchQuery, searchResults.isEmpty());
                });
            }
        });
    }

    private void handleApiResults(List<Wallpaper> newWallpapers, boolean isFirstPage) {
        searchProgressDialog.dismiss();
        isSearchingApi = false;

        if (isFirstPage) {
            apiWallpapers.clear();
        }

        if (!newWallpapers.isEmpty()) {
            apiWallpapers.addAll(newWallpapers);
            hasMorePages = newWallpapers.size() >= 30;
        } else {
            hasMorePages = false;
        }

        // Combine local premium + API results
        List<Wallpaper> combined = new ArrayList<>();
        String lowerQuery = currentSearchQuery.toLowerCase(Locale.getDefault());
        
        for (Wallpaper w : premiumWallpapers) {
            if (matchesSearch(w, lowerQuery)) {
                combined.add(w);
            }
        }
        combined.addAll(apiWallpapers);

        searchResults.clear();
        searchResults.addAll(combined);
        wallpaperAdapter.updateData(searchResults);
        updateNoResultsView(currentSearchQuery, combined.isEmpty());
    }

    private void loadMoreApiResults() {
        if (currentSearchQuery.length() < 3 || !hasMorePages || isLoadingMore || isSearchingApi) {
            return;
        }

        isLoadingMore = true;
        apiManager.loadNextPage();
    }

    private void updateNoResultsView(String query, boolean noResults) {
        if (noResults) {
            noResultsText.setVisibility(View.VISIBLE);
            noResultsText.setText("No results found for \"" + query + "\"");
            searchResultsRecyclerView.setVisibility(View.GONE);
        } else {
            noResultsText.setVisibility(View.GONE);
            searchResultsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showInitialState() {
        noResultsText.setVisibility(View.VISIBLE);
        noResultsText.setText("Start typing to search wallpapers");
        searchResultsRecyclerView.setVisibility(View.GONE);
        wallpaperAdapter.updateData(new ArrayList<>());
    }

    private void resetSearchState() {
        currentSearchQuery = "";
        searchResults.clear();
        apiWallpapers.clear();
        hasMorePages = true;
        isLoadingMore = false;
        isSearchingApi = false;

        wallpaperAdapter.updateData(new ArrayList<>());
        showInitialState();

        if (debounceRunnable != null) {
            debounceHandler.removeCallbacks(debounceRunnable);
        }
    }

    private boolean matchesSearch(Wallpaper wallpaper, String query) {
        if (wallpaper == null) return false;
        
        return (wallpaper.getTitle() != null && wallpaper.getTitle().toLowerCase(Locale.getDefault()).contains(query)) ||
               (wallpaper.getCategory() != null && wallpaper.getCategory().toLowerCase(Locale.getDefault()).contains(query)) ||
               (wallpaper.getPhotographer() != null && wallpaper.getPhotographer().toLowerCase(Locale.getDefault()).contains(query)) ||
               (wallpaper.getSource() != null && wallpaper.getSource().toLowerCase(Locale.getDefault()).contains(query));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onWallpaperClick(Wallpaper wallpaper) {
        WallpaperDetailsActivity.start(this, wallpaper);
    }

    @Override
    public void onWallpaperLongClick(Wallpaper wallpaper, int position) {
        Toast.makeText(this, "Long-clicked: " + wallpaper.getTitle(), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (premiumValueEventListener != null) {
            premiumDatabaseReference.removeEventListener(premiumValueEventListener);
        }
        if (debounceHandler != null && debounceRunnable != null) {
            debounceHandler.removeCallbacks(debounceRunnable);
        }
        if (searchProgressDialog != null && searchProgressDialog.isShowing()) {
            searchProgressDialog.dismiss();
        }
    }
}