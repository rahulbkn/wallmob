package com.wall.mob;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeSectionsFragment extends Fragment {
    private static final String TAG = "HomeSectionsFragment";

    private RecyclerView recyclerBestMonth;
    private RecyclerView recyclerLandscape;
    private RecyclerView recyclerColorTone;
    private RecyclerView recyclerCategories;
    private List<Wallpaper> allPortraitWallpapers = new ArrayList<>();

    private View premiumSectionView;
    private RecyclerView recyclerPremium;

    private View landscapeSectionView;
    private TextView tvSeeAllTrending;

    private BestMonthAdapter bestMonthAdapter;
    private LandscapeAdapter landscapeAdapter;
    private ColorToneAdapter colorToneAdapter;
    private CategoryGridAdapter categoryGridAdapter;
    private BestMonthAdapter premiumAdapter;
    private BestMonthAdapter recentAdapter;

    private View recentSection;
    private RecyclerView recyclerRecent;
    private TextView tvClearRecent;

    private ApiManager apiManager;
    private List<Wallpaper> allWallpapers = new ArrayList<>();
    private List<Wallpaper> premiumWallpapers = new ArrayList<>();
    private Map<String, List<Wallpaper>> categoryWallpapers = new HashMap<>();
    private Set<String> loadedWallpaperIds = new HashSet<>();
    private Context mContext;

    private boolean isLoadingMore = false;

    private RecyclerView recyclerNasa;
    private View nasaSectionView;
    private NasaApodAdapter nasaApodAdapter;
    private List<Wallpaper> nasaWallpapers = new ArrayList<>();

    private TabLayout tabLayout;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.home_sections, container, false);
        initializeViews(view);
        setupRecyclerViews();
        return view;
    }

    private void initializeViews(View view) {
        recyclerBestMonth = view.findViewById(R.id.recycler_best_month);
        recyclerLandscape = view.findViewById(R.id.recycler_landscape);
        landscapeSectionView = view.findViewById(R.id.landscape_section);
        recyclerColorTone = view.findViewById(R.id.recycler_color_tone);
        recyclerCategories = view.findViewById(R.id.recycler_categories);
        premiumSectionView = view.findViewById(R.id.premium_section);
        recyclerPremium = view.findViewById(R.id.recycler_premium);
        tvSeeAllTrending = view.findViewById(R.id.tv_see_all_trending);
        TextView tvUnlockAll = view.findViewById(R.id.tv_unlock_all_premium);

        if (tvUnlockAll != null) tvUnlockAll.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).navigateToPremium();
        });

        if (tvSeeAllTrending != null) tvSeeAllTrending.setOnClickListener(v ->
                SeeAllActivity.start(requireContext(), getString(R.string.trending_now), SeeAllActivity.TYPE_TRENDING));

        ImageView tvShuffle = view.findViewById(R.id.tv_shuffle);
        if (tvShuffle != null) tvShuffle.setOnClickListener(v -> shuffleTrending());

        recyclerNasa = view.findViewById(R.id.recycler_nasa);
        nasaSectionView = view.findViewById(R.id.nasa_section);

        recentSection = view.findViewById(R.id.recent_section);
        recyclerRecent = view.findViewById(R.id.recycler_recent);
        tvClearRecent = view.findViewById(R.id.tv_clear_recent);

        tabLayout = view.findViewById(R.id.tab_layout);
        setupTabLayout();
    }

    private void setupTabLayout() {
        if (tabLayout == null) return;
        tabLayout.addTab(tabLayout.newTab().setText("For You"));
        tabLayout.addTab(tabLayout.newTab().setText("Trending"));
        tabLayout.addTab(tabLayout.newTab().setText("Premium"));
        tabLayout.addTab(tabLayout.newTab().setText("Categories"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                scrollToSection(tab.getPosition());
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                scrollToSection(tab.getPosition());
            }
        });
    }

    private void scrollToSection(int position) {
        View rootView = getView();
        if (rootView == null) return;
        NestedScrollView parentScroll = getActivity() != null
                ? getActivity().findViewById(R.id.main_scroll_view) : null;
        if (parentScroll == null) return;

        int heroHeight = 0;
        android.view.View heroContainer = getActivity() != null
                ? getActivity().findViewById(R.id.hero_container) : null;
        if (heroContainer != null) heroHeight = heroContainer.getHeight();

        int targetScrollY = 0;
        switch (position) {
            case 0:
                targetScrollY = 0;
                break;
            case 1:
                View trending = rootView.findViewById(R.id.recycler_best_month);
                if (trending != null) targetScrollY = trending.getTop() + heroHeight;
                break;
            case 2:
                View premium = rootView.findViewById(R.id.premium_section);
                if (premium != null) targetScrollY = premium.getTop() + heroHeight;
                break;
            case 3:
                View categories = rootView.findViewById(R.id.recycler_categories);
                if (categories != null) targetScrollY = categories.getTop() + heroHeight;
                break;
        }
        parentScroll.smoothScrollTo(0, Math.max(0, targetScrollY - dpToPx(48)));
    }

    private void setupRecyclerViews() {
        if (mContext == null) return;

        LinearLayoutManager bestMonthLayout = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        recyclerBestMonth.setLayoutManager(bestMonthLayout);
        recyclerBestMonth.setNestedScrollingEnabled(false);
        bestMonthAdapter = new BestMonthAdapter(mContext, new ArrayList<>(), this::onWallpaperClick);
        recyclerBestMonth.setAdapter(bestMonthAdapter);

        LinearLayoutManager premiumLayout = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        recyclerPremium.setLayoutManager(premiumLayout);
        recyclerPremium.setNestedScrollingEnabled(false);
        premiumAdapter = new BestMonthAdapter(mContext, new ArrayList<>(), this::onWallpaperClick);
        recyclerPremium.setAdapter(premiumAdapter);

        LinearLayoutManager landscapeLayout = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        recyclerLandscape.setLayoutManager(landscapeLayout);
        recyclerLandscape.setNestedScrollingEnabled(false);
        landscapeAdapter = new LandscapeAdapter(mContext, new ArrayList<>(), this::onWallpaperClick);
        recyclerLandscape.setAdapter(landscapeAdapter);

        LinearLayoutManager colorToneLayout = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        recyclerColorTone.setLayoutManager(colorToneLayout);
        recyclerColorTone.setNestedScrollingEnabled(false);
        List<String> colors = Arrays.asList("#FF0000", "#FF7F00", "#FFD700", "#32CD32", "#40E0D0", "#4169E1", "#4B0082", "#8B00FF", "#FF1493", "#2C2C2C");
        colorToneAdapter = new ColorToneAdapter(mContext, colors, this::onColorClick);
        recyclerColorTone.setAdapter(colorToneAdapter);

        if (recyclerRecent != null) {
            LinearLayoutManager recentLayout = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
            recyclerRecent.setLayoutManager(recentLayout);
            recyclerRecent.setNestedScrollingEnabled(false);
            recentAdapter = new BestMonthAdapter(mContext, new ArrayList<>(), this::onWallpaperClick);
            recyclerRecent.setAdapter(recentAdapter);
        }

        if (tvClearRecent != null) {
            tvClearRecent.setOnClickListener(v -> {
                RecentWallpapersManager.clearRecents(requireContext());
                updateRecentSection();
            });
        }

        int span = 2;
        GridLayoutManager categoryLayout = new GridLayoutManager(mContext, span);
        recyclerCategories.setLayoutManager(categoryLayout);
        recyclerCategories.setNestedScrollingEnabled(false);
        recyclerCategories.addItemDecoration(new GridSpacingItemDecoration(span, dpToPx(2)));
        categoryGridAdapter = new CategoryGridAdapter(mContext, new ArrayList<>(), this::onCategoryClick);
        recyclerCategories.setAdapter(categoryGridAdapter);

        if (recyclerNasa != null) {
            LinearLayoutManager nasaLayout = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
            recyclerNasa.setLayoutManager(nasaLayout);
            recyclerNasa.setNestedScrollingEnabled(false);
            nasaApodAdapter = new NasaApodAdapter(mContext, new ArrayList<>(), this::onWallpaperClick);
            recyclerNasa.setAdapter(nasaApodAdapter);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private boolean isLandscapeWallpaper(Wallpaper w) {
        if (w == null) return false;
        int width = w.getWidth();
        int height = w.getHeight();
        if (width > 0 && height > 0) {
            return width > height;
        }
        if (w.getCategory() != null && w.getCategory().toLowerCase().contains("landscape")) return true;
        if (w.getTitle() != null && w.getTitle().toLowerCase().contains("landscape")) return true;
        return false;
    }

    public void refreshAllSectionsPublic() {
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

        allPortraitWallpapers = portraitList;
        if (bestMonthAdapter != null) bestMonthAdapter.updateData(portraitList);

        if (landscapeAdapter != null) landscapeAdapter.updateData(landscapeList);

        if (premiumAdapter != null) premiumAdapter.updateData(premiumWallpapers);
        if (premiumSectionView != null) {
            premiumSectionView.setVisibility(premiumWallpapers.isEmpty() ? View.GONE : View.VISIBLE);
        }

        if (landscapeSectionView != null) {
            landscapeSectionView.setVisibility(landscapeList.isEmpty() ? View.GONE : View.VISIBLE);
        }

        updateCategoriesSection();
        updateRecentSection();
    }

    private void updateCategoriesSection() {
        List<CategoryItem> categories = new ArrayList<>();

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

            if (rawName.equalsIgnoreCase("Landscape")) continue;

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
            case "premium": return getString(R.string.others);
            default: return apiName.substring(0, 1).toUpperCase() + apiName.substring(1).toLowerCase();
        }
    }

    private void shuffleTrending() {
        if (allPortraitWallpapers.size() > 1) {
            List<Wallpaper> shuffled = new ArrayList<>(allPortraitWallpapers);
            Collections.shuffle(shuffled);
            if (bestMonthAdapter != null) bestMonthAdapter.updateData(shuffled);
            Toast.makeText(mContext, "Trending shuffled!", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRecentSection() {
        if (mContext == null) return;
        List<Wallpaper> recents = RecentWallpapersManager.getRecents(mContext);
        if (recentAdapter != null) recentAdapter.updateData(recents);
        if (recentSection != null) {
            recentSection.setVisibility(recents.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void onWallpaperClick(Wallpaper wallpaper) {
        if (wallpaper != null && isAdded() && getActivity() != null) {
            WallpaperDetailsActivity.start(getActivity(), wallpaper);
        }
    }

    private void onColorClick(String color) {
        if (color != null && isAdded() && getActivity() != null) {
            CategoryActivity.startWithColorFilter(getActivity(), color, color);
        }
    }

    private void onCategoryClick(CategoryItem category) {
        if (category != null && isAdded() && getActivity() != null) {
            CategoryActivity.start(getActivity(), category.getName(), category.getImageUrl());
        }
    }

    public void refreshData() {
        allWallpapers.clear();
        loadedWallpaperIds.clear();
        loadNasaApod();
    }

    public void setApiManager(ApiManager manager) {
        apiManager = manager;
    }

    public void setAllWallpapers(List<Wallpaper> wallpapers) {
        if (wallpapers != null) {
            allWallpapers.clear();
            allWallpapers.addAll(wallpapers);
            organizeWallpapersByCategory(allWallpapers);
        }
    }

    public void setPremiumWallpapers(List<Wallpaper> wallpapers) {
        if (wallpapers != null) {
            premiumWallpapers.clear();
            premiumWallpapers.addAll(wallpapers);
        }
    }

    private void organizeWallpapersByCategory(List<Wallpaper> wallpapers) {
        categoryWallpapers.clear();
        for (Wallpaper wallpaper : wallpapers) {
            String category = wallpaper.getCategory();
            if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("Premium")) {
                categoryWallpapers.computeIfAbsent(category, k -> new ArrayList<>()).add(wallpaper);
            } else {
                categoryWallpapers.computeIfAbsent("Others", k -> new ArrayList<>()).add(wallpaper);
            }
        }
    }

    private void loadNasaApod() {
        if (mContext == null) return;

        String url = "https://api.nasa.gov/planetary/apod"
                + "?api_key=6QPKzAjSBthigZmCIBTrtHgEPXHTr95ECW1f3r5m"
                + "&count=15"
                + "&thumbs=true";

        Log.d(TAG, "NASA APOD URL: " + url);

        com.android.volley.toolbox.JsonArrayRequest request = new com.android.volley.toolbox.JsonArrayRequest(
                com.android.volley.Request.Method.GET, url, null,
                response -> {
                    nasaWallpapers.clear();
                    for (int i = 0; i < response.length(); i++) {
                        try {
                            org.json.JSONObject item = response.getJSONObject(i);
                            String mediaType = item.optString("media_type", "image");
                            if (!mediaType.equals("image")) continue;

                            String imageUrl = item.optString("hdurl", item.optString("url", ""));
                            String thumbUrl = item.optString("url", imageUrl);
                            if (imageUrl.isEmpty()) continue;

                            String date = item.optString("date", "");
                            String title = item.optString("title", "NASA APOD");
                            String copyright = item.optString("copyright", "NASA");

                            Wallpaper w = new Wallpaper(
                                    "nasa-" + date,
                                    imageUrl,
                                    thumbUrl,
                                    title,
                                    "Space",
                                    "NASA",
                                    copyright,
                                    false
                            );
                            nasaWallpapers.add(w);
                        } catch (org.json.JSONException e) {
                            Log.e(TAG, "NASA parse error at " + i, e);
                        }
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (nasaApodAdapter != null) {
                                nasaApodAdapter.updateData(nasaWallpapers);
                            }
                            if (nasaSectionView != null) {
                                nasaSectionView.setVisibility(nasaWallpapers.isEmpty() ? View.GONE : View.VISIBLE);
                            }
                        });
                    }
                },
                error -> {
                    Log.e(TAG, "NASA APOD error: " + (error.getMessage() != null ? error.getMessage() : "unknown"));
                    if (nasaSectionView != null && getActivity() != null) {
                        getActivity().runOnUiThread(() -> nasaSectionView.setVisibility(View.GONE));
                    }
                }
        ) {
            @Override
            public com.android.volley.Request.Priority getPriority() {
                return com.android.volley.Request.Priority.LOW;
            }
        };

        com.android.volley.RequestQueue queue = com.android.volley.toolbox.Volley.newRequestQueue(mContext);
        queue.add(request);
    }
}