package com.wall.mob;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.app.NotificationManager;
import android.app.NotificationChannel;

import com.wall.mob.reels.ReelFragment;
import com.bumptech.glide.Glide;
import android.view.Window;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";
    
    // Material Bottom Navigation
    private BottomNavigationView bottomNavigationView;
    private View bottomNavigationContainer;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabUpload;
    
    private int currentPosition = -1;

    private LinearLayout searchLayout;
    private FrameLayout contentFrame;
    private ImageView notificationButton, profileButton, btn_menu, settingsButton, btnPremium;
    private Toolbar toolbar;
    private AppBarLayout appBarLayout;
    private TextView textview1;
    
    private Fragment homeFragment;
    private Fragment premiumFragment;
    private Fragment favoriteFragment;
    private Fragment downloadsFragment;
    private Fragment reelFragment;
    
    private static final int NOTIFICATION_PERMISSION_CODE = 100;
    private static final int BATTERY_OPTIMIZATION_CODE = 101;
    private static final String PREF_NOTIFICATION_PROMPT = "notification_prompt_shown";
    private static final String PREF_NAME = "app_prefs";
    private SessionManager sessionManager;
    
    private static final String PREF_LAST_UPDATE_CHECK = "last_update_check";
    private static final long UPDATE_CHECK_INTERVAL = 6 * 60 * 60 * 1000; 
    
    // Coin display variables
    private TextView toolbarCoinsTextView;
    private LinearLayout coinDisplayLayout;
    private SharedPreferences sharedPrefs;
    private static final String PREFS_NAME = "GamePrefs";
    private static final String COINS_KEY = "coins";
    private int currentCoins = 0;

    // SwipeRefreshLayout
    private SwipeRefreshLayout swipeRefresh;
    
    // Profile photo state
    private boolean hasProfilePhoto = false;
    
    // Sticky tab container
    private FrameLayout stickyTabContainer;
    
    // UI visibility state
    private boolean uiElementsHidden = false;
    private boolean isToolbarHidden = false;
    private boolean isBottomNavHidden = false;

    // Coin update receiver
    private BroadcastReceiver coinsUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("COINS_UPDATED".equals(intent.getAction())) {
                int newCoins = intent.getIntExtra("new_coins", 0);
                updateCoins(newCoins);
                Log.d(TAG, "Coins updated via broadcast: " + newCoins);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "========== MainActivity Created ==========");
        Log.d(TAG, "Timestamp: " + System.currentTimeMillis());

        // Initialize SessionManager
        sessionManager = new SessionManager(this);
        
        // Initialize SharedPreferences for coins
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentCoins = sharedPrefs.getInt(COINS_KEY, 0);
        Log.d(TAG, "Loaded coins: " + currentCoins);

        // Initialize Firebase Cloud Messaging (Default FCM will handle notifications)
        initializeDefaultFCM();

        // Request notification permission (Android 13+)
        requestNotificationPermission();

        // Request battery optimization exemption
        requestBatteryOptimization();

        // Status bar setup
        setupStatusBar();

        // Initialize views
        initializeViews();

        // Setup navigation
        setupNavigation();

        // Setup other components
        setupOtherComponents();
        
        UnityAdsManager.getInstance(this).initializeAds(this);

        // Update coin display
        updateToolbarCoinsDisplay();

        // Initially select Home
        showFragment(0);

        // Handle notification intent if app was opened from notification
        handleNotificationIntent(getIntent());
        
        // Run FCM diagnostic test
        testFCMSetup();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitDialog();
            }
        });
    }

    private void initializeDefaultFCM() {
        Log.d(TAG, "========== Initializing Default FCM ==========");
        NotificationHelper.createNotificationChannel(this);
        Log.d(TAG, "✓ Notification channel creation called");
    }

    private void testFCMSetup() {
        Log.d(TAG, "========== FCM SETUP DIAGNOSTIC TEST ==========");
        
        try {
            FirebaseApp app = FirebaseApp.getInstance();
            Log.d(TAG, "✓ Firebase initialized: " + app.getName());
        } catch (Exception e) {
            Log.e(TAG, "✗ Firebase NOT initialized!", e);
            Toast.makeText(this, getString(R.string.firebase_init_failed), Toast.LENGTH_LONG).show();
            return;
        }
        
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    String token = task.getResult();
                    Log.d(TAG, "✓ FCM Token retrieved successfully");
                    testTopicSubscription();
                } else {
                    Log.e(TAG, "✗ Failed to get FCM token", task.getException());
                    Toast.makeText(this, getString(R.string.failed_get_fcm_token), Toast.LENGTH_LONG).show();
                }
            });
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✓ POST_NOTIFICATIONS permission granted");
            } else {
                Log.e(TAG, "✗ POST_NOTIFICATIONS permission NOT granted");
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = nm.getNotificationChannel("wallmob_notifications");
                if (channel != null) {
                    Log.d(TAG, "✓ Notification channel exists");
                } else {
                    Log.e(TAG, "✗ Notification channel 'wallmob_notifications' NOT found!");
                }
            }
        }
        
        String packageName = getPackageName();
        if (!packageName.equals("com.rv.wallmob")) {
            Log.e(TAG, "✗ WARNING: Package name doesn't match expected!");
        }
        
        Log.d(TAG, "========== FCM DIAGNOSTIC TEST COMPLETE ==========");
    }

    private void testTopicSubscription() {
        FirebaseMessaging.getInstance().subscribeToTopic("all")
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "✓✓✓ SUCCESSFULLY SUBSCRIBED TO 'all' TOPIC ✓✓✓");
                } else {
                    Log.e(TAG, "✗✗✗ FAILED TO SUBSCRIBE TO 'all' TOPIC ✗✗✗");
                }
            });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                    NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(intent, BATTERY_OPTIMIZATION_CODE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error requesting battery optimization", e);
                }
            }
        }
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            
            // Allow the layout to extend into the status bar area
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {  
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;  
            }  

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;  
            }  
            
            window.getDecorView().setSystemUiVisibility(flags); 
            
            // Set initial color to transparent
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(ContextCompat.getColor(this, android.R.color.white));
        }  
    }
    
    public void updateToolbarOnScroll(int scrollY) {
        float maxScroll = 300f;
        float alpha = Math.min(1.0f, (float) scrollY / maxScroll);

        int baseBgColor = (currentPosition == 1) ?
                ContextCompat.getColor(this, R.color.premium_background) :
                ContextCompat.getColor(this, R.color.surface);

        int alphaBgColor = ColorUtils.setAlphaComponent(baseBgColor, (int) (alpha * 255));

        if (toolbar != null) {
            toolbar.setBackgroundColor(alphaBgColor);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(alphaBgColor);
        }

        // Status bar icons: white at top, dark when scrolled (Home only, light mode)
        if (currentPosition == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean isNight = (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            if (!isNight) {
                View decorView = getWindow().getDecorView();
                int flags = decorView.getSystemUiVisibility();
                if (alpha > 0.5f) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                decorView.setSystemUiVisibility(flags);
            }
        }

        int targetIconColor = (currentPosition == 1) ?
                ContextCompat.getColor(this, R.color.premium_gold) :
                ContextCompat.getColor(this, R.color.onSurface);

        int currentIconColor = ColorUtils.blendARGB(Color.WHITE, targetIconColor, alpha);

        if (btn_menu != null) btn_menu.setColorFilter(currentIconColor);
                if (btnPremium != null) btnPremium.setColorFilter(currentIconColor);
        if (notificationButton != null) notificationButton.setColorFilter(currentIconColor);
        if (profileButton != null && !hasProfilePhoto) profileButton.setColorFilter(currentIconColor);
        if (settingsButton != null) settingsButton.setColorFilter(currentIconColor);
        if (textview1 != null) textview1.setTextColor(currentIconColor);

        if (searchLayout != null) {
            ImageView searchIcon = findViewById(R.id.imageview1);
            int searchTargetColor = (currentPosition == 1) ? targetIconColor : ContextCompat.getColor(this, R.color.gray_dark);
            int searchCurrentColor = ColorUtils.blendARGB(Color.WHITE, searchTargetColor, alpha);

            if (searchIcon != null) searchIcon.setColorFilter(searchCurrentColor);
            if (searchLayout.getChildAt(1) instanceof TextView) {
                ((TextView) searchLayout.getChildAt(1)).setTextColor(searchCurrentColor);
            }

            if (searchLayout.getBackground() != null) {
                int searchBgAlpha = (int) (70 + (alpha * 185));
                searchLayout.getBackground().mutate().setAlpha(searchBgAlpha);
            }
        }
    }

    public void handleScrollDirection(int dy) {
        // Only handle for Home fragment
        if (currentPosition != 0) return;
        
        if (dy > 0 && !uiElementsHidden) {
            // Scrolling down - hide both toolbar and bottom navigation
            uiElementsHidden = true;
            hideToolbarAndBottomNav();
        } else if (dy < 0 && uiElementsHidden) {
            // Scrolling up - show both toolbar and bottom navigation
            uiElementsHidden = false;
            showToolbarAndBottomNav();
        }
    }

    private void hideToolbarAndBottomNav() {
        if (appBarLayout != null && !isToolbarHidden) {
            appBarLayout.animate()
                .translationY(-appBarLayout.getHeight())
                .setDuration(200)
                .start();
            isToolbarHidden = true;
        }
        if (bottomNavigationContainer != null && !isBottomNavHidden) {
            bottomNavigationContainer.animate()
                .translationY(bottomNavigationContainer.getHeight())
                .setDuration(200)
                .start();
            isBottomNavHidden = true;
        }
    }

    private void showToolbarAndBottomNav() {
        if (appBarLayout != null && isToolbarHidden) {
            appBarLayout.animate()
                .translationY(0)
                .setDuration(200)
                .start();
            isToolbarHidden = false;
        }
        if (bottomNavigationContainer != null && isBottomNavHidden) {
            bottomNavigationContainer.animate()
                .translationY(0)
                .setDuration(200)
                .start();
            isBottomNavHidden = false;
        }
    }

    private void resetUIVisibility() {
        uiElementsHidden = false;
        if (appBarLayout != null) {
            appBarLayout.setTranslationY(0);
            isToolbarHidden = false;
        }
        if (bottomNavigationContainer != null) {
            bottomNavigationContainer.setTranslationY(0);
            isBottomNavHidden = false;
        }
    }

    private void initializeViews() {
        appBarLayout = findViewById(R.id.appBarLayout);
        toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }
        
        if (appBarLayout != null) {
            appBarLayout.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
                return insets;
            });
        }

        bottomNavigationContainer = findViewById(R.id.bottomNavigationContainer);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        fabUpload = findViewById(R.id.fab_upload);
        notificationButton = findViewById(R.id.imageview3);
        contentFrame = findViewById(R.id.content_frame);
        searchLayout = findViewById(R.id.searchLayout);
        profileButton = findViewById(R.id.imageview4);
        settingsButton= findViewById(R.id.btn_settings);
        btnPremium = findViewById(R.id.btn_premium);
        btn_menu = findViewById(R.id.btn_menu);
        textview1 = findViewById(R.id.textview1);

        toolbarCoinsTextView = findViewById(R.id.toolbarCoinsTextView);
        coinDisplayLayout = findViewById(R.id.coinDisplayLayout);
        swipeRefresh = findViewById(R.id.swipeRefreshLayout);
        
        // Initialize sticky tab container
        stickyTabContainer = findViewById(R.id.sticky_tab_container);
    }

    private void setupNavigation() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    if (currentPosition != 0) showFragment(0);
                    return true;
                } else if (itemId == R.id.nav_reel) {
                    if (currentPosition != 4) showFragment(4);
                    return true;
                } else if (itemId == R.id.nav_favorite) {
                    if (currentPosition != 2) showFragment(2);
                    return true;
                } else if (itemId == R.id.nav_downloads) {
                    if (currentPosition != 3) showFragment(3);
                    return true;
                }
                return false;
            });
        }
    }

    private void setupOtherComponents() {
        setupMenuButton();
        setupSwipeRefresh();

        if (coinDisplayLayout != null) {
            coinDisplayLayout.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, UnityAdActivity.class);
                startActivity(intent);
            });
        }

        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> openNotifications());
        }

        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent profileIntent = new Intent(MainActivity.this, ProfileActivity.class);
                startActivity(profileIntent);
            });
        }

        searchLayout.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
            startActivity(intent);
        });
        
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        btnPremium.setOnClickListener(v -> {
            showFragment(1);
        });

        fabUpload.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UploadWallpaperActivity.class);
            startActivity(intent);
        });

    }

    private void setupSwipeRefresh() {
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                Log.d(TAG, "Swipe refresh triggered");
                refreshCurrentFragment();
            });

            swipeRefresh.setColorSchemeResources(
                    android.R.color.holo_blue_bright,
                    android.R.color.holo_green_light,
                    android.R.color.holo_orange_light,
                    android.R.color.holo_red_light
            );

            swipeRefresh.setProgressBackgroundColorSchemeResource(
                    android.R.color.white
            );
        }
    }

    private void updateToolbarCoinsDisplay() {
        if (toolbarCoinsTextView != null) {
            runOnUiThread(() -> {
                toolbarCoinsTextView.setText(String.valueOf(currentCoins));
            });
        }
    }

    private void updateMainIconSizes() {
        SharedPreferences prefs = getSharedPreferences("settings_prefs", MODE_PRIVATE);
        String currentTextSize = prefs.getString("app_text_size", "normal");
        int iconSizeDimen = R.dimen.icon_size_normal;
        
        switch (currentTextSize) {
            case "small":
                iconSizeDimen = R.dimen.icon_size_small;
                break;
            case "large":
                iconSizeDimen = R.dimen.icon_size_large;
                break;
            case "normal":
            default:
                iconSizeDimen = R.dimen.icon_size_normal;
                break;
        }

        int iconSize = getResources().getDimensionPixelSize(iconSizeDimen);
        
        int[] iconIds = {
            R.id.btn_menu,
            R.id.btn_settings,
            R.id.imageview3,
            R.id.btn_premium,
            R.id.cardview1
        };
        
        for (int id : iconIds) {
            View view = findViewById(id);
            if (view != null) {
                android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = iconSize;
                params.height = iconSize;
                view.setLayoutParams(params);
            }
        }
        
        if (bottomNavigationView != null) {
            bottomNavigationView.setItemIconSize(iconSize);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SketchApplication.needsDataRefresh) {
            SketchApplication.needsDataRefresh = false;
            refreshAllFragments();
        }
        refreshCoins();
        loadProfilePhoto();
        updateMainIconSizes();
    }

    private void refreshCoins() {
        currentCoins = sharedPrefs.getInt(COINS_KEY, 0);
        updateToolbarCoinsDisplay();
    }

    private void loadProfilePhoto() {
        String photoUrl = sessionManager.getPhotoUrl();
        hasProfilePhoto = photoUrl != null && !photoUrl.isEmpty();
        if (hasProfilePhoto && profileButton != null) {
            profileButton.setImageTintList(null);
            profileButton.setColorFilter(null);
            Glide.with(this)
                    .load(photoUrl)
                    .transform(new CircleCrop())
                    .into(profileButton);
        } else if (profileButton != null) {
            profileButton.setImageResource(R.drawable.ic_profile);
        }
    }

    public void updateCoins(int newCoins) {
        currentCoins = newCoins;
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(COINS_KEY, currentCoins);
        editor.apply();
        updateToolbarCoinsDisplay();
    }

    public int getCurrentCoins() {
        return currentCoins;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("from_notification", false)) {
            String notificationType = intent.getStringExtra("notification_type");
            String extraData = intent.getStringExtra("extra_data");
            
            if (intent.hasExtra("title") && intent.hasExtra("message")) {
                String title = intent.getStringExtra("title");
                String message = intent.getStringExtra("message");
                NotificationHelper.storeNotificationInHistory(this, title, message, 
                    notificationType != null ? notificationType : "default", 
                    extraData != null ? extraData : "");
            }

            if (notificationType != null) {
                switch (notificationType) {
                    case "new_wallpaper":
                        if (extraData != null && !extraData.isEmpty()) {
                            openWallpaperDetails(extraData);
                        } else {
                            showFragment(0);
                        }
                        break;
                    case "special_offer":
                        showFragment(1);
                        break;
                    case "favorites_update":
                        showFragment(2);
                        if (favoriteFragment instanceof FavoriteFragment) {
                            ((FavoriteFragment) favoriteFragment).refreshData();
                        }
                        break;
                    case "public_message":
                    default:
                        openNotifications();
                        break;
                }
            }
        }
    }

    private void openWallpaperDetails(String wallpaperId) {
        showFragment(0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter coinFilter = new IntentFilter("COINS_UPDATED");
        LocalBroadcastManager.getInstance(this).registerReceiver(coinsUpdateReceiver, coinFilter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(coinsUpdateReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
    }

    private void openNotifications() {
        Intent intent = new Intent(this, NotificationsActivity.class);
        startActivity(intent);
    }

    private void setupMenuButton() {
        btn_menu.setOnClickListener(v -> openMenu());
        textview1.setOnClickListener(v -> openMenu());
    }

    private void openMenu() {
        MenuBottomSheetDialog bottomSheet = new MenuBottomSheetDialog(MainActivity.this);
        bottomSheet.show();
    }

    /**
     * Show or hide the sticky tab container
     * @param show true to show, false to hide
     */
    public void showStickyTabs(boolean show) {
        if (stickyTabContainer != null) {
            stickyTabContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showFragment(int position) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        if (homeFragment != null) ft.hide(homeFragment);
        if (premiumFragment != null) ft.hide(premiumFragment);
        if (favoriteFragment != null) ft.hide(favoriteFragment);
        if (downloadsFragment != null) ft.hide(downloadsFragment);
        if (reelFragment != null) ft.hide(reelFragment);

        switch (position) {
            case 0:
                if (homeFragment == null) {
                    homeFragment = new HomeFragment();
                    ft.add(R.id.content_frame, homeFragment, "HOME");
                } else {
                    ft.show(homeFragment);
                }
                break;
                
            case 1:
                if (premiumFragment == null) {
                    premiumFragment = new PremiumFragment();
                    ft.add(R.id.content_frame, premiumFragment, "PREMIUM");
                } else {
                    ft.show(premiumFragment);
                }
                showStickyTabs(false);
                break;
                
            case 2:
                if (favoriteFragment == null) {
                    favoriteFragment = new FavoriteFragment();
                    ft.add(R.id.content_frame, favoriteFragment, "FAVORITE");
                } else {
                    ft.show(favoriteFragment);
                }
                showStickyTabs(false);
                break;

            case 3: 
                if (downloadsFragment == null) {
                    downloadsFragment = new DownloadsFragment();
                    ft.add(R.id.content_frame, downloadsFragment, "DOWNLOADS");
                } else {
                    ft.show(downloadsFragment);
                }
                showStickyTabs(false);
                break;

            case 4:
                if (reelFragment == null) {
                    reelFragment = ReelFragment.newInstance(Math.max(0, pendingReelPosition));
                    ft.add(R.id.content_frame, reelFragment, "REEL");
                } else {
                    ft.show(reelFragment);
                    if (pendingReelPosition >= 0) {
                        ((ReelFragment) reelFragment).scrollToPosition(pendingReelPosition);
                    }
                }
                pendingReelPosition = -1;
                showStickyTabs(false);
                break;
        }
        
        ft.commit();
        currentPosition = position;
        
        // Reset UI visibility when switching fragments
        resetUIVisibility();
        
        updateNavigationSelection(position);

        // THEME SWITCHING LOGIC
        if (position == 1) {
            enablePremiumTheme();
        } else if (position == 4) {
            enableReelTheme();
        } else {
            enableNormalTheme();
        }

        // Hide toolbar for full-bleed reels
        if (appBarLayout != null) {
            appBarLayout.setVisibility(position == 4 ? View.GONE : View.VISIBLE);
        }

        // Transparent/scroll toolbar for Home and Premium only
        if (position == 0 || position == 1) {
            updateToolbarOnScroll(0);
        }
    }

    private void enablePremiumTheme() {
int darkColor = ContextCompat.getColor(this, R.color.premium_background);
int goldColor = ContextCompat.getColor(this, R.color.premium_gold);
int whiteColor = ContextCompat.getColor(this, R.color.white);

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {  
        Window window = getWindow();  
        int flags = window.getDecorView().getSystemUiVisibility();  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {  
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;  
        }  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;  
        }  
        window.getDecorView().setSystemUiVisibility(flags);  
        window.setNavigationBarColor(darkColor);  
    }  

    if (toolbar != null) toolbar.setBackgroundColor(darkColor);  

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {  
        getWindow().setStatusBarColor(darkColor);  
    }  

    if (btn_menu != null) btn_menu.setColorFilter(goldColor);  
    if (notificationButton != null) notificationButton.setColorFilter(goldColor);  
    if (profileButton != null && !hasProfilePhoto) profileButton.setColorFilter(goldColor);  
    if (textview1 != null) textview1.setTextColor(whiteColor);  

    if (searchLayout != null) {  
        searchLayout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.search_premium_bg)));  
        if (searchLayout.getChildAt(0) instanceof ImageView) {  
            ((ImageView) searchLayout.getChildAt(0)).setColorFilter(goldColor);  
        }  
        if (searchLayout.getChildAt(1) instanceof TextView) {  
            ((TextView) searchLayout.getChildAt(1)).setTextColor(ContextCompat.getColor(this, R.color.gray_medium));  
        }  
        ImageView settingsBtn = findViewById(R.id.btn_settings);  
        if (settingsBtn != null) {  
            settingsBtn.setColorFilter(goldColor);  
        }  
    }  

    if (bottomNavigationView != null) {  
        bottomNavigationView.setBackgroundColor(darkColor);  
        int[][] states = new int[][] {  
            new int[] { android.R.attr.state_checked },  
            new int[] { -android.R.attr.state_checked }  
        };  
        int[] colors = new int[] { goldColor, Color.GRAY };  
        android.content.res.ColorStateList colorStateList = new android.content.res.ColorStateList(states, colors);  
          
        bottomNavigationView.setItemIconTintList(colorStateList);  
        bottomNavigationView.setItemTextColor(colorStateList);  
    }  
      
        
        if (fabUpload != null) {
            fabUpload.setBackgroundTintList(ColorStateList.valueOf(goldColor));
        }
    }

    private void enableNormalTheme() {
        int surfaceColor = ContextCompat.getColor(this, R.color.surface);
        int onSurfaceColor = ContextCompat.getColor(this, R.color.onSurface);
        int grayColor = ContextCompat.getColor(this, R.color.gray_dark);

        boolean isNight = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            int flags = window.getDecorView().getSystemUiVisibility();
            if (!isNight) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            window.getDecorView().setSystemUiVisibility(flags);
            window.setNavigationBarColor(surfaceColor);
        }

        if (toolbar != null) toolbar.setBackgroundColor(surfaceColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(surfaceColor);
        }

        if (btn_menu != null) btn_menu.setColorFilter(onSurfaceColor);
        if (btnPremium != null) btnPremium.setColorFilter(onSurfaceColor);
        if (notificationButton != null) notificationButton.setColorFilter(onSurfaceColor);
        if (profileButton != null && !hasProfilePhoto) profileButton.setColorFilter(onSurfaceColor);
        if (textview1 != null) textview1.setTextColor(onSurfaceColor);

        if (searchLayout != null) {
            searchLayout.setBackgroundTintList(null);
            searchLayout.setBackgroundResource(R.drawable.bg_search);
            if (searchLayout.getChildAt(0) instanceof ImageView) {
                ((ImageView) searchLayout.getChildAt(0)).setColorFilter(grayColor);
            }
            if (searchLayout.getChildAt(1) instanceof TextView) {
                ((TextView) searchLayout.getChildAt(1)).setTextColor(grayColor);
            }
            ImageView settingsBtn = findViewById(R.id.btn_settings);
            if (settingsBtn != null) {
                settingsBtn.setColorFilter(onSurfaceColor);
            }
        }

        if (bottomNavigationView != null) {
            bottomNavigationView.setBackgroundColor(surfaceColor);
            int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] { -android.R.attr.state_checked }
            };
            int[] colors = new int[] { onSurfaceColor, grayColor };
            android.content.res.ColorStateList colorStateList = new android.content.res.ColorStateList(states, colors);

            bottomNavigationView.setItemIconTintList(colorStateList);
            bottomNavigationView.setItemTextColor(colorStateList);
        }

        if (fabUpload != null) {
            fabUpload.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
        }
    }

    private void enableReelTheme() {
        int blackColor = ContextCompat.getColor(this, android.R.color.black);
        int whiteColor = ContextCompat.getColor(this, android.R.color.white);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            int flags = window.getDecorView().getSystemUiVisibility();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
            window.setStatusBarColor(blackColor);
            window.setNavigationBarColor(blackColor);
        }

        if (bottomNavigationView != null) {
            bottomNavigationView.setBackgroundColor(blackColor);
            int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] { -android.R.attr.state_checked }
            };
            int[] colors = new int[] { whiteColor, Color.GRAY };
            android.content.res.ColorStateList colorStateList = new android.content.res.ColorStateList(states, colors);
            bottomNavigationView.setItemIconTintList(colorStateList);
            bottomNavigationView.setItemTextColor(colorStateList);
        }


    }

    private void updateNavigationSelection(int position) {
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(null); 
            switch (position) {
                case 0:
                    bottomNavigationView.setSelectedItemId(R.id.nav_home);
                    break;
                case 1:
                    int size = bottomNavigationView.getMenu().size();
                    for (int i = 0; i < size; i++) {
                        bottomNavigationView.getMenu().getItem(i).setChecked(false);
                    }
                    break;
                case 2:
                    bottomNavigationView.setSelectedItemId(R.id.nav_favorite);
                    break;
                case 3:
                    bottomNavigationView.setSelectedItemId(R.id.nav_downloads);
                    break;
                case 4:
                    bottomNavigationView.setSelectedItemId(R.id.nav_reel);
                    break;
            }
            setupNavigation(); 
        }
    }

    private void refreshAllFragments() {
        if (homeFragment instanceof HomeFragment) {
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.detach(homeFragment).attach(homeFragment).commit();
        }
        if (premiumFragment instanceof PremiumFragment) {
            ((PremiumFragment) premiumFragment).refreshData();
        }
        if (favoriteFragment instanceof FavoriteFragment) {
            ((FavoriteFragment) favoriteFragment).refreshData();
        }
        if (downloadsFragment instanceof DownloadsFragment) {
            ((DownloadsFragment) downloadsFragment).refreshData();
        }
        if (reelFragment instanceof ReelFragment) {
            ((ReelFragment) reelFragment).loadFeed();
        }
    }

    private void refreshCurrentFragment() {
        switch (currentPosition) {
            case 0:
                if (homeFragment instanceof HomeFragment) {
                    FragmentManager fm = getSupportFragmentManager();
                    FragmentTransaction ft = fm.beginTransaction();
                    ft.detach(homeFragment).attach(homeFragment).commit();
                }
                break;
            case 1:
                if (premiumFragment instanceof PremiumFragment) {
                    ((PremiumFragment) premiumFragment).refreshData();
                }
                break;
            case 2:
                if (favoriteFragment instanceof FavoriteFragment) {
                    ((FavoriteFragment) favoriteFragment).refreshData();
                }
                break;
            case 3:
                if (downloadsFragment instanceof DownloadsFragment) {
                    ((DownloadsFragment) downloadsFragment).refreshData();
                }
                break;
            case 4:
                if (reelFragment instanceof ReelFragment) {
                    ((ReelFragment) reelFragment).loadFeed();
                }
                break;
        }

        if (swipeRefresh != null) {
            swipeRefresh.postDelayed(() -> {
                if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
                    swipeRefresh.setRefreshing(false);
                }
            }, 1200);
        }
    }

    public void refreshPremiumFragment() {
        if (premiumFragment instanceof PremiumFragment) {
            ((PremiumFragment) premiumFragment).refreshData();
        }
    }

    public boolean isPremiumFragmentLoading() {
        if (premiumFragment instanceof PremiumFragment) {
            return ((PremiumFragment) premiumFragment).isLoadingData();
        }
        return false;
    }

    public void navigateToPremium() {
        showFragment(1);
    }

    private int pendingReelPosition = -1;

    public void navigateToReels(int position) {
        pendingReelPosition = position;
        showFragment(4);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.notifications_enabled), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.notifications_disabled_settings), Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BATTERY_OPTIMIZATION_CODE) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Log.d(TAG, "✓ Battery optimization disabled");
            }
        }
    }
    
    private void showExitDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_exit, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        MaterialButton btnYes = dialogView.findViewById(R.id.btn_yes);
        MaterialButton btnNo = dialogView.findViewById(R.id.btn_no);

        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            finishAffinity();
        });

        btnNo.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}