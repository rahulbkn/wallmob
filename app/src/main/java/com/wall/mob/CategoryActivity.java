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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class CategoryActivity extends BaseActivity implements WallpaperAdapter.OnWallpaperClickListener {

    @Override

    private static final String TAG = "CategoryActivity";
    private static final String EXTRA_CATEGORY_NAME = "category_name";
    private static final String EXTRA_CATEGORY_IMAGE = "category_image";
    private static final String EXTRA_IS_COLOR_FILTER = "is_color_filter";
    private static final String EXTRA_COLOR_HEX = "color_hex";

    private static final int PAGE_SIZE = 24;

    // UI Components
    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbar;
    private Toolbar toolbar;
    private ImageView categoryHeaderImage;
    private RecyclerView wallpapersRecycler;
    private ProgressBar progressBar;
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
        setContentView(R.layout.category);

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
            if (categoryName == null || categoryName.isEmpty()) categoryName = "Category";
        }
    }

    private void initializeViews() {
        appBarLayout = findViewById(R.id.app_bar);
        collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        toolbar = findViewById(R.id.toolbar);
        categoryHeaderImage = findViewById(R.id.category_header_image);
        wallpapersRecycler = findViewById(R.id.wallpapers_recycler);
        progressBar = findViewById(R.id.progress_bar);
        emptyState = findViewById(R.id.empty_state);
        fabScrollTop = findViewById(R.id.fab_scroll_top);
        filterButton = findViewById(R.id.filter_button);
        colorIndicator = findViewById(R.id.color_indicator);

        apiManager = new ApiManager(this);

        if (collapsingToolbar != null) {
            collapsingToolbar.setTitle(categoryName);
            collapsingToolbar.setCollapsedTitleTextColor(ContextCompat.getColor(this, R.color.onSurface));
            collapsingToolbar.setExpandedTitleColor(ContextCompat.getColor(this, R.color.white));
        }

        setupStatusBar();

        if (appBarLayout != null && toolbar != null) {
            appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                if (toolbar.getNavigationIcon() != null) {
                    if (Math.abs(verticalOffset) - appBarLayout.getTotalScrollRange() == 0) {
                        toolbar.getNavigationIcon().setTint(ContextCompat.getColor(CategoryActivity.this, R.color.onSurface));
                    } else {
                        toolbar.getNavigationIcon().setTint(ContextCompat.getColor(CategoryActivity.this, R.color.white));
                    }
                }
            });
        }
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ThemeUtils.applySystemBars(this);
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
                    indicatorDrawable.setStroke(dpToPx(3), ContextCompat.getColor(CategoryActivity.this, R.color.onSurface));
                    indicatorDrawable.setSize(dpToPx(40), dpToPx(40));
                    colorIndicator.setBackground(indicatorDrawable);
                    colorIndicator.setVisibility(View.VISIBLE);
                }

                // Update header in adapter
                if (adapter != null) adapter.setHeaderText(categoryName + " Tone Wallpapers");

            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid color hex: " + colorHex, e);
                categoryHeaderImage.setBackgroundColor(ContextCompat.getColor(this, R.color.gray_light));
                if (colorIndicator != null) colorIndicator.setVisibility(View.GONE);
                if (adapter != null) adapter.setHeaderText(categoryName + " Wallpapers");
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
            if (adapter != null) adapter.setHeaderText(categoryName);
        }

        if (filterButton != null) {
            filterButton.setOnClickListener(v ->
                    Toast.makeText(this, getString(R.string.filter_options_coming_soon), Toast.LENGTH_SHORT).show());
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

        // Header row spans full width
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter != null && adapter.isHeader(position) ? spanCount : 1;
            }
        });

        wallpapersRecycler.setLayoutManager(layoutManager);
        wallpapersRecycler.setHasFixedSize(false); // false because header exists
        // No setNestedScrollingEnabled(false) — let RecyclerView scroll natively

        adapter = new CategoryWallpaperAdapter(this, wallpapers, this);
        adapter.setHeaderText(categoryName);
        wallpapersRecycler.setAdapter(adapter);

        // Scroll listener replaces NestedScrollView listener
        wallpapersRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                // FAB show/hide
                if (fabScrollTop != null) {
                    if (dy > 10) fabScrollTop.show();
                    else if (dy < -10) fabScrollTop.hide();
                }

                // Pagination trigger — load when 6 items from end
                GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
                if (lm != null && !isLoading && !isLastPage) {
                    int totalItems = lm.getItemCount();
                    int lastVisible = lm.findLastVisibleItemPosition();
                    if (lastVisible >= totalItems - 6) {
                        loadNextPage();
                    }
                }
            }
        });
    }

    private int calculateSpanCount() {
        float screenWidthDp = getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density;
        return Math.max(2, Math.min(4, (int) (screenWidthDp / 160)));
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
        isLoading = false;
        showLoading(false);
        showEmptyState(wallpapers.isEmpty());
        if (loadedWallpapers.size() < PAGE_SIZE) isLastPage = true;
    }

    private void handleLoadError(String message) {
        isLoading = false;
        showLoading(false);
        showEmptyState(wallpapers.isEmpty());
        Toast.makeText(CategoryActivity.this, getString(R.string.error_message, message), Toast.LENGTH_LONG).show();
    }

    private void loadNextPage() {
        if (isLoading || isLastPage) return;
        isLoading = true;
        currentPage++;
        if (adapter != null) adapter.showFooterLoading(true);

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
                runOnUiThread(() -> handleMoreWallpapersError());
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
                runOnUiThread(() -> handleMoreWallpapersError());
            }
        });
    }

    private void handleMoreWallpapersLoaded(List<Wallpaper> newWallpapers) {
        adapter.showFooterLoading(false);
        if (newWallpapers.isEmpty()) {
            isLastPage = true;
            isLoading = false;
            return;
        }
        wallpapers.addAll(newWallpapers);
        adapter.addData(newWallpapers);
        isLoading = false;
        if (newWallpapers.size() < PAGE_SIZE) isLastPage = true;
    }

    private void handleMoreWallpapersError() {
        adapter.showFooterLoading(false);
        isLoading = false;
        currentPage--;
    }

    private void showLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (wallpapersRecycler != null) wallpapersRecycler.setVisibility(show && currentPage == 1 ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(boolean show) {
        if (emptyState != null) emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        if (wallpapersRecycler != null && show) wallpapersRecycler.setVisibility(View.GONE);
    }

    @Override
    public void onWallpaperClick(Wallpaper wallpaper) {
        WallpaperDetailsActivity.start(this, wallpaper);
    }

    @Override
    public void onWallpaperLongClick(Wallpaper wallpaper, int position) {}

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
