package com.wall.mob;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.graphics.Color;

import androidx.annotation.NonNull;
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
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.app.NotificationManager;
import android.app.NotificationChannel;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    
    // Material Bottom Navigation
    private BottomNavigationView bottomNavigationView;
    
    private int currentPosition = -1;

    private LinearLayout searchLayout;
    private FrameLayout contentFrame;
    private ImageView notificationButton, profileButton, btn_menu;
    private Toolbar toolbar;
    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbar;
    private TextView textview1;
    
    private Fragment homeFragment;
    private Fragment premiumFragment;
    private Fragment favoriteFragment;
    private Fragment downloadsFragment; // NEW: Added Downloads Fragment
    
    private static final int NOTIFICATION_PERMISSION_CODE = 100;
    private static final int BATTERY_OPTIMIZATION_CODE = 101;
    private static final String PREF_NOTIFICATION_PROMPT = "notification_prompt_shown";
    private static final String PREF_NAME = "app_prefs";
    private SessionManager sessionManager;
    
    private static final String PREF_LAST_UPDATE_CHECK = "last_update_check";
    private static final long UPDATE_CHECK_INTERVAL = 6 * 60 * 60 * 1000; // 6 hours
    
    // Coin display variables
    private TextView toolbarCoinsTextView;
    private LinearLayout coinDisplayLayout;
    private SharedPreferences sharedPrefs;
    private static final String PREFS_NAME = "GamePrefs";
    private static final String COINS_KEY = "coins";
    private int currentCoins = 0;

    // SwipeRefreshLayout
    private SwipeRefreshLayout swipeRefresh;
    
        @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }
    

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
    
    SharedPreferences prefs =
        getSharedPreferences("settings_prefs", MODE_PRIVATE);

String theme = prefs.getString("app_theme", "system");

switch (theme) {
    case "light":
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        break;

    case "dark":
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        break;

    default:
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        break;
}
    
    
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
            Toast.makeText(this, "Firebase initialization failed!", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(this, "Failed to get FCM token!", Toast.LENGTH_LONG).show();
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
    
    private void initializeViews() {
        appBarLayout = findViewById(R.id.appBarLayout);
        collapsingToolbar = findViewById(R.id.collapsingToolbar);
        toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }

        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        notificationButton = findViewById(R.id.imageview3);
        contentFrame = findViewById(R.id.content_frame);
        searchLayout = findViewById(R.id.searchLayout);
        profileButton = findViewById(R.id.imageview4);
        btn_menu = findViewById(R.id.btn_menu);
        textview1 = findViewById(R.id.textview1);

        toolbarCoinsTextView = findViewById(R.id.toolbarCoinsTextView);
        coinDisplayLayout = findViewById(R.id.coinDisplayLayout);
        swipeRefresh = findViewById(R.id.swipeRefreshLayout);
    }

    private void setupNavigation() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    if (currentPosition != 0) showFragment(0);
                    return true;
                } else if (itemId == R.id.nav_premium) {
                    if (currentPosition != 1) showFragment(1);
                    return true;
                } else if (itemId == R.id.nav_favorite) {
                    if (currentPosition != 2) showFragment(2);
                    return true;
                } else if (itemId == R.id.nav_downloads) { // NEW: Downloads Tab Logic
                    if (currentPosition != 3) showFragment(3);
                    return true;
                }
                return false;
            });
        }
    }

    private void setupOtherComponents() {
        setupMenuButton();
        setupAppBarScrollBehavior();
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

        // NEW: Filter Button Click Listener
        ImageView btnFilter = findViewById(R.id.btn_filter);
        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> {
                // Prevent the parent searchLayout click from firing simultaneously
                Toast.makeText(MainActivity.this, "Open Filters", Toast.LENGTH_SHORT).show();
                // TODO: Initialize your bottom sheet filter dialog here
            });
        }
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

    private void setupAppBarScrollBehavior() {
        if (appBarLayout != null) {
            appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                float percentage = (float) Math.abs(verticalOffset) / (float) appBarLayout.getTotalScrollRange();
                if (toolbar != null) {
                    toolbar.setAlpha(1.0f - percentage);
                }
            });
        }
    }

    private void updateToolbarCoinsDisplay() {
        if (toolbarCoinsTextView != null) {
            runOnUiThread(() -> {
                toolbarCoinsTextView.setText(String.valueOf(currentCoins));
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCoins();
    }

    private void refreshCoins() {
        currentCoins = sharedPrefs.getInt(COINS_KEY, 0);
        updateToolbarCoinsDisplay();
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

    private void showFragment(int position) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        if (homeFragment != null) ft.hide(homeFragment);
        if (premiumFragment != null) ft.hide(premiumFragment);
        if (favoriteFragment != null) ft.hide(favoriteFragment);
        if (downloadsFragment != null) ft.hide(downloadsFragment); // NEW: Hide Downloads Fragment

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
                break;
                
            case 2:
                if (favoriteFragment == null) {
                    favoriteFragment = new FavoriteFragment();
                    ft.add(R.id.content_frame, favoriteFragment, "FAVORITE");
                } else {
                    ft.show(favoriteFragment);
                }
                break;

            case 3: // NEW: Show Downloads Fragment
                if (downloadsFragment == null) {
                    downloadsFragment = new DownloadsFragment(); // Note: Create DownloadsFragment.java to match this
                    ft.add(R.id.content_frame, downloadsFragment, "DOWNLOADS");
                } else {
                    ft.show(downloadsFragment);
                }
                break;
        }
        
        ft.commit();
        currentPosition = position;
        updateNavigationSelection(position);

        // THEME SWITCHING LOGIC
        if (position == 1) {
            enablePremiumTheme(); // Turn on Dark/Gold mode for Premium
        } else {
            enableNormalTheme();  // Turn on White/Black mode for Home, Favorites & Downloads
        }
    }
    
    private void enablePremiumTheme() {
        int darkColor = ContextCompat.getColor(this, R.color.premium_background);
        int goldColor = ContextCompat.getColor(this, R.color.premium_gold);
        int whiteColor = ContextCompat.getColor(this, R.color.white);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(darkColor);
            window.setNavigationBarColor(darkColor);
            
            int flags = window.getDecorView().getSystemUiVisibility();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }

        if (toolbar != null) toolbar.setBackgroundColor(darkColor);
        if (appBarLayout != null) appBarLayout.setBackgroundColor(darkColor);

        if (btn_menu != null) btn_menu.setColorFilter(goldColor);
        if (notificationButton != null) notificationButton.setColorFilter(goldColor);
        if (profileButton != null) profileButton.setColorFilter(goldColor);
        if (textview1 != null) textview1.setTextColor(whiteColor);

        if (searchLayout != null) {
            searchLayout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.search_premium_bg)));
            if (searchLayout.getChildAt(0) instanceof ImageView) {
                ((ImageView) searchLayout.getChildAt(0)).setColorFilter(goldColor);
            }
            if (searchLayout.getChildAt(1) instanceof TextView) {
                ((TextView) searchLayout.getChildAt(1)).setTextColor(ContextCompat.getColor(this, R.color.gray_medium));
            }
            // Tint the new filter icon in premium mode
            ImageView btnFilter = findViewById(R.id.btn_filter);
            if (btnFilter != null) {
                btnFilter.setColorFilter(goldColor);
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
    }

    private void enableNormalTheme() {
        int whiteColor = ContextCompat.getColor(this, R.color.white);
        int blackColor = ContextCompat.getColor(this, R.color.black);
        int grayColor = ContextCompat.getColor(this, R.color.gray_dark);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(whiteColor);
            window.setNavigationBarColor(whiteColor);
            
            int flags = window.getDecorView().getSystemUiVisibility();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }

        if (toolbar != null) toolbar.setBackgroundColor(whiteColor);
        if (appBarLayout != null) appBarLayout.setBackgroundColor(Color.TRANSPARENT);

        if (btn_menu != null) btn_menu.setColorFilter(blackColor);
        if (notificationButton != null) notificationButton.setColorFilter(blackColor);
        if (profileButton != null) profileButton.setColorFilter(blackColor);
        if (textview1 != null) textview1.setTextColor(blackColor);

        if (searchLayout != null) {
            searchLayout.setBackgroundTintList(null); 
            searchLayout.setBackgroundResource(R.drawable.bg_search); 
            if (searchLayout.getChildAt(0) instanceof ImageView) {
                ((ImageView) searchLayout.getChildAt(0)).setColorFilter(grayColor);
            }
            if (searchLayout.getChildAt(1) instanceof TextView) {
                ((TextView) searchLayout.getChildAt(1)).setTextColor(grayColor);
            }
            // Revert the new filter icon in normal mode
            ImageView btnFilter = findViewById(R.id.btn_filter);
            if (btnFilter != null) {
                btnFilter.setColorFilter(grayColor);
            }
        }

        if (bottomNavigationView != null) {
            bottomNavigationView.setBackgroundColor(whiteColor);
            int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] { -android.R.attr.state_checked }
            };
            int[] colors = new int[] { blackColor, grayColor };
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
                    bottomNavigationView.setSelectedItemId(R.id.nav_premium);
                    break;
                case 2:
                    bottomNavigationView.setSelectedItemId(R.id.nav_favorite);
                    break;
                case 3: // NEW: Select Downloads tab
                    bottomNavigationView.setSelectedItemId(R.id.nav_downloads);
                    break;
            }
            setupNavigation(); 
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
                // Add refresh logic for DownloadsFragment if applicable
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
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_premium);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications enabled!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notifications disabled. Enable in Settings.", Toast.LENGTH_LONG).show();
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
    
    @Override
    public void onBackPressed() {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                finishAffinity();
            } else {
                finish();
            }
        });
        
        btnNo.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
