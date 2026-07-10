package com.wall.mob;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HomeTabContentFragment extends Fragment {

    private RecyclerView recyclerBestMonth, recyclerLandscape, recyclerColorTone, recyclerCategories, recyclerPremium, recyclerRecent, recyclerNasa;
    private View premiumSectionView, landscapeSectionView, recentSection, nasaSectionView;
    private TextView tvSeeAllTrending, tvClearRecent;

    private BestMonthAdapter bestMonthAdapter, premiumAdapter, recentAdapter;
    private LandscapeAdapter landscapeAdapter;
    private ColorToneAdapter colorToneAdapter;
    private CategoryGridAdapter categoryGridAdapter;
    private NasaApodAdapter nasaApodAdapter;

    private List<Wallpaper> currentPortraitList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_tab_content, container, false);
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
        ImageView tvShuffle = view.findViewById(R.id.tv_shuffle);
        recyclerNasa = view.findViewById(R.id.recycler_nasa);
        nasaSectionView = view.findViewById(R.id.nasa_section);
        recentSection = view.findViewById(R.id.recent_section);
        recyclerRecent = view.findViewById(R.id.recycler_recent);
        tvClearRecent = view.findViewById(R.id.tv_clear_recent);

        if (tvUnlockAll != null) {
            tvUnlockAll.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).navigateToPremium();
            });
        }
        if (tvSeeAllTrending != null) {
            tvSeeAllTrending.setOnClickListener(v ->
                    SeeAllActivity.start(requireContext(), getString(R.string.trending_now), SeeAllActivity.TYPE_TRENDING));
        }
        if (tvShuffle != null) tvShuffle.setOnClickListener(v -> shuffleTrending());
        if (tvClearRecent != null) {
            tvClearRecent.setOnClickListener(v -> {
                RecentWallpapersManager.clearRecents(requireContext());
                updateRecentSection();
            });
        }
    }

    private void setupRecyclerViews() {
    Context context = requireContext();

    // 1. Trending / Best Month Section
    recyclerBestMonth.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
    bestMonthAdapter = new BestMonthAdapter(context, new ArrayList<>(), this::onWallpaperClick);
    recyclerBestMonth.setAdapter(bestMonthAdapter);
    ViewPager2ConflictResolver.attach(recyclerBestMonth); // <-- FIX ADDED HERE

    // 2. Premium Section
    recyclerPremium.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
    premiumAdapter = new BestMonthAdapter(context, new ArrayList<>(), this::onWallpaperClick);
    recyclerPremium.setAdapter(premiumAdapter);
    ViewPager2ConflictResolver.attach(recyclerPremium); // <-- FIX ADDED HERE

    // 3. Landscape Section
    recyclerLandscape.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
    landscapeAdapter = new LandscapeAdapter(context, new ArrayList<>(), this::onWallpaperClick);
    recyclerLandscape.setAdapter(landscapeAdapter);
    ViewPager2ConflictResolver.attach(recyclerLandscape); // <-- FIX ADDED HERE

    // 4. Color Palettes Section
    recyclerColorTone.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
    List<String> colors = Arrays.asList("#FF0000", "#FF7F00", "#FFD700", "#32CD32", "#40E0D0", "#4169E1", "#4B0082", "#8B00FF", "#FF1493", "#2C2C2C");
    colorToneAdapter = new ColorToneAdapter(context, colors, this::onColorClick);
    recyclerColorTone.setAdapter(colorToneAdapter);
    ViewPager2ConflictResolver.attach(recyclerColorTone); // <-- FIX ADDED HERE

    // 5. Recent Section
    recyclerRecent.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
    recentAdapter = new BestMonthAdapter(context, new ArrayList<>(), this::onWallpaperClick);
    recyclerRecent.setAdapter(recentAdapter);
    ViewPager2ConflictResolver.attach(recyclerRecent); // <-- FIX ADDED HERE

    // 6. NASA Section
    recyclerNasa.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
    nasaApodAdapter = new NasaApodAdapter(context, new ArrayList<>(), this::onWallpaperClick);
    recyclerNasa.setAdapter(nasaApodAdapter);
    ViewPager2ConflictResolver.attach(recyclerNasa); // <-- FIX ADDED HERE

    // (Note: Categories Section uses GridLayoutManager which scrolls vertically, 
    // so it will not cause horizontal ViewPager swiping conflicts)
    recyclerCategories.setLayoutManager(new GridLayoutManager(context, 2));
    recyclerCategories.addItemDecoration(new GridSpacingItemDecoration(2, Math.round(2 * getResources().getDisplayMetrics().density)));
    categoryGridAdapter = new CategoryGridAdapter(context, new ArrayList<>(), this::onCategoryClick);
    recyclerCategories.setAdapter(categoryGridAdapter);
}


    public void updateDashboardData(List<Wallpaper> portrait, List<Wallpaper> landscape, List<Wallpaper> premium, List<CategoryItem> categories, List<Wallpaper> nasa) {
        currentPortraitList = portrait;
        if (bestMonthAdapter != null) bestMonthAdapter.updateData(portrait);
        if (landscapeAdapter != null) landscapeAdapter.updateData(landscape);
        if (premiumAdapter != null) premiumAdapter.updateData(premium);
        if (categoryGridAdapter != null) categoryGridAdapter.updateData(categories);
        if (nasaApodAdapter != null) nasaApodAdapter.updateData(nasa);

        if (premiumSectionView != null) premiumSectionView.setVisibility(premium.isEmpty() ? View.GONE : View.VISIBLE);
        if (landscapeSectionView != null) landscapeSectionView.setVisibility(landscape.isEmpty() ? View.GONE : View.VISIBLE);
        if (nasaSectionView != null) nasaSectionView.setVisibility(nasa.isEmpty() ? View.GONE : View.VISIBLE);

        updateRecentSection();
    }

    public void updateRecentSection() {
        if (getContext() == null) return;
        Context ctx = getContext();
        SketchApplication.getIoExecutor().execute(() -> {
            List<Wallpaper> recents = RecentWallpapersManager.getRecents(ctx);
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (recentAdapter != null) recentAdapter.updateData(recents);
                if (recentSection != null) recentSection.setVisibility(recents.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void shuffleTrending() {
        if (currentPortraitList.size() > 1) {
            List<Wallpaper> shuffled = new ArrayList<>(currentPortraitList);
            Collections.shuffle(shuffled);
            if (bestMonthAdapter != null) bestMonthAdapter.updateData(shuffled);
            Toast.makeText(getContext(), "Trending shuffled!", Toast.LENGTH_SHORT).show();
        }
    }

    private void onWallpaperClick(Wallpaper wallpaper) {
        if (wallpaper != null && isAdded()) WallpaperDetailsActivity.start(getActivity(), wallpaper);
    }

    private void onColorClick(String color) {
        if (color != null && isAdded()) CategoryActivity.startWithColorFilter(getActivity(), color, color);
    }

    private void onCategoryClick(CategoryItem category) {
        if (category != null && isAdded()) CategoryActivity.start(getActivity(), category.getName(), category.getImageUrl());
    }
}
