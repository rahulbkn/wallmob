package com.wall.mob;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class CategoryActivity extends AppCompatActivity implements WallpaperAdapter.OnWallpaperClickListener {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    private static final String TAG = "CategoryActivity";
    private static final String EXTRA_CATEGORY_NAME = "category_name";
    private static final String EXTRA_CATEGORY_IMAGE = "category_image";
    private static final String EXTRA_IS_COLOR_FILTER = "is_color_filter";
    private static final String EXTRA_COLOR_HEX = "color_hex";

    // Constants for pagination
    private static final int PAGE_SIZE = 24;

    // UI Components
    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbar;
    private Toolbar toolbar;
    private ImageView categoryHeaderImage;
    private TextView categoryNameText;
    private TextView wallpaperCountText;
    private RecyclerView wallpapersRecycler;
    private ProgressBar progressBar;
    private ProgressBar footerProgressBar;
    private NestedScrollView nestedScrollView;
    private View emptyState;
    private FloatingActionButton fabScrollTop;
    private ImageView filterButton;
    private View colorIndicator;

    // Data
    private String categoryName;
    private String categoryImage;
    private List<Wallpaper> wallpapers = new ArrayList<>();
    private CategoryWallpaperAdapter adapter;
    private ApiManager apiManager;

    // Color filter state
    private boolean isColorFilter = false;
    private String colorHex;

    // Pagination state
    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isLastPage = false;

    public static void start(Context context, String categoryName, String categoryImage) {
        Intent intent = new Intent(context, CategoryActivity.class);
        intent.putExtra(EXTRA_CATEGORY_NAME, categoryName);
        intent.putExtra(EXTRA_CATEGORY_IMAGE, categoryImage);
        intent.putExtra(EXTRA_IS_COLOR_FILTER, false);
        context.startActivity(intent);
    }

    public static void startWithColorFilter(Context context, String colorName, String colorHex) {
        Intent intent = new Intent(context, CategoryActivity.class);
        intent.putExtra(EXTRA_CATEGORY_NAME, colorName);
        intent.putExtra(EXTRA_CATEGORY_IMAGE, colorHex);
        intent.putExtra(EXTRA_IS_COLOR_FILTER, true);
        intent.putExtra(EXTRA_COLOR_HEX, colorHex);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.category); // Ensure this points to your updated category.xml

        getIntentData();
        initializeViews();
        setupToolbar();
        setupRecyclerView();
        setupColorFilterUI();
        loadFirstPage();
    }

    private void getIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME);
            categoryImage = intent.getStringExtra(EXTRA_CATEGORY_IMAGE);
            isColorFilter = intent.getBooleanExtra(EXTRA_IS_COLOR_FILTER, false);
            colorHex = intent.getStringExtra(EXTRA_COLOR_HEX);

            if (categoryName == null || categoryName.isEmpty()) {
                categoryName = "Category";
            }
        }
    }

    private void initializeViews() {
        appBarLayout = findViewById(R.id.app_bar);
        collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        toolbar = findViewById(R.id.toolbar);
        categoryHeaderImage = findViewById(R.id.category_header_image);
        categoryNameText = findViewById(R.id.category_name);
        wallpaperCountText = findViewById(R.id.wallpaper_count);
        wallpapersRecycler = findViewById(R.id.wallpapers_recycler);
        progressBar = findViewById(R.id.progress_bar);
        footerProgressBar = findViewById(R.id.footer_progress_bar);
        nestedScrollView = findViewById(R.id.nested_scroll_view);
        emptyState = findViewById(R.id.empty_state);
        fabScrollTop = findViewById(R.id.fab_scroll_top);
        filterButton = findViewById(R.id.filter_button);
        colorIndicator = findViewById(R.id.color_indicator);

        apiManager = new ApiManager(this);

        // Update the collapsing toolbar text colors to match the new White/Light theme
        if (collapsingToolbar != null) {
            collapsingToolbar.setTitle(categoryName);
            collapsingToolbar.setCollapsedTitleTextColor(Color.BLACK);
            collapsingToolbar.setExpandedTitleColor(Color.WHITE);
        }
        
        if (categoryNameText != null) {
            categoryNameText.setText(categoryName);
        }

        setupStatusBar();
        
        // Ensure the back button changes color when collapsed
        if (appBarLayout != null && toolbar != null) {
            appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                if (toolbar.getNavigationIcon() != null) {
                    if (Math.abs(verticalOffset) - appBarLayout.getTotalScrollRange() == 0) {
                        // Collapsed (surface/background) -> onSurface icon color
                        toolbar.getNavigationIcon().setTint(ContextCompat.getColor(CategoryActivity.this, R.color.onSurface));
                    } else {
                        // Expanded (Image background) -> White Icon
                        toolbar.getNavigationIcon().setTint(ContextCompat.getColor(CategoryActivity.this, R.color.white));
                    }
                }
            });
        }
    }

    private void setupStatusBar() {
    Window window = getWindow();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        window.setStatusBarColor(ContextCompat.getColor(this, R.color.white));
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.white));

        View decorView = window.getDecorView();
        int flags = decorView.getSystemUiVisibility();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // black status bar icons
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; // black nav icons
        }

        decorView.setSystemUiVisibility(flags);
    }
}

    private void setupColorFilterUI() {
        if (isColorFilter && colorHex != null && !colorHex.isEmpty()) {
            try {
                int parsedColor = Color.parseColor(colorHex);
                categoryHeaderImage.setBackgroundColor(parsedColor);
                categoryHeaderImage.setImageDrawable(null);

                if (colorIndicator != null) {
                    GradientDrawable indicatorDrawable = new GradientDrawable();
                    indicatorDrawable.setShape(GradientDrawable.OVAL);
                    indicatorDrawable.setColor(parsedColor);
                    int strokePx = dpToPx(3);
                    indicatorDrawable.setStroke(strokePx, ContextCompat.getColor(CategoryActivity.this, R.color.onSurface));
                    int sizePx = dpToPx(40);
                    indicatorDrawable.setSize(sizePx, sizePx);

                    colorIndicator.setBackground(indicatorDrawable);
                    colorIndicator.setVisibility(View.VISIBLE);
                }
                categoryNameText.setText(categoryName + " Tone Wallpapers");

            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid color hex code: " + colorHex, e);
                categoryHeaderImage.setBackgroundColor(ContextCompat.getColor(this, R.color.gray_light));
                if (colorIndicator != null) colorIndicator.setVisibility(View.GONE);
                categoryNameText.setText(categoryName + " Wallpapers");
            }
        } else {
            if (colorIndicator != null) colorIndicator.setVisibility(View.GONE);
            if (categoryImage != null && !categoryImage.isEmpty()) {
                Glide.with(this)
                        .load(categoryImage)
                        .centerCrop()
                        .placeholder(R.drawable.bg)
                        .into(categoryHeaderImage);
            } else {
                categoryHeaderImage.setImageResource(R.drawable.bg);
            }
            categoryNameText.setText(categoryName);
        }

        if (filterButton != null) {
            filterButton.setOnClickListener(v ->
                    Toast.makeText(this, "Filter options coming soon", Toast.LENGTH_SHORT).show());
        }

        if (fabScrollTop != null) {
            fabScrollTop.setOnClickListener(v ->
                    wallpapersRecycler.smoothScrollToPosition(0));
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupRecyclerView() {
        int spanCount = calculateSpanCount();
        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
        wallpapersRecycler.setLayoutManager(layoutManager);
        wallpapersRecycler.setNestedScrollingEnabled(false);

        adapter = new CategoryWallpaperAdapter(this, wallpapers, this);
        wallpapersRecycler.setAdapter(adapter);

        if (nestedScrollView != null) {
            nestedScrollView.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (scrollView, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (fabScrollTop != null) {
                        if (scrollY > oldScrollY) {
                            fabScrollTop.show();
                        } else if (scrollY < oldScrollY) {
                            fabScrollTop.hide();
                        }
                    }

                    boolean reachedBottom = !scrollView.canScrollVertically(1);
                    if (reachedBottom && !isLoading && !isLastPage) {
                        loadNextPage();
                    }
                });
        }
    }

    private int calculateSpanCount() {
        float screenWidthDp = getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density;
        int spanCount = (int) (screenWidthDp / 160);
        return Math.max(2, Math.min(4, spanCount));
    }

    private void loadFirstPage() {
        showLoading(true);
        showEmptyState(false);
        currentPage = 1;
        isLastPage = false;
        wallpapers.clear();

        if (isColorFilter) {
            loadColorWallpapers();
        } else {
            loadCategoryWallpapers();
        }
    }

    private void loadCategoryWallpapers() {
        apiManager.loadWallpapersByQuery(categoryName, PAGE_SIZE, currentPage, new ApiManager.ApiCallback() {
            @Override
            public void onWallpapersLoaded(List<Wallpaper> loadedWallpapers) {
                runOnUiThread(() -> handleWallpapersLoaded(loadedWallpapers));
            }
            @Override
            public void onMoreWallpapersLoaded(List<Wallpaper> newWallpapers) {}
            @Override
            public void onError(String message) {
                runOnUiThread(() -> handleLoadError(message));
            }
        });
    }

    private void loadColorWallpapers() {
        String colorQuery = createColorQuery();
        apiManager.loadWallpapersByQuery(colorQuery, PAGE_SIZE, currentPage, new ApiManager.ApiCallback() {
            @Override
            public void onWallpapersLoaded(List<Wallpaper> loadedWallpapers) {
                runOnUiThread(() -> handleWallpapersLoaded(loadedWallpapers));
            }
            @Override
            public void onMoreWallpapersLoaded(List<Wallpaper> newWallpapers) {}
            @Override
            public void onError(String message) {
                runOnUiThread(() -> handleLoadError(message));
            }
        });
    }

    private String createColorQuery() {
        switch (colorHex != null ? colorHex.toUpperCase() : "") {
            case "#FFB6D9": case "#FF1493": return "pink abstract";
            case "#4169E1": return "blue sky ocean";
            case "#8B00FF": return "purple violet";
            case "#40E0D0": return "turquoise teal";
            case "#2C2C2C": return "dark black gray";
            case "#FF8C00": return "orange sunset";
            case "#32CD32": return "green nature";
            default: return "colorful abstract";
        }
    }

    private void handleWallpapersLoaded(List<Wallpaper> loadedWallpapers) {
        wallpapers.clear();
        wallpapers.addAll(loadedWallpapers);
        adapter.updateData(wallpapers);
        updateWallpaperCount();

        isLoading = false;
        showLoading(false);

        if (wallpapers.isEmpty()) {
            showEmptyState(true);
        } 

        if (loadedWallpapers.size() < PAGE_SIZE) {
            isLastPage = true;
        }
    }

    private void handleLoadError(String message) {
        isLoading = false;
        showLoading(false);
        showEmptyState(wallpapers.isEmpty());
        Toast.makeText(CategoryActivity.this, "Error: " + message, Toast.LENGTH_LONG).show();
    }

    private void loadNextPage() {
        if (isLoading || isLastPage) return;
        isLoading = true;
        currentPage++;
        if (footerProgressBar != null) footerProgressBar.setVisibility(View.VISIBLE);

        if (isColorFilter) {
            loadNextPageColor();
        } else {
            loadNextPageCategory();
        }
    }

    private void loadNextPageCategory() {
        apiManager.loadWallpapersByQuery(categoryName, PAGE_SIZE, currentPage, new ApiManager.ApiCallback() {
            @Override
            public void onWallpapersLoaded(List<Wallpaper> loadedWallpapers) {}
            @Override
            public void onMoreWallpapersLoaded(List<Wallpaper> newWallpapers) {
                runOnUiThread(() -> handleMoreWallpapersLoaded(newWallpapers));
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> handleMoreWallpapersError(message));
            }
        });
    }

    private void loadNextPageColor() {
        String colorQuery = createColorQuery();
        apiManager.loadWallpapersByQuery(colorQuery, PAGE_SIZE, currentPage, new ApiManager.ApiCallback() {
            @Override
            public void onWallpapersLoaded(List<Wallpaper> loadedWallpapers) {}
            @Override
            public void onMoreWallpapersLoaded(List<Wallpaper> newWallpapers) {
                runOnUiThread(() -> handleMoreWallpapersLoaded(newWallpapers));
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> handleMoreWallpapersError(message));
            }
        });
    }

    private void handleMoreWallpapersLoaded(List<Wallpaper> newWallpapers) {
        if (footerProgressBar != null) footerProgressBar.setVisibility(View.GONE);
        if (newWallpapers.isEmpty()) {
            isLastPage = true;
            isLoading = false;
            return;
        }

        wallpapers.addAll(newWallpapers);
        adapter.addData(newWallpapers);
        updateWallpaperCount();
        isLoading = false;

        if (newWallpapers.size() < PAGE_SIZE) {
            isLastPage = true;
        }
    }

    private void handleMoreWallpapersError(String message) {
        runOnUiThread(() -> {
            if (footerProgressBar != null) footerProgressBar.setVisibility(View.GONE);
            isLoading = false;
            currentPage--; 
        });
    }

    private void updateWallpaperCount() {
        if (wallpaperCountText != null) {
            int count = wallpapers.size();
            String text = count + (count == 1 ? " wallpaper" : " wallpapers");
            wallpaperCountText.setText(text);
        }
    }

    private void showLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (wallpapersRecycler != null) {
            if (!show || currentPage == 1) {
                wallpapersRecycler.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyState != null) emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        if (wallpapersRecycler != null) wallpapersRecycler.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onWallpaperClick(Wallpaper wallpaper) {
        WallpaperDetailsActivity.start(this, wallpaper);
    }

    @Override
    public void onWallpaperLongClick(Wallpaper wallpaper, int position) {
        // Optional implementation
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}