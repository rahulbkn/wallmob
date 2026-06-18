package com.wall.mob;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final int PREMIUM_WALLPAPER_LIMIT = 10;

    // UI Components
    private RecyclerView recyclerBestMonth;
    private RecyclerView recyclerLandscape;
    private RecyclerView recyclerColorTone;
    private RecyclerView recyclerCategories;

    private View premiumSectionView;
    private TextView premiumSectionTitle;
    private RecyclerView recyclerPremium;

    private View landscapeSectionView;
    private TextView tvSeeAllTrending;

    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private View errorView;
    private Button retryButton;
    private TextView errorMessage;

    // Adapters
    private BestMonthAdapter bestMonthAdapter;
    private LandscapeAdapter landscapeAdapter;
    private ColorToneAdapter colorToneAdapter;
    private CategoryGridAdapter categoryGridAdapter;
    private BestMonthAdapter premiumAdapter;

    // Data
    private ApiManager apiManager;
    private List<Wallpaper> allWallpapers = new ArrayList<>();
    private List<Wallpaper> premiumWallpapers = new ArrayList<>();
    private Map<String, List<Wallpaper>> categoryWallpapers = new HashMap<>();
    private Set<String> loadedWallpaperIds = new HashSet<>();
    private Context mContext;

    // Firebase
    private DatabaseReference firebasePremiumRef;
    private ValueEventListener premiumValueListener;

    // Loading states
    private boolean isLoadingApiData = false;
    private boolean isLoadingPremiumData = false;
    private boolean isLoadingMore = false;

    private ViewPager2 heroCarousel;
    private LinearLayout heroDots;
    private HeroCarouselAdapter heroCarouselAdapter;
    private Handler autoScrollHandler = new Handler(Looper.getMainLooper());
    private int currentHeroPage = 0;
    private static final int AUTO_SCROLL_INTERVAL_MS = 3500;

    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (heroCarouselAdapter == null || heroCarouselAdapter.getItemCount() == 0) return;
            currentHeroPage = (currentHeroPage + 1) % heroCarouselAdapter.getItemCount();
            heroCarousel.setCurrentItem(currentHeroPage, true);
            autoScrollHandler.postDelayed(this, AUTO_SCROLL_INTERVAL_MS);
        }
    };

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.home, container, false);
        initializeViews(view);
        setupRecyclerViews();
        setupSwipeRefresh(view);
        firebasePremiumRef = FirebaseDatabase.getInstance().getReference("wallpapers/premium");

        // Populate static categories immediately so the UI doesn't look empty
        updateCategoriesSection();

        loadAllWallpapers();
        return view;
    }

    private void initializeViews(View view) {
        recyclerBestMonth = view.findViewById(R.id.recycler_best_month);
        recyclerLandscape = view.findViewById(R.id.recycler_landscape);
        landscapeSectionView = view.findViewById(R.id.landscape_section);
        recyclerColorTone = view.findViewById(R.id.recycler_color_tone);
        recyclerCategories = view.findViewById(R.id.recycler_categories);
        premiumSectionView = view.findViewById(R.id.premium_section);
        premiumSectionTitle = view.findViewById(R.id.premium_section_title);
        recyclerPremium = view.findViewById(R.id.recycler_premium);
        progressBar = view.findViewById(R.id.progress_bar);
        errorView = view.findViewById(R.id.error_view);
        retryButton = view.findViewById(R.id.retry_button);
        errorMessage = view.findViewById(R.id.error_message);
        tvSeeAllTrending = view.findViewById(R.id.tv_see_all_trending);
        TextView tvUnlockAll = view.findViewById(R.id.tv_unlock_all_premium);

        if (tvUnlockAll != null) tvUnlockAll.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).navigateToPremium();
        });

        if (tvSeeAllTrending != null) tvSeeAllTrending.setOnClickListener(v ->
                SeeAllActivity.start(requireContext(), getString(R.string.trending_now), SeeAllActivity.TYPE_TRENDING));

        apiManager = new ApiManager(requireContext());
        retryButton.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            loadAllWallpapers();
        });

        heroCarousel = view.findViewById(R.id.hero_carousel);
        heroDots = view.findViewById(R.id.hero_dots);
    }

    private void setupRecyclerViews() {
        if (mContext == null) return;

        // Trending Now Setup
        LinearLayoutManager bestMonthLayout = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        recyclerBestMonth.setLayoutManager(bestMonthLayout);
        recyclerBestMonth.setNestedScrollingEnabled(false);
        bestMonthAdapter = new BestMonthAdapter(mContext, new ArrayList<>(), this::onWallpaperClick);
        recyclerBestMonth.setAdapter(bestMonthAdapter);
        recyclerBestMonth.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null && !isLoadingMore && lm.findLastVisibleItemPosition() >= lm.getItemCount() - 3) {
                    isLoadingMore = true;
                    apiManager.loadNextPage();
                }
            }
        });

        // Premium Setup
        LinearLayoutManager premiumLayout = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        recyclerPremium.setLayoutManager(premiumLayout);
        recyclerPremium.setNestedScrollingEnabled(false);
        premiumAdapter = new BestMonthAdapter(mContext, new ArrayList<>(), this::onWallpaperClick);
        recyclerPremium.setAdapter(premiumAdapter);

        // Landscape Setup
        LinearLayoutManager landscapeLayout = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        recyclerLandscape.setLayoutManager(landscapeLayout);
        recyclerLandscape.setNestedScrollingEnabled(false);
        landscapeAdapter = new LandscapeAdapter(mContext, new ArrayList<>(), this::onWallpaperClick);
        recyclerLandscape.setAdapter(landscapeAdapter);
        recyclerLandscape.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null && !isLoadingMore && lm.findLastVisibleItemPosition() >= lm.getItemCount() - 3) {
                    isLoadingMore = true;
                    apiManager.loadNextPage();
                }
            }
        });

        // Color Palettes Setup
        LinearLayoutManager colorToneLayout = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        recyclerColorTone.setLayoutManager(colorToneLayout);
        recyclerColorTone.setNestedScrollingEnabled(false);
        List<String> colors = Arrays.asList("#FFB6D9", "#4169E1", "#8B00FF", "#40E0D0", "#2C2C2C", "#FF8C00", "#FF1493", "#32CD32");
        colorToneAdapter = new ColorToneAdapter(mContext, colors, this::onColorClick);
        recyclerColorTone.setAdapter(colorToneAdapter);

        // Categories Setup
        int span = 2;
        GridLayoutManager categoryLayout = new GridLayoutManager(mContext, span);
        recyclerCategories.setLayoutManager(categoryLayout);
        recyclerCategories.setNestedScrollingEnabled(false);
        recyclerCategories.addItemDecoration(new GridSpacingItemDecoration(span, dpToPx(2)));
        categoryGridAdapter = new CategoryGridAdapter(mContext, new ArrayList<>(), this::onCategoryClick);
        recyclerCategories.setAdapter(categoryGridAdapter);
    }

    private void setupSwipeRefresh(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::refreshData);
            swipeRefreshLayout.setColorSchemeResources(
                    android.R.color.holo_blue_bright,
                    android.R.color.holo_green_light,
                    android.R.color.holo_orange_light,
                    android.R.color.holo_red_light
            );
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ✅ BUG 4 FIXED: Use actual width/height from Wallpaper model instead of string matching
    private boolean isLandscapeWallpaper(Wallpaper w) {
        if (w == null) return false;
        int width = w.getWidth();
        int height = w.getHeight();
        // If dimensions are available from API, use them
        if (width > 0 && height > 0) {
            return width > height;
        }
        // Fallback: check category/title string only if dimensions not available
        if (w.getCategory() != null && w.getCategory().toLowerCase().contains("landscape")) return true;
        if (w.getTitle() != null && w.getTitle().toLowerCase().contains("landscape")) return true;
        return false;
    }

    private void refreshAllSections() {
        List<Wallpaper> portraitList = new ArrayList<>();
        List<Wallpaper> landscapeList = new ArrayList<>();
        Set<String> uniqueUrls = new HashSet<>();

        for (Wallpaper w : allWallpapers) {
            if (w == null || w.getImageUrl() == null || w.isPremium()) continue;
            if (!uniqueUrls.contains(w.getImageUrl())) {
                uniqueUrls.add(w.getImageUrl());
                if (isLandscapeWallpaper(w)) {
                    landscapeList.add(w);
                } else {
                    portraitList.add(w);
                }
            }
        }

        if (bestMonthAdapter != null) bestMonthAdapter.updateData(portraitList);
        if (landscapeAdapter != null) landscapeAdapter.updateData(landscapeList);

        if (premiumAdapter != null) premiumAdapter.updateData(premiumWallpapers);
        if (premiumSectionView != null) {
            premiumSectionView.setVisibility(premiumWallpapers.isEmpty() ? View.GONE : View.VISIBLE);
        }

        if (landscapeSectionView != null) {
            landscapeSectionView.setVisibility(landscapeList.isEmpty() ? View.GONE : View.VISIBLE);
        }

        // Hero carousel uses premium wallpapers
        List<Wallpaper> heroMix = new ArrayList<>();
        Set<String> heroUrls = new HashSet<>();
        for (Wallpaper w : premiumWallpapers) {
            if (w != null && w.getImageUrl() != null && !heroUrls.contains(w.getImageUrl())) {
                heroUrls.add(w.getImageUrl());
                heroMix.add(w);
                if (heroMix.size() >= 6) break;
            }
        }
        setupHeroCarousel(heroMix);
        updateCategoriesSection();
    }

    private void loadAllWallpapers() {
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }
        showLoading(true);
        isLoadingApiData = false;
        isLoadingPremiumData = false;
        loadApiWallpapers();
        loadPremiumWallpapersFromFirebase();
    }

    private void loadApiWallpapers() {
        isLoadingApiData = true;
        AtomicInteger loadingTasks = new AtomicInteger(1);
        List<Wallpaper> tempWallpapers = Collections.synchronizedList(new ArrayList<>());

        apiManager.loadWallpapersFromAllSources(new ApiManager.ApiCallback() {
            @Override
            public void onWallpapersLoaded(List<Wallpaper> wallpapers) {
                synchronized (loadedWallpaperIds) {
                    for (Wallpaper w : wallpapers)
                        if (w != null && w.getId() != null) loadedWallpaperIds.add(w.getId());
                }
                tempWallpapers.addAll(wallpapers);
                isLoadingApiData = false;
                if (loadingTasks.decrementAndGet() == 0) onAllWallpapersLoaded(tempWallpapers);
            }

            @Override
            public void onError(String message) {
                isLoadingMore = false;
                isLoadingApiData = false;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (allWallpapers.isEmpty()) {
                            showLoading(false);
                            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                            if (errorView != null) {
                                errorView.setVisibility(View.VISIBLE);
                                if (errorMessage != null)
                                    errorMessage.setText(getString(R.string.failed_load_trending, message));
                            }
                        } else {
                            Toast.makeText(mContext, getString(R.string.could_not_refresh_trending), Toast.LENGTH_SHORT).show();
                            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        }
                    });
                }
            }

            @Override
            public void onMoreWallpapersLoaded(List<Wallpaper> newWallpapers) {
                isLoadingMore = false;
                handleMoreWallpapers(newWallpapers);
            }
        });
    }

    private void loadPremiumWallpapersFromFirebase() {
        isLoadingPremiumData = true;
        if (premiumValueListener != null) firebasePremiumRef.removeEventListener(premiumValueListener);

        premiumValueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                premiumWallpapers.clear();
                Set<String> uniqueIds = new HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Wallpaper wallpaper = parseWallpaperManually(child);
                    if (wallpaper != null && wallpaper.getId() != null) {
                        if (!uniqueIds.contains(wallpaper.getId())) {
                            uniqueIds.add(wallpaper.getId());
                            premiumWallpapers.add(0, wallpaper);
                        }
                    }
                }
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    refreshAllSections();
                    isLoadingPremiumData = false;
                    checkAllDataLoaded();
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isLoadingPremiumData = false;
                checkAllDataLoaded();
            }
        };
        firebasePremiumRef.orderByChild("addedAt").addValueEventListener(premiumValueListener);
    }

    private Wallpaper parseWallpaperManually(DataSnapshot snapshot) {
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

                if (title == null && isAdded()) title = getString(R.string.premium_wallpaper);
                else if (title == null) title = "Premium Wallpaper";

                if (imageUrl == null) imageUrl = "";
                if (thumbnailUrl == null || thumbnailUrl.isEmpty()) thumbnailUrl = imageUrl;

                if (category == null && isAdded()) category = getString(R.string.premium);
                else if (category == null) category = "Premium";

                if (source == null && isAdded()) source = getString(R.string.firebase_source);
                else if (source == null) source = "Firebase";

                return new Wallpaper(id, imageUrl, thumbnailUrl, title, category, source, photographer, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error for ID: " + snapshot.getKey());
        }
        return null;
    }

    private void checkAllDataLoaded() {
        if (!isLoadingApiData && !isLoadingPremiumData && getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                showLoading(false);
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            });
        }
    }

    private void onAllWallpapersLoaded(List<Wallpaper> loadedWallpapers) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (loadedWallpapers != null && !loadedWallpapers.isEmpty()) {
                allWallpapers.clear();
                allWallpapers.addAll(loadedWallpapers);
                organizeWallpapersByCategory(allWallpapers);
            }
            refreshAllSections();
            checkAllDataLoaded();
        });
    }

    private void organizeWallpapersByCategory(List<Wallpaper> wallpapers) {
        categoryWallpapers.clear();
        for (Wallpaper wallpaper : wallpapers) {
            String category = wallpaper.getCategory();
            // Keep "Premium" in English as an internal API identifier
            if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("Premium")) {
                categoryWallpapers.computeIfAbsent(category, k -> new ArrayList<>()).add(wallpaper);
            }
        }
    }

    private String getLocalizedCategoryName(String apiName) {
        if (apiName == null || !isAdded()) return apiName;
        switch (apiName.toLowerCase()) {
            case "abstract": return getString(R.string.category_abstract);
            case "amoled": return getString(R.string.category_amoled);
            case "nature": return getString(R.string.category_nature);
            case "space": return getString(R.string.category_space);
            case "cities": return getString(R.string.category_cities);
            case "animals": return getString(R.string.category_animals);
            case "cars": return getString(R.string.category_cars);
            case "anime": return getString(R.string.category_anime);
            case "landscape": return getString(R.string.category_landscape);
            case "premium": return getString(R.string.premium);
            default: return apiName.substring(0, 1).toUpperCase() + apiName.substring(1).toLowerCase();
        }
    }

    private void updateCategoriesSection() {
        List<CategoryItem> categories = new ArrayList<>();

        if (!premiumWallpapers.isEmpty() && isAdded()) {
            categories.add(new CategoryItem(getString(R.string.premium), premiumWallpapers.get(0).getImageUrl()));
        }

        Map<String, String> masterCategories = new LinkedHashMap<>();
        masterCategories.put("Abstract", "https://images.unsplash.com/photo-1541701494587-cb58502866ab?w=800&fit=crop");
        masterCategories.put("Amoled", "https://images.unsplash.com/photo-1614732414444-096e5f1122d5?w=800&fit=crop");
        masterCategories.put("Nature", "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=800&fit=crop");
        masterCategories.put("Space", "https://images.unsplash.com/photo-1462331940025-496dfbfc7564?w=800&fit=crop");
        masterCategories.put("Cities", "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=800&fit=crop");
        masterCategories.put("Animals", "https://images.unsplash.com/photo-1474511320723-9a56873867b5?w=800&fit=crop");
        masterCategories.put("Cars", "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?w=800&fit=crop");
        masterCategories.put("Anime", "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=800&fit=crop");

        for (Map.Entry<String, List<Wallpaper>> entry : categoryWallpapers.entrySet()) {
            String rawName = entry.getKey();
            List<Wallpaper> walls = entry.getValue();

            if (rawName.equalsIgnoreCase("Landscape") || rawName.equalsIgnoreCase("Premium")) continue;

            if (!walls.isEmpty()) {
                String displayName = rawName.substring(0, 1).toUpperCase() + rawName.substring(1).toLowerCase();

                if (masterCategories.containsKey(displayName)) {
                    masterCategories.put(displayName, walls.get(0).getImageUrl());
                } else {
                    categories.add(new CategoryItem(getLocalizedCategoryName(displayName), walls.get(0).getImageUrl()));
                }
            }
        }

        for (Map.Entry<String, String> entry : masterCategories.entrySet()) {
            categories.add(new CategoryItem(getLocalizedCategoryName(entry.getKey()), entry.getValue()));
        }

        if (categoryGridAdapter != null) {
            categoryGridAdapter.updateData(categories);
        }
    }

    private void handleMoreWallpapers(List<Wallpaper> newWallpapers) {
        if (newWallpapers == null || newWallpapers.isEmpty()) return;
        List<Wallpaper> filtered = new ArrayList<>();
        synchronized (loadedWallpaperIds) {
            for (Wallpaper w : newWallpapers) {
                if (w != null && w.getId() != null && !loadedWallpaperIds.contains(w.getId())) {
                    filtered.add(w);
                    loadedWallpaperIds.add(w.getId());
                }
            }
        }
        if (filtered.isEmpty()) return;
        allWallpapers.addAll(filtered);
        organizeWallpapersByCategory(allWallpapers);
        if (getActivity() != null) getActivity().runOnUiThread(this::refreshAllSections);
    }

    private void onWallpaperClick(Wallpaper wallpaper) { WallpaperDetailsActivity.start(mContext, wallpaper); }
    private void onColorClick(String color) { CategoryActivity.startWithColorFilter(requireContext(), color, color); }
    private void onCategoryClick(CategoryItem category) { if (category != null) CategoryActivity.start(mContext, category.getName(), category.getImageUrl()); }

    private void showLoading(boolean loading) {
        boolean hasExistingData = !allWallpapers.isEmpty() || !premiumWallpapers.isEmpty();

        if (progressBar != null) {
            progressBar.setVisibility(loading && !hasExistingData ? View.VISIBLE : View.GONE);
        }

        if (loading && !hasExistingData) {
            if (recyclerBestMonth != null) recyclerBestMonth.setVisibility(View.INVISIBLE);
            if (recyclerColorTone != null) recyclerColorTone.setVisibility(View.INVISIBLE);
            if (recyclerCategories != null) recyclerCategories.setVisibility(View.INVISIBLE);
        } else {
            if (recyclerBestMonth != null) recyclerBestMonth.setVisibility(View.VISIBLE);
            if (recyclerColorTone != null) recyclerColorTone.setVisibility(View.VISIBLE);
            if (recyclerCategories != null) recyclerCategories.setVisibility(View.VISIBLE);
        }

        if (errorView != null) errorView.setVisibility(View.GONE);
    }

    public void refreshData() {
        allWallpapers.clear();
        loadedWallpaperIds.clear();
        loadAllWallpapers();
    }

    private static class HeroCarouselAdapter extends RecyclerView.Adapter<HeroCarouselAdapter.VH> {
        private final Context ctx;
        private final List<String> urls;

        HeroCarouselAdapter(Context ctx, List<String> urls) {
            this.ctx = ctx;
            this.urls = urls;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(ctx);
            iv.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new VH(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String url = urls.get(position);
            Glide.with(ctx)
                    .load(url)
                    .apply(new com.bumptech.glide.request.RequestOptions()
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                            .centerCrop())
                    .into(holder.imageView);
            holder.imageView.setOnClickListener(v -> {
                if (ctx instanceof MainActivity) ((MainActivity) ctx).navigateToPremium();
            });
        }

        @Override
        public int getItemCount() { return urls.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView imageView;
            VH(ImageView iv) { super(iv); imageView = iv; }
        }
    }

    private void setupHeroCarousel(List<Wallpaper> wallpapers) {
        if (heroCarousel == null || wallpapers == null || wallpapers.isEmpty()) return;
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < Math.min(6, wallpapers.size()); i++) {
            String url = wallpapers.get(i).getImageUrl();
            if (url != null && !url.isEmpty()) urls.add(url);
        }
        if (urls.isEmpty()) return;
        heroCarouselAdapter = new HeroCarouselAdapter(mContext, urls);
        heroCarousel.setAdapter(heroCarouselAdapter);
        heroDots.removeAllViews();
        for (int i = 0; i < urls.size(); i++) {
            View dot = new View(mContext);
            int size = dpToPx(6);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(dpToPx(3), 0, dpToPx(3), 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i == 0 ? R.drawable.dot_active : R.drawable.dot_inactive);
            heroDots.addView(dot);
        }
        heroCarousel.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentHeroPage = position;
                for (int i = 0; i < heroDots.getChildCount(); i++) {
                    heroDots.getChildAt(i).setBackgroundResource(
                            i == position ? R.drawable.dot_active : R.drawable.dot_inactive);
                }
            }
        });
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
        autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL_MS);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
        if (premiumValueListener != null) {
            firebasePremiumRef.removeEventListener(premiumValueListener);
            premiumValueListener = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (heroCarouselAdapter != null && heroCarouselAdapter.getItemCount() > 0) {
            autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL_MS);
        }
    }
}