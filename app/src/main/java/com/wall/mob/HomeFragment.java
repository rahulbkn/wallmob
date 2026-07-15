package com.wall.mob;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;
import androidx.viewpager2.widget.CompositePageTransformer;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final int PREMIUM_WALLPAPER_LIMIT = 10;
    private FrameLayout heroContainer;
    private TextView heroTitle;

    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerViewContainer;
    private View errorView;
    private Button retryButton;
    private TextView errorMessage;
    private FrameLayout sectionsContainer;

    private ApiManager apiManager;
    private List<Wallpaper> allWallpapers = new ArrayList<>();
    private List<Wallpaper> premiumWallpapers = new ArrayList<>();
    private Map<String, List<Wallpaper>> categoryWallpapers;
    private Set<String> loadedWallpaperIds = new HashSet<>();
    private Context mContext;

    private DatabaseReference firebasePremiumRef;
    private ValueEventListener premiumValueListener;
    private LinearLayout mainContentContainer;

    private boolean isLoadingApiData = false;
    private boolean isLoadingPremiumData = false;
    private boolean isLoadingMore = false;

    private ViewPager2 heroCarousel;
    private LinearLayout heroDots;
    private HeroCarouselAdapter heroCarouselAdapter;
    private Handler autoScrollHandler = new Handler(Looper.getMainLooper());
    private int currentHeroPage = 0;
    private static final int AUTO_SCROLL_INTERVAL_MS = 5000;

    private HomeSectionsFragment sectionsFragment;
    private NestedScrollView nestedScrollView;
    private View cachedTabLayout;
private View cachedTabPlaceholder;
    private ViewGroup stickyTabContainer;
private ViewGroup tabOriginalParent;
private int tabOriginalIndex = -1;
private boolean tabIsSticky = false;

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
        setupSwipeRefresh(view);
        firebasePremiumRef = FirebaseDatabase.getInstance().getReference("wallpapers/premium");
        loadAllWallpapers();
        return view;
    }

    private void initializeViews(View view) {
        nestedScrollView = view.findViewById(R.id.main_scroll_view);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        progressBar = view.findViewById(R.id.progress_bar);
        shimmerViewContainer = view.findViewById(R.id.shimmer_view_container);
        errorView = view.findViewById(R.id.error_view);
        retryButton = view.findViewById(R.id.retry_button);
        errorMessage = view.findViewById(R.id.error_message);
        heroContainer = view.findViewById(R.id.hero_container);
        heroTitle = view.findViewById(R.id.hero_title);
        heroCarousel = view.findViewById(R.id.hero_carousel);
        heroDots = view.findViewById(R.id.hero_dots);
        mainContentContainer = view.findViewById(R.id.main_content_container);

        if (nestedScrollView != null) {
            nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    activity.updateToolbarOnScroll(scrollY);
                    activity.handleScrollDirection(scrollY - oldScrollY);
                }
                updateStickyTab(activity, scrollY);
            });
        }

        apiManager = new ApiManager(requireContext());
        retryButton.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            loadAllWallpapers();
        });

        sectionsContainer = view.findViewById(R.id.sections_container);
        FragmentManager childFm = getChildFragmentManager();
        sectionsFragment = (HomeSectionsFragment) childFm.findFragmentByTag("home_sections");
        if (sectionsFragment == null) {
            sectionsFragment = new HomeSectionsFragment();
            childFm.beginTransaction()
                    .add(R.id.sections_container, sectionsFragment, "home_sections")
                    .commit();
            childFm.executePendingTransactions();
        }
        if (sectionsFragment != null) {
            sectionsFragment.setApiManager(apiManager);
        }
    }

    private void setupSwipeRefresh(View view) {
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

    private void updateStickyTab(MainActivity activity, int scrollY) {
        if (activity == null || getView() == null) return;

        if (cachedTabLayout == null || cachedTabPlaceholder == null) {
            if (sectionsFragment == null || sectionsFragment.getView() == null) return;
            cachedTabLayout = sectionsFragment.getView().findViewById(R.id.tab_layout);
            cachedTabPlaceholder = sectionsFragment.getView().findViewById(R.id.tab_placeholder);
        }
        if (cachedTabLayout == null || cachedTabPlaceholder == null) return;

        View tabLayout = cachedTabLayout;
        View tabPlaceholder = cachedTabPlaceholder;

        if (stickyTabContainer == null) {
            stickyTabContainer = activity.findViewById(R.id.sticky_tab_container);
        }
        if (stickyTabContainer == null) return;

        int toolbarH = 0;
        View tb = activity.findViewById(R.id.toolbar);
        if (tb != null) toolbarH = tb.getHeight();

        int heroH = heroContainer != null ? heroContainer.getHeight() : 0;
        if (heroH == 0) return;

        boolean shouldStick = scrollY > (heroH - toolbarH - dpToPx(25));

        if (shouldStick && !tabIsSticky) {
            tabIsSticky = true;
            tabOriginalParent = (ViewGroup) tabLayout.getParent();
            tabOriginalIndex = tabOriginalParent.indexOfChild(tabLayout);
            tabPlaceholder.getLayoutParams().height = tabLayout.getHeight();
            tabPlaceholder.setVisibility(View.VISIBLE);
            tabOriginalParent.removeView(tabLayout);
            stickyTabContainer.removeAllViews();
            stickyTabContainer.addView(tabLayout);
            stickyTabContainer.setVisibility(View.VISIBLE);
        } else if (!shouldStick && tabIsSticky) {
            tabIsSticky = false;
            stickyTabContainer.removeView(tabLayout);
            stickyTabContainer.setVisibility(View.GONE);
            tabPlaceholder.setVisibility(View.GONE);
            if (tabOriginalParent != null) {
                int index = Math.min(tabOriginalIndex, tabOriginalParent.getChildCount());
                tabOriginalParent.addView(tabLayout, index);
            }
        }
    }

    private void refreshAllSections() {
        final List<Wallpaper> ap = new ArrayList<>(allWallpapers);
        final List<Wallpaper> pp = new ArrayList<>(premiumWallpapers);
        final Context ctx = mContext != null ? mContext : getContext();
        if (ctx == null) return;

        SketchApplication.getIoExecutor().execute(() -> {
            List<Wallpaper> portraitList = new ArrayList<>();
            List<Wallpaper> landscapeList = new ArrayList<>();
            Set<String> uniqueUrls = new HashSet<>();

            for (Wallpaper w : ap) {
                if (w == null || w.getImageUrl() == null || w.isPremium()) continue;
                if (!uniqueUrls.contains(w.getImageUrl())) {
                    uniqueUrls.add(w.getImageUrl());
                    boolean isLandscape = w.getWidth() > 0 && w.getHeight() > 0 && w.getWidth() > w.getHeight();
                    if (!isLandscape) portraitList.add(w);
                    else landscapeList.add(w);
                }
            }

            List<Wallpaper> heroMix = new ArrayList<>();
            Set<String> heroUrls = new HashSet<>();

            Wallpaper wotd = WallpaperOfTheDayManager.getDailyWallpaper(ctx);
            if (wotd != null && wotd.getImageUrl() != null) {
                heroUrls.add(wotd.getImageUrl());
                heroMix.add(wotd);
            }

            for (Wallpaper w : pp) {
                if (w != null && w.getImageUrl() != null && !heroUrls.contains(w.getImageUrl())) {
                    heroUrls.add(w.getImageUrl());
                    heroMix.add(w);
                    if (heroMix.size() >= 6) break;
                }
            }
            if (heroMix.isEmpty() && !portraitList.isEmpty()) {
                Wallpaper first = portraitList.get(0);
                if (first != null && first.getImageUrl() != null) {
                    WallpaperOfTheDayManager.setDailyWallpaper(ctx, first);
                    heroMix.add(first);
                }
            }

            Map<String, List<Wallpaper>> categoryWallpapers = new HashMap<>();
            for (Wallpaper wallpaper : ap) {
                String cat = wallpaper.getCategory();
                String key = (cat != null && !cat.trim().isEmpty() && !cat.equalsIgnoreCase("Premium")) ? cat : "Others";
                categoryWallpapers.computeIfAbsent(key, k -> new ArrayList<>()).add(wallpaper);
            }

            final List<Wallpaper> heroFinal = heroMix;
            final Map<String, List<Wallpaper>> catFinal = categoryWallpapers;

            new Handler(Looper.getMainLooper()).post(() -> {
                setupHeroCarousel(heroFinal);
                if (sectionsFragment != null) {
                    sectionsFragment.setAllWallpapers(ap, catFinal);
                    sectionsFragment.setPremiumWallpapers(pp);
                    sectionsFragment.refreshAllSectionsPublic();
                }
            });
        });
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

        apiManager.loadWallpapersFromAllSources(new ApiManager.ApiCallback() {
            @Override
            public void onWallpapersLoaded(List<Wallpaper> wallpapers) {
                synchronized (loadedWallpaperIds) {
                    for (Wallpaper w : wallpapers)
                        if (w != null && w.getId() != null) loadedWallpaperIds.add(w.getId());
                }
                allWallpapers.clear();
                allWallpapers.addAll(wallpapers);
                isLoadingApiData = false;
                isLoadingMore = false;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        refreshAllSections();
                        checkAllDataLoaded();
                    });
                }
            }

            @Override
            public void onError(String message) {
                isLoadingMore = false;
                isLoadingApiData = false;

                if (getActivity() != null && isAdded()) {
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
                handleMoreWallpapers(newWallpapers);
            }
        });
    }

    private void loadPremiumWallpapersFromFirebase() {
        isLoadingPremiumData = true;
        DatabaseReference premiumRef = FirebaseDatabase.getInstance().getReference("wallpapers/premium");
        premiumRef.get().addOnCompleteListener(task -> {
            isLoadingPremiumData = false;
            if (task.isSuccessful() && task.getResult() != null) {
                List<Wallpaper> refreshedPremium = new ArrayList<>();
                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    Wallpaper wallpaper = snapshot.getValue(Wallpaper.class);
                    if (wallpaper != null && wallpaper.getImageUrl() != null) {
                        wallpaper.setPremium(true);
                        refreshedPremium.add(wallpaper);
                    }
                }
                premiumWallpapers.clear();
                premiumWallpapers.addAll(refreshedPremium);
                refreshAllSections();
            }
            checkAllDataLoaded();
        }).addOnFailureListener(e -> {
            isLoadingPremiumData = false;
            checkAllDataLoaded();
        });
    }

    public void refreshData() {
        if (sectionsFragment != null) {
            sectionsFragment.refreshData();
        }
        loadAllWallpapers();
    }

    private void checkAllDataLoaded() {
        if (!isLoadingApiData && !isLoadingPremiumData) {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            showLoading(false);
            if (errorView != null) errorView.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean show) {
        if (show) {
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            if (shimmerViewContainer != null) shimmerViewContainer.setVisibility(View.VISIBLE);
        } else {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (shimmerViewContainer != null) shimmerViewContainer.setVisibility(View.GONE);
        }
    }

    private void setupHeroCarousel(List<Wallpaper> wallpapers) {
    }

    private void handleMoreWallpapers(List<Wallpaper> newWallpapers) {
    }
}
