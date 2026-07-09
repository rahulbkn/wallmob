package com.wall.mob;

import android.content.Context;
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
    if (activity == null) return;

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

    boolean shouldStick = scrollY > (heroH - toolbarH);

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
        List<Wallpaper> portraitList = new ArrayList<>();
        List<Wallpaper> landscapeList = new ArrayList<>();
        Set<String> uniqueUrls = new HashSet<>();

        for (Wallpaper w : allWallpapers) {
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

        Wallpaper wotd = WallpaperOfTheDayManager.getDailyWallpaper(requireContext());
        if (wotd != null && wotd.getImageUrl() != null) {
            heroUrls.add(wotd.getImageUrl());
            heroMix.add(wotd);
        }

        for (Wallpaper w : premiumWallpapers) {
            if (w != null && w.getImageUrl() != null && !heroUrls.contains(w.getImageUrl())) {
                heroUrls.add(w.getImageUrl());
                heroMix.add(w);
                if (heroMix.size() >= 6) break;
            }
        }
        if (heroMix.isEmpty() && !portraitList.isEmpty()) {
            Wallpaper first = portraitList.get(0);
            if (first != null && first.getImageUrl() != null) {
                WallpaperOfTheDayManager.setDailyWallpaper(requireContext(), first);
                heroMix.add(first);
            }
        }
        setupHeroCarousel(heroMix);

        if (sectionsFragment != null) {
            sectionsFragment.setAllWallpapers(allWallpapers);
            sectionsFragment.setPremiumWallpapers(premiumWallpapers);
            sectionsFragment.refreshAllSectionsPublic();
        }
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

    private void handleMoreWallpapers(List<Wallpaper> newWallpapers) {
        isLoadingMore = false;
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
        if (getActivity() != null) getActivity().runOnUiThread(() -> refreshAllSections());
    }

    private void showLoading(boolean loading) {
        boolean hasExistingData = !allWallpapers.isEmpty() || !premiumWallpapers.isEmpty();
        Log.d(TAG, "showLoading: loading=" + loading + ", hasExistingData=" + hasExistingData);

        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        if (shimmerViewContainer != null) {
            if (loading && !hasExistingData) {
                Log.d(TAG, "Showing shimmer");
                shimmerViewContainer.setVisibility(View.VISIBLE);
                shimmerViewContainer.startShimmer();
                if (nestedScrollView != null) nestedScrollView.setVisibility(View.GONE);
            } else {
                Log.d(TAG, "Hiding shimmer");
                shimmerViewContainer.stopShimmer();
                shimmerViewContainer.setVisibility(View.GONE);
                if (nestedScrollView != null) nestedScrollView.setVisibility(View.VISIBLE);
            }
        }

        if (errorView != null) errorView.setVisibility(View.GONE);
    }

    public void refreshData() {
        allWallpapers.clear();
        loadedWallpaperIds.clear();
        loadAllWallpapers();
        if (sectionsFragment != null) sectionsFragment.refreshData();
    }

    private static class HeroCarouselAdapter extends RecyclerView.Adapter<HeroCarouselAdapter.VH> {
        private final Context ctx;
        private final List<Wallpaper> wallpapers;
        private final float density;
        private final SparseArray<int[]> paletteColors = new SparseArray<>();

        HeroCarouselAdapter(Context ctx, List<Wallpaper> wallpapers) {
            this.ctx = ctx;
            this.wallpapers = wallpapers;
            this.density = ctx.getResources().getDisplayMetrics().density;
        }

        void storeColors(int position, int top, int bottom) {
            paletteColors.put(position, new int[]{top, bottom});
        }

        @Nullable
        int[] getStoredColors(int position) {
            return paletteColors.get(position);
        }

        private int getThemeColor(int attr) {
            TypedValue typedValue = new TypedValue();
            ctx.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout root = new FrameLayout(ctx);
            root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            root.setClipChildren(true);
            root.setClipToPadding(true);

            View bgView = new View(ctx);
            bgView.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            GradientDrawable initialBg = new GradientDrawable();
            initialBg.setShape(GradientDrawable.RECTANGLE);
            initialBg.setColor(getThemeColor(android.R.attr.windowBackground));
            bgView.setBackground(initialBg);
            root.addView(bgView);

            ImageView blurIv = new ImageView(ctx);
            blurIv.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            blurIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            root.addView(blurIv);

            FrameLayout contentContainer = new FrameLayout(ctx);
            contentContainer.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            contentContainer.setPadding(0, (int) (50 * density), 0, 0);
            root.addView(contentContainer);

            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int containerWidth = dm.widthPixels;

            int framePadding = (int) (80 * density);
            int calculatedWidth = (int) ((containerWidth - 2 * framePadding) * 0.55f);
            int maxWidth = (int) (400 * density);
            int phoneWidth = Math.min(calculatedWidth, maxWidth);
            int phoneHeight = (int) (phoneWidth * 18f / 9f);

            ImageView silverFrame = new ImageView(ctx);
            int silverWidth = phoneWidth + (int) (3 * density);
            int silverHeight = phoneHeight + (int) (3 * density);
            FrameLayout.LayoutParams silverLp = new FrameLayout.LayoutParams(silverWidth, silverHeight);
            silverLp.gravity = android.view.Gravity.CENTER;
            silverFrame.setLayoutParams(silverLp);
            silverFrame.setScaleType(ImageView.ScaleType.FIT_XY);
            silverFrame.setImageResource(R.drawable.phone_silver_frame);
            contentContainer.addView(silverFrame);

            androidx.cardview.widget.CardView cardView = new androidx.cardview.widget.CardView(ctx);
            FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(phoneWidth, phoneHeight);
            cardLp.gravity = android.view.Gravity.CENTER;
            cardView.setLayoutParams(cardLp);
            cardView.setRadius(16 * density);
            cardView.setPreventCornerOverlap(false);
            cardView.setUseCompatPadding(false);
            cardView.setCardElevation(0);
            cardView.setCardBackgroundColor(0xFF000000);
            cardView.setContentPadding((int)(1 * density), (int)(1 * density), (int)(1 * density), (int)(1 * density));

            ImageView iv = new ImageView(ctx);
            iv.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            cardView.addView(iv);
            contentContainer.addView(cardView);

            View bottomScrim = new View(ctx);
            FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, (int) (80 * density));
            bottomLp.gravity = android.view.Gravity.BOTTOM;
            bottomScrim.setLayoutParams(bottomLp);
            GradientDrawable scrimBg = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xCC000000, 0x00000000});
            scrimBg.setShape(GradientDrawable.RECTANGLE);
            bottomScrim.setBackground(scrimBg);
            contentContainer.addView(bottomScrim);

            View topScrim = new View(ctx);
            FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, (int) (64 * density));
            topLp.gravity = android.view.Gravity.TOP;
            topScrim.setLayoutParams(topLp);
            topScrim.setBackgroundResource(R.drawable.scrim_top);
            root.addView(topScrim);

            LinearLayout statusBar = new LinearLayout(ctx);
            statusBar.setOrientation(LinearLayout.HORIZONTAL);
            FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(phoneWidth, (int) (20 * density));
            statusLp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            statusBar.setLayoutParams(statusLp);
            statusBar.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
            statusBar.setPadding((int) (10 * density), 0, (int) (10 * density), 0);

            TextView timeTv = new TextView(ctx);
            timeTv.setText("9:41");
            timeTv.setTextSize(8);
            timeTv.setTextColor(0xFFFFFFFF);
            statusBar.addView(timeTv);

            cardView.addView(statusBar);

            LinearLayout appsContainer = new LinearLayout(ctx);
            appsContainer.setOrientation(LinearLayout.VERTICAL);
            FrameLayout.LayoutParams appsLp = new FrameLayout.LayoutParams(phoneWidth, FrameLayout.LayoutParams.MATCH_PARENT);
            appsLp.gravity = android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.BOTTOM;
            appsLp.bottomMargin = (int) (10 * density);
            appsContainer.setLayoutParams(appsLp);
            appsContainer.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            appsContainer.setPadding((int) (2 * density), 0, (int) (2 * density), 0);

            for (int row = 0; row < 2; row++) {
                LinearLayout rowLayout = new LinearLayout(ctx);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(android.view.Gravity.CENTER);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                if (row == 1) {
                    rowLayout.setPadding(0, (int) (2 * density), 0, 0);
                }

                for (int i = 0; i < 4; i++) {
                    LinearLayout holder = new LinearLayout(ctx);
                    holder.setGravity(android.view.Gravity.CENTER);
                    LinearLayout.LayoutParams holderLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    holderLp.setMargins((int) (2 * density), (int) (2 * density), (int) (2 * density), (int) (2 * density));
                    holder.setLayoutParams(holderLp);

                    View appIcon = new View(ctx);
                    LinearLayout.LayoutParams iconLp =
                            new LinearLayout.LayoutParams(
                                    (int) (22 * density),
                                    (int) (22 * density));
                    appIcon.setLayoutParams(iconLp);

                    GradientDrawable iconBg = new GradientDrawable();
                    iconBg.setShape(GradientDrawable.RECTANGLE);
                    iconBg.setCornerRadius(4 * density);
                    iconBg.setColor(0x40FFFFFF);
                    appIcon.setBackground(iconBg);

                    holder.addView(appIcon);
                    rowLayout.addView(holder);
                }

                appsContainer.addView(rowLayout);
            }
            cardView.addView(appsContainer);

            ImageView frameIv = new ImageView(ctx);
            int frameWidth = phoneWidth;
            int frameHeight = (int) (frameWidth * 18f / 9f);
            FrameLayout.LayoutParams frameLp = new FrameLayout.LayoutParams(frameWidth, frameHeight);
            frameLp.gravity = android.view.Gravity.CENTER;
            frameIv.setLayoutParams(frameLp);
            frameIv.setScaleType(ImageView.ScaleType.FIT_XY);
            frameIv.setImageResource(R.drawable.phone_frame_overlay);
            contentContainer.addView(frameIv);

            return new VH(root, bgView, blurIv, iv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Wallpaper wp = wallpapers.get(position);
            String thumb = wp.getThumbnailUrl();
            String url = (thumb != null && !thumb.isEmpty()) ? thumb : wp.getImageUrl();
            holder.currentUrl = url;

            Glide.with(ctx).clear(holder.imageView);
            Glide.with(ctx).clear(holder.blurImageView);

            int backgroundColor = getThemeColor(android.R.attr.windowBackground);
            GradientDrawable defaultBg = new GradientDrawable();
            defaultBg.setShape(GradientDrawable.RECTANGLE);
            defaultBg.setColor(backgroundColor);
            holder.bgView.setBackground(defaultBg);

            Glide.with(ctx)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transform(new jp.wasabeef.glide.transformations.BlurTransformation(25, 3))
                    .into(holder.blurImageView);

            Glide.with(ctx)
                    .asBitmap()
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                            if (!url.equals(holder.currentUrl)) return;
                            holder.imageView.setImageBitmap(bitmap);

                            Palette.from(bitmap).generate(palette -> {
                                if (!url.equals(holder.currentUrl)) return;

                                int defaultBgColor = getThemeColor(android.R.attr.windowBackground);
                                int defaultTop = defaultBgColor;
                                int defaultBottom = 0xFF121212;

                                int topColor = palette.getDarkVibrantColor(defaultTop);
                                int bottomColor = palette.getDarkMutedColor(defaultBottom);

                                GradientDrawable g = new GradientDrawable(
                                        GradientDrawable.Orientation.TOP_BOTTOM,
                                        new int[]{topColor, bottomColor});
                                g.setShape(GradientDrawable.RECTANGLE);
                                holder.bgView.setBackground(g);
                            });
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            holder.imageView.setImageDrawable(placeholder);
                        }
                    });

            holder.imageView.setOnClickListener(v -> {
                if (ctx instanceof MainActivity) ((MainActivity) ctx).navigateToPremium();
            });
        }

        @Override
        public void onViewRecycled(@NonNull VH holder) {
            super.onViewRecycled(holder);
            holder.currentUrl = null;
            Glide.with(ctx).clear(holder.imageView);
        }

        @Override
        public int getItemCount() { return wallpapers.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final FrameLayout root;
            final View bgView;
            final ImageView blurImageView;
            final ImageView imageView;
            String currentUrl;

            VH(FrameLayout root, View bgView, ImageView blurIv, ImageView iv) {
                super(root);
                this.root = root;
                this.bgView = bgView;
                this.blurImageView = blurIv;
                imageView = iv;
            }
        }
    }

    private void setupHeroCarousel(List<Wallpaper> wallpapers) {
        if (heroCarousel == null || wallpapers == null || wallpapers.isEmpty()) return;
        List<Wallpaper> heroWallpapers = new ArrayList<>();
        for (int i = 0; i < Math.min(6, wallpapers.size()); i++) {
            Wallpaper w = wallpapers.get(i);
            if (w.getImageUrl() != null && !w.getImageUrl().isEmpty()) heroWallpapers.add(w);
        }
        if (heroWallpapers.isEmpty()) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cardWidth = screenWidth;
        int cardHeight = cardWidth;

        ViewGroup.LayoutParams lp = heroContainer.getLayoutParams();
        lp.height = cardHeight - dpToPx(10);
        heroContainer.setLayoutParams(lp);

        ViewGroup.LayoutParams vlp = heroCarousel.getLayoutParams();
        vlp.height = cardHeight - dpToPx(10);
        heroCarousel.setLayoutParams(vlp);

        heroCarousel.setOffscreenPageLimit(3);

        RecyclerView rv = (RecyclerView) heroCarousel.getChildAt(0);
        rv.setPadding(0, 0, 0, 0);
        rv.setClipToPadding(false);

        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer((page, position) -> {
            if (page instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) page;
                if (vg.getChildCount() > 1) {
                    vg.getChildAt(1).setTranslationX(-position * (page.getWidth() * 0.5f));
                }
            }
        });
        heroCarousel.setPageTransformer(transformer);

        heroCarouselAdapter = new HeroCarouselAdapter(mContext, heroWallpapers);
        heroCarousel.setAdapter(heroCarouselAdapter);

        View oldBg = heroContainer.findViewWithTag("hero_dark_bg");
        if (oldBg != null) heroContainer.removeView(oldBg);
        View darkBg = new View(mContext);
        darkBg.setTag("hero_dark_bg");
        darkBg.setLayoutParams(new FrameLayout.LayoutParams(screenWidth, cardHeight));
        GradientDrawable bgGradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF000000, 0x00000000});
        darkBg.setBackground(bgGradient);
        heroContainer.addView(darkBg, 0);

        heroDots.removeAllViews();
        for (int i = 0; i < heroWallpapers.size(); i++) {
            View dot = new View(mContext);
            int size = dpToPx(6);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(size, size);
            dotLp.setMargins(dpToPx(3), 0, dpToPx(3), 0);
            dot.setLayoutParams(dotLp);
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
tabIsSticky = false;
stickyTabContainer = null;
cachedTabLayout = null;
cachedTabPlaceholder = null;
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