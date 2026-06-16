package com.wall.mob;

import android.content.Context;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UnityAdActivity extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    private static final String TAG = "UnityAdActivity";
    private static final String REWARDED_AD_ID = "Rewarded_Android";
    private static final String PREFS_NAME = "GamePrefs";
    private static final String COINS_KEY = "coins";
    
    private Button showAdButton;
    private TextView coinsTextView;
    private TextView statusTextView;
    private LinearLayout bannerContainer;
    private ListView transactionListView;
    private int currentCoins = 0;
    private SharedPreferences sharedPrefs;
    private UnityAdsManager adsManager;
    private boolean isRewardedAdLoaded = false;
    private TransactionManager transactionManager;
    private ArrayAdapter<String> transactionAdapter;
    private List<String> transactionDisplayList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.unity_ad);

        Log.d(TAG, "========================================");
        Log.d(TAG, "onCreate: Activity started");
        Log.d(TAG, "========================================");

        // Initialize views
        showAdButton = findViewById(R.id.showAdButton);
        coinsTextView = findViewById(R.id.coinsTextView);
        statusTextView = findViewById(R.id.statusTextView);
        bannerContainer = findViewById(R.id.bannerContainer);
        transactionListView = findViewById(R.id.transactionListView);

        // Initialize SharedPreferences and TransactionManager
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        transactionManager = new TransactionManager(this);
        currentCoins = sharedPrefs.getInt(COINS_KEY, 0);
        Log.d(TAG, "onCreate: Loaded coins = " + currentCoins);

        // Initialize transaction list
        transactionDisplayList = new ArrayList<>();
        transactionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, transactionDisplayList);
        transactionListView.setAdapter(transactionAdapter);
        updateTransactionList();

        // Update UI
        updateCoinsDisplay();
        statusTextView.setText(R.string.ad_initializing);

        // Initialize Unity Ads Manager
        adsManager = UnityAdsManager.getInstance(this);
        
        // IMPORTANT: Clear premium status for testing banner ads
        // Remove this line once you've verified banners work
        adsManager.revokePremiumAccess(this);
        Log.d(TAG, "Premium status cleared for banner testing");
        
        adsManager.initializeAds(this);
        Log.d(TAG, "onCreate: UnityAdsManager initialization started");

        // Button click listener
        showAdButton.setOnClickListener(v -> {
            Log.d(TAG, "showAdButton clicked");
            showRewardedAd();
        });

        showAdButton.setEnabled(false);

        // Wait for ads to initialize
        showAdButton.postDelayed(new Runnable() {
            int retryCount = 0;
            
            @Override
            public void run() {
                retryCount++;
                Log.d(TAG, "Checking ads initialization (attempt " + retryCount + ")...");
                
                if (adsManager.isAdsInitialized()) {
                    Log.d(TAG, "✓ Unity Ads initialized successfully!");
                    Log.d(TAG, "Loading rewarded ad...");
                    loadRewardedAd();
                    
                    Log.d(TAG, "Loading banner ad...");
                    loadBannerAd();
                } else {
                    if (retryCount < 10) {
                        Log.w(TAG, "Unity Ads not initialized yet, retrying in 2 seconds...");
                        statusTextView.setText(getString(R.string.ad_waiting_initialization, retryCount));
                        showAdButton.postDelayed(this, 2000);
                    } else {
                        Log.e(TAG, "✗ Unity Ads failed to initialize after 10 attempts");
                        statusTextView.setText(R.string.ad_failed_initialize);
                    }
                }
            }
        }, 2000);
    }

    private void loadRewardedAd() {
        Log.d(TAG, "loadRewardedAd: Loading rewarded ad...");
        statusTextView.setText(R.string.ad_loading_rewarded);

        UnityAds.load(REWARDED_AD_ID, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String adUnitId) {
                Log.d(TAG, "✓ Rewarded ad loaded: " + adUnitId);
                runOnUiThread(() -> {
                    isRewardedAdLoaded = true;
                    showAdButton.setEnabled(true);
                    statusTextView.setText(R.string.ad_loaded_ready);
                });
            }

            @Override
            public void onUnityAdsFailedToLoad(String adUnitId, UnityAds.UnityAdsLoadError error, String message) {
                Log.e(TAG, "✗ Failed to load rewarded ad: " + error + " - " + message);
                runOnUiThread(() -> {
                    isRewardedAdLoaded = false;
                    statusTextView.setText(getString(R.string.ad_failed_load, error));
                    Toast.makeText(UnityAdActivity.this, getString(R.string.ad_load_failed), Toast.LENGTH_LONG).show();

                    // Retry after 5 seconds
                    showAdButton.postDelayed(() -> {
                        Log.d(TAG, "Retrying rewarded ad load...");
                        loadRewardedAd();
                    }, 5000);
                });
            }
        });
    }

    private void showRewardedAd() {
        if (!isRewardedAdLoaded) {
            Log.w(TAG, "showRewardedAd: Ad not ready");
            Toast.makeText(this, getString(R.string.ad_not_ready), Toast.LENGTH_SHORT).show();
            loadRewardedAd();
            return;
        }

        showAdButton.setEnabled(false);
        statusTextView.setText(R.string.ad_showing_rewarded);
        Log.d(TAG, "Showing rewarded ad...");

        UnityAds.show(this, REWARDED_AD_ID, new IUnityAdsShowListener() {
            @Override
            public void onUnityAdsShowStart(String adUnitId) {
                Log.d(TAG, "Ad show started: " + adUnitId);
                runOnUiThread(() -> statusTextView.setText(R.string.ad_started));
            }

            @Override
            public void onUnityAdsShowClick(String adUnitId) {
                Log.d(TAG, "Ad clicked: " + adUnitId);
            }

            @Override
            public void onUnityAdsShowComplete(String adUnitId, UnityAds.UnityAdsShowCompletionState state) {
                Log.d(TAG, "Ad show complete: " + adUnitId + " state: " + state);
                runOnUiThread(() -> {
                    isRewardedAdLoaded = false;

                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        rewardUser();
                        statusTextView.setText(R.string.ad_completed_reward);
                        Log.d(TAG, "✓ User rewarded with 1 coin, total = " + currentCoins);
                    } else if (state == UnityAds.UnityAdsShowCompletionState.SKIPPED) {
                        statusTextView.setText(R.string.ad_skipped_no_reward);
                        Log.w(TAG, "Ad skipped, no reward");
                    }

                    // Reload rewarded ad after 1 second
                    showAdButton.postDelayed(() -> {
                        Log.d(TAG, "Reloading rewarded ad...");
                        loadRewardedAd();
                    }, 1000);
                });
            }

            @Override
            public void onUnityAdsShowFailure(String adUnitId, UnityAds.UnityAdsShowError error, String message) {
                Log.e(TAG, "✗ Ad show failed: " + error + " - " + message);
                runOnUiThread(() -> {
                    isRewardedAdLoaded = false;
                    statusTextView.setText(getString(R.string.ad_show_failed, error));
                    Toast.makeText(UnityAdActivity.this, getString(R.string.ad_failed_show), Toast.LENGTH_SHORT).show();
                    showAdButton.setEnabled(true);
                    loadRewardedAd();
                });
            }
        });
    }

    private void loadBannerAd() {
        Log.d(TAG, "========================================");
        Log.d(TAG, "loadBannerAd: Attempting to load banner");
        Log.d(TAG, "========================================");
        
        if (adsManager.isAdsInitialized()) {
            Log.d(TAG, "Ads initialized, calling adsManager.loadBannerAd()...");
            adsManager.loadBannerAd(this, bannerContainer);
        } else {
            Log.e(TAG, "✗ Unity Ads not initialized, banner cannot load");
            // Retry after 3 seconds
            bannerContainer.postDelayed(() -> {
                Log.d(TAG, "Retrying banner load...");
                loadBannerAd();
            }, 3000);
        }
    }

    private void rewardUser() {
        currentCoins += 1;
        Log.d(TAG, "rewardUser: New coin balance = " + currentCoins);

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(COINS_KEY, currentCoins);
        editor.apply();

        // Log transaction
        transactionManager.addTransaction(new Transaction("Earned Coins", 1, System.currentTimeMillis()));

        updateCoinsDisplay();
        updateTransactionList();

        // Notify other activities to update coin display
        Intent intent = new Intent("COINS_UPDATED");
        intent.putExtra("new_coins", currentCoins);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void updateCoinsDisplay() {
        Log.d(TAG, "updateCoinsDisplay: Coins = " + currentCoins);
        coinsTextView.setText(getString(R.string.coins_count, currentCoins));
    }

    private void updateTransactionList() {
        transactionDisplayList.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        for (Transaction transaction : transactionManager.getTransactions()) {
            String displayText = transaction.toString() + " (" + sdf.format(new Date(transaction.getTimestamp())) + ")";
            transactionDisplayList.add(displayText);
        }
        transactionAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        // Refresh coin balance and transaction list in case they changed
        currentCoins = sharedPrefs.getInt(COINS_KEY, 0);
        updateCoinsDisplay();
        updateTransactionList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Cleaning up ads...");
        if (adsManager != null) {
            adsManager.destroyBanner();
        }
    }
}
// test
