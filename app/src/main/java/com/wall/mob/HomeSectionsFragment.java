package com.wall.mob;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeSectionsFragment extends Fragment {
    private static final String TAG = "HomeSectionsFragment";

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private SectionsPagerAdapter pagerAdapter;
    private HomeTabContentFragment homeTabContentFragment;

    private ApiManager apiManager;
    private List<Wallpaper> allWallpapers = new ArrayList<>();
    private List<Wallpaper> premiumWallpapers = new ArrayList<>();
    private Map<String, List<Wallpaper>> categoryWallpapers = new HashMap<>();
    private List<Wallpaper> nasaWallpapers = new ArrayList<>();
    private Context mContext;

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
        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.sections_viewpager);

        WrapContentFrameLayout wrapper = view.findViewById(R.id.viewpager_wrapper);
        if (wrapper != null) {
            wrapper.attachToViewPager(viewPager);
        }

        setupViewPager();
        return view;
    }

    private void setupViewPager() {
        pagerAdapter = new SectionsPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            View customView = LayoutInflater.from(mContext).inflate(R.layout.custom_tab_item, null);
            ImageView icon = customView.findViewById(R.id.tab_icon);
            TextView text = customView.findViewById(R.id.tab_text);

            switch (position) {
                case 0:
                    text.setText(getString(R.string.tab_for_you));
                    icon.setImageResource(R.drawable.ic_foryou);
                    break;
                case 1:
                    text.setText(getString(R.string.tab_newlyadded));
                    icon.setImageResource(R.drawable.ic_recent);
                    break;
                case 2:
                    text.setText(getString(R.string.tab_popular));
                    icon.setImageResource(R.drawable.ic_star);
                    break;
                case 3:
                    text.setText(getString(R.string.tab_categories));
                    icon.setImageResource(R.drawable.ic_category);
                    break;
            }
            icon.setImageTintList(ContextCompat.getColorStateList(mContext, R.color.tab_icon_selector));
            tab.setCustomView(customView);
        }).attach();

        loadNasaApod();
    }

    public void refreshAllSectionsPublic() {
        final List<Wallpaper> allWallpapersCopy = new ArrayList<>(allWallpapers);
        final List<Wallpaper> premiumWallpapersCopy = new ArrayList<>(premiumWallpapers);
        final Map<String, List<Wallpaper>> categoryWallpapersCopy = new HashMap<>(categoryWallpapers);
        final List<Wallpaper> nasaWallpapersCopy = new ArrayList<>(nasaWallpapers);

        new Thread(() -> {
            List<Wallpaper> portraitList = new ArrayList<>();
            List<Wallpaper> landscapeList = new ArrayList<>();
            Set<String> uniqueUrls = new HashSet<>();

            for (Wallpaper w : allWallpapersCopy) {
                if (w == null || w.getImageUrl() == null || w.isPremium()) continue;
                if (!uniqueUrls.contains(w.getImageUrl())) {
                    uniqueUrls.add(w.getImageUrl());
                    if (w.getWidth() > 0 && w.getHeight() > 0 && w.getWidth() > w.getHeight()) {
                        landscapeList.add(w);
                    } else {
                        portraitList.add(w);
                    }
                }
            }

            List<CategoryItem> categories = buildCategoriesList(categoryWallpapersCopy);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (homeTabContentFragment != null) {
                        homeTabContentFragment.updateDashboardData(portraitList, landscapeList, premiumWallpapersCopy, categories, nasaWallpapersCopy);
                    }
                });
            }
        }).start();
    }

    private List<CategoryItem> buildCategoriesList(Map<String, List<Wallpaper>> categoryWallpapersMap) {
        List<CategoryItem> categories = new ArrayList<>();
        Map<String, String> masterCategories = new LinkedHashMap<>();
        masterCategories.put("Abstract", "https://images.unsplash.com/photo-1541701494587-cb58502866ab?w=800&fit=crop");
        masterCategories.put("Amoled", "https://images.unsplash.com/photo-1614732414444-096e5f1122d5?w=800&fit=crop");
        masterCategories.put("Nature", "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=800&fit=crop");
        masterCategories.put("Mountains", "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=800&fit=crop");
        masterCategories.put("Flowers", "https://images.unsplash.com/photo-1490750967868-88aa4f44baee?w=800&fit=crop");
        masterCategories.put("Space", "https://images.unsplash.com/photo-1462331940025-496dfbfc7564?w=800&fit=crop");
        masterCategories.put("Cities", "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=800&fit=crop");
        masterCategories.put("Animals", "https://images.unsplash.com/photo-1474511320723-9a56873867b5?w=800&fit=crop");
        masterCategories.put("Cars", "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?w=800&fit=crop");
        masterCategories.put("Anime", "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=800&fit=crop");
        masterCategories.put("Cartoons", "https://images.unsplash.com/photo-1560472355-536de3962603?w=800&fit=crop");
        masterCategories.put("Cyberpunk", "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=800&fit=crop");
        masterCategories.put("Dark", "https://images.unsplash.com/photo-1550684376-efcbd6e3f031?w=800&fit=crop");
        masterCategories.put("Neon", "https://images.unsplash.com/photo-1553356084-58ef4a7b7987?w=800&fit=crop");
        masterCategories.put("Minimal", "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800&fit=crop");
        masterCategories.put("Beach", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&fit=crop");
        masterCategories.put("Ocean", "https://images.unsplash.com/photo-1505118380757-91f5f5632de0?w=800&fit=crop");
        masterCategories.put("Galaxy", "https://images.unsplash.com/photo-1462332420958-a05d1e002413?w=800&fit=crop");
        masterCategories.put("Fantasy", "https://images.unsplash.com/photo-1518709766631-a6a7f45921c3?w=800&fit=crop");
        masterCategories.put("Gaming", "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?w=800&fit=crop");
        masterCategories.put("Food", "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800&fit=crop");
        masterCategories.put("Music", "https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=800&fit=crop");
        masterCategories.put("Architecture", "https://images.unsplash.com/photo-1487958449943-2429e8be8625?w=800&fit=crop");
        masterCategories.put("Art", "https://images.unsplash.com/photo-1513364776144-60967b0f800f?w=800&fit=crop");
        masterCategories.put("Travel", "https://images.unsplash.com/photo-1488646953014-85cb44e25828?w=800&fit=crop");
        masterCategories.put("Sports", "https://images.unsplash.com/photo-1461896836934-bd45ba8fcf9b?w=800&fit=crop");
        masterCategories.put("Technology", "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&fit=crop");
        masterCategories.put("Vintage", "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=800&fit=crop");
        masterCategories.put("Aesthetic", "https://images.unsplash.com/photo-1549490349-8643362247b5?w=800&fit=crop");
        masterCategories.put("Forest", "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=800&fit=crop");

        for (Map.Entry<String, List<Wallpaper>> entry : categoryWallpapersMap.entrySet()) {
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
        return categories;
    }

    private String getLocalizedCategoryName(String apiName) {
        if (apiName == null || !isAdded()) return apiName;
        switch (apiName.toLowerCase()) {
            case "abstract": return getString(R.string.category_abstract);
            case "amoled": return getString(R.string.category_amoled);
            case "nature": return getString(R.string.category_nature);
            case "mountains": return getString(R.string.category_mountains);
            case "flowers": return getString(R.string.category_flowers);
            case "space": return getString(R.string.category_space);
            case "cities": return getString(R.string.category_cities);
            case "animals": return getString(R.string.category_animals);
            case "cars": return getString(R.string.category_cars);
            case "anime": return getString(R.string.category_anime);
            case "cartoons": return getString(R.string.category_cartoons);
            case "cyberpunk": return getString(R.string.category_cyberpunk);
            case "dark": return getString(R.string.category_dark);
            case "neon": return getString(R.string.category_neon);
            case "minimal": return getString(R.string.category_minimal);
            case "beach": return getString(R.string.category_beach);
            case "ocean": return getString(R.string.category_ocean);
            case "galaxy": return getString(R.string.category_galaxy);
            case "fantasy": return getString(R.string.category_fantasy);
            case "gaming": return getString(R.string.category_gaming);
            case "music": return getString(R.string.category_music);
            case "food": return getString(R.string.category_food);
            case "forest": return getString(R.string.category_forest);
            case "architecture": return getString(R.string.category_architecture);
            case "art": return getString(R.string.category_art);
            case "sports": return getString(R.string.category_sports);
            case "travel": return getString(R.string.category_travel);
            case "technology": return getString(R.string.category_technology);
            case "vintage": return getString(R.string.category_vintage);
            case "aesthetic": return getString(R.string.category_aesthetic);
            default: return apiName;
        }
    }

    public void refreshData() {
        allWallpapers.clear();
        loadNasaApod();
        if (homeTabContentFragment != null) homeTabContentFragment.updateRecentSection();
    }

    public void setApiManager(ApiManager manager) { this.apiManager = manager; }

    public void setAllWallpapers(List<Wallpaper> wallpapers) {
        if (wallpapers != null) {
            allWallpapers.clear();
            allWallpapers.addAll(wallpapers);
            categoryWallpapers.clear();
            for (Wallpaper wallpaper : wallpapers) {
                String cat = wallpaper.getCategory();
                String key = (cat != null && !cat.trim().isEmpty() && !cat.equalsIgnoreCase("Premium")) ? cat : "Others";
                categoryWallpapers.computeIfAbsent(key, k -> new ArrayList<>()).add(wallpaper);
            }
        }
    }

    public void setAllWallpapers(List<Wallpaper> wallpapers, Map<String, List<Wallpaper>> preBuiltCategories) {
        if (wallpapers != null) {
            allWallpapers.clear();
            allWallpapers.addAll(wallpapers);
        }
        if (preBuiltCategories != null) {
            categoryWallpapers.clear();
            categoryWallpapers.putAll(preBuiltCategories);
        }
    }

    public void setPremiumWallpapers(List<Wallpaper> wallpapers) {
        if (wallpapers != null) {
            premiumWallpapers.clear();
            premiumWallpapers.addAll(wallpapers);
        }
    }

    private void loadNasaApod() {
        if (mContext == null) return;
        String url = "https://api.nasa.gov/planetary/apod?api_key=6QPKzAjSBthigZmCIBTrtHgEPXHTr95ECW1f3r5m&count=15&thumbs=true";
        com.android.volley.toolbox.JsonArrayRequest request = new com.android.volley.toolbox.JsonArrayRequest(
                com.android.volley.Request.Method.GET, url, null,
                response -> {
                    nasaWallpapers.clear();
                    for (int i = 0; i < response.length(); i++) {
                        try {
                            org.json.JSONObject item = response.getJSONObject(i);
                            if (!item.optString("media_type", "image").equals("image")) continue;
                            String imageUrl = item.optString("hdurl", item.optString("url", ""));
                            if (imageUrl.isEmpty()) continue;
                            nasaWallpapers.add(new Wallpaper("nasa-" + item.optString("date", ""), imageUrl, item.optString("url", imageUrl), item.optString("title", "NASA APOD"), "Space", "NASA", item.optString("copyright", "NASA"), false));
                        } catch (org.json.JSONException e) { Log.e(TAG, "NASA parse error", e); }
                    }
                    if (getActivity() != null) getActivity().runOnUiThread(this::refreshAllSectionsPublic);
                },
                error -> Log.e(TAG, "NASA APOD error")
        );
        com.android.volley.toolbox.Volley.newRequestQueue(mContext).add(request);
    }

    private class SectionsPagerAdapter extends FragmentStateAdapter {
        public SectionsPagerAdapter(@NonNull Fragment fragment) { super(fragment); }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    homeTabContentFragment = new HomeTabContentFragment();
                    return homeTabContentFragment;
                case 1:
                    return new NewlyTabFragment(); // Replace with your standalone layouts later
                case 2:
                    return new PopularTabFragment();  // Replace with your standalone layouts later
                default:
                    return new CategoriesTabFragment(); // Replace with your standalone layouts later
            }
        }

        @Override
        public int getItemCount() { return 4; }
    }
}
