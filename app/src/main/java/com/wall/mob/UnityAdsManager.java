package com.wall.mob;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.LinearLayout;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerErrorInfo;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

public class UnityAdsManager {
    private static final String TAG = "UnityAdsManager";
    private static final String UNITY_GAME_ID = "5941135"; // your real game id
    private static final String INTERSTITIAL_AD_ID = "Interstitial_Android";
    private static final String BANNER_AD_ID = "Banner_Android";
    private static final String REWARDED_AD_ID = "Rewarded_Android";

    private static final boolean TEST_MODE = false; // Set to false for production

    private static final String PREFS_NAME = "GamePrefs";
    private static final String PREMIUM_USER_KEY = "is_premium_user";

    private static UnityAdsManager instance;
    private boolean adsInitialized = false;
    private boolean isInterstitialLoaded = false;
    private boolean isRewardedLoaded = false;
    private BannerView bannerView;

    private UnityAdsManager() {}

    public static synchronized UnityAdsManager getInstance(Activity activity) {
        if (instance == null) {
            instance = new UnityAdsManager();
        }
        return instance;
    }

    /** Initialize Unity Ads SDK */
    public void initializeAds(Activity activity) {
        if (adsInitialized) {
            Log.d(TAG, "Unity Ads already initialized");
            preloadInterstitialAd();
            preloadRewardedAd();
            return;
        }

        Log.d(TAG, "Initializing Unity Ads. Test mode = " + TEST_MODE);
        
        // REMOVED: Automatic premium grant in test mode
        // This was preventing banner ads from showing

        UnityAds.initialize(activity, UNITY_GAME_ID, TEST_MODE, new IUnityAdsInitializationListener() {
            @Override
            public void onInitializationComplete() {
                adsInitialized = true;
                Log.d(TAG, "✓ Unity Ads initialization complete");
                preloadInterstitialAd();
                preloadRewardedAd();
            }

            @Override
            public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
                adsInitialized = false;
                Log.e(TAG, "✗ Unity Ads initialization failed: " + error + " - " + message);
            }
        });
    }

    /** Set premium status in SharedPreferences */
    private void setPremiumStatus(Activity activity, boolean isPremium) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREMIUM_USER_KEY, isPremium);
        editor.apply();
        Log.d(TAG, "Premium status set to: " + isPremium);
    }

    /** Check if user is premium */
    public boolean isPremiumUser(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        boolean isPremium = prefs.getBoolean(PREMIUM_USER_KEY, false);
        Log.d(TAG, "isPremiumUser check: " + isPremium);
        return isPremium;
    }

    /** Preload interstitial so it is ready when needed */
    private void preloadInterstitialAd() {
        if (!adsInitialized) {
            Log.w(TAG, "Cannot preload interstitial - ads not initialized");
            return;
        }

        Log.d(TAG, "Preloading interstitial ad...");
        UnityAds.load(INTERSTITIAL_AD_ID, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String placementId) {
                isInterstitialLoaded = true;
                Log.d(TAG, "✓ Interstitial loaded: " + placementId);
            }

            @Override
            public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                isInterstitialLoaded = false;
                Log.e(TAG, "✗ Interstitial load failed: " + error + " - " + message);
            }
        });
    }

    /** Preload rewarded so it is ready when needed */
    private void preloadRewardedAd() {
        if (!adsInitialized) {
            Log.w(TAG, "Cannot preload rewarded - ads not initialized");
            return;
        }

        Log.d(TAG, "Preloading rewarded ad...");
        UnityAds.load(REWARDED_AD_ID, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String placementId) {
                isRewardedLoaded = true;
                Log.d(TAG, "✓ Rewarded loaded: " + placementId);
            }

            @Override
            public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                isRewardedLoaded = false;
                Log.e(TAG, "✗ Rewarded load failed: " + error + " - " + message);
            }
        });
    }

    /** Show interstitial ad */
    public void showInterstitialAd(Activity activity, Runnable onAdComplete) {
        // Skip ads for premium users
        if (isPremiumUser(activity)) {
            Log.d(TAG, "Premium user - skipping interstitial ad");
            onAdComplete.run();
            return;
        }

        if (!isInterstitialLoaded) {
            Log.w(TAG, "Interstitial not loaded, skipping");
            onAdComplete.run();
            preloadInterstitialAd();
            return;
        }

        Log.d(TAG, "Showing interstitial ad...");
        UnityAds.show(activity, INTERSTITIAL_AD_ID, new IUnityAdsShowListener() {
            @Override
            public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                Log.e(TAG, "✗ Interstitial show failed: " + error + " - " + message);
                isInterstitialLoaded = false;
                onAdComplete.run();
                preloadInterstitialAd();
            }

            @Override
            public void onUnityAdsShowStart(String placementId) {
                Log.d(TAG, "Interstitial show started: " + placementId);
                isInterstitialLoaded = false;
            }

            @Override
            public void onUnityAdsShowClick(String placementId) {
                Log.d(TAG, "Interstitial clicked: " + placementId);
            }

            @Override
            public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                Log.d(TAG, "✓ Interstitial show complete: " + state);
                onAdComplete.run();
                preloadInterstitialAd();
            }
        });
    }

    /** Show rewarded ad */
    public void showRewardedAd(Activity activity, Runnable onReward, Runnable onSkip) {
        // Premium users can still watch rewarded ads for bonus coins if they want
        if (isPremiumUser(activity)) {
            Log.d(TAG, "Premium user watching rewarded ad for bonus");
        }

        if (!isRewardedLoaded) {
            Log.w(TAG, "Rewarded not loaded, skipping");
            if (onSkip != null) onSkip.run();
            preloadRewardedAd();
            return;
        }

        Log.d(TAG, "Showing rewarded ad...");
        UnityAds.show(activity, REWARDED_AD_ID, new IUnityAdsShowListener() {
            @Override
            public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                Log.e(TAG, "✗ Rewarded show failed: " + error + " - " + message);
                isRewardedLoaded = false;
                if (onSkip != null) onSkip.run();
                preloadRewardedAd();
            }

            @Override
            public void onUnityAdsShowStart(String placementId) {
                Log.d(TAG, "Rewarded show started: " + placementId);
                isRewardedLoaded = false;
            }

            @Override
            public void onUnityAdsShowClick(String placementId) {
                Log.d(TAG, "Rewarded clicked: " + placementId);
            }

            @Override
            public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                Log.d(TAG, "Rewarded show complete: " + state);
                if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                    if (onReward != null) onReward.run();
                } else {
                    if (onSkip != null) onSkip.run();
                }
                preloadRewardedAd();
            }
        });
    }

    /** Load banner ad */
    public void loadBannerAd(Activity activity, LinearLayout bannerContainer) {
        Log.d(TAG, "========================");
        Log.d(TAG, "loadBannerAd called");
        Log.d(TAG, "========================");
        
        // Skip banner ads for premium users
        if (isPremiumUser(activity)) {
            Log.d(TAG, "Premium user - skipping banner ad");
            if (bannerContainer != null) {
                bannerContainer.removeAllViews();
            }
            return;
        }

        if (!adsInitialized) {
            Log.e(TAG, "✗ Ads not initialized - cannot load banner");
            return;
        }

        Log.d(TAG, "Loading banner ad with ID: " + BANNER_AD_ID);
        Log.d(TAG, "Banner container: " + bannerContainer);

        if (bannerView != null) {
            Log.d(TAG, "Destroying existing banner view");
            bannerContainer.removeAllViews();
            bannerView.destroy();
            bannerView = null;
        }

        try {
            bannerView = new BannerView(activity, BANNER_AD_ID, new UnityBannerSize(320, 50));
            Log.d(TAG, "BannerView created successfully");
            
            bannerView.setListener(new BannerView.IListener() {
                @Override
                public void onBannerLoaded(BannerView bannerAdView) {
                    Log.d(TAG, "========================");
                    Log.d(TAG, "✓✓✓ BANNER LOADED SUCCESSFULLY! ✓✓✓");
                    Log.d(TAG, "========================");
                    activity.runOnUiThread(() -> {
                        bannerContainer.removeAllViews();
                        bannerContainer.addView(bannerAdView);
                        Log.d(TAG, "Banner added to container");
                    });
                }

                @Override
                public void onBannerShown(BannerView bannerAdView) {
                    Log.d(TAG, "✓ Banner shown on screen");
                }
                
                @Override
                public void onBannerClick(BannerView bannerAdView) {
                    Log.d(TAG, "Banner clicked by user");
                }
                
                @Override
                public void onBannerFailedToLoad(BannerView bannerAdView, BannerErrorInfo errorInfo) {
                    Log.e(TAG, "========================");
                    Log.e(TAG, "✗✗✗ BANNER FAILED TO LOAD ✗✗✗");
                    Log.e(TAG, "Error code: " + errorInfo.errorCode);
                    Log.e(TAG, "Error message: " + errorInfo.errorMessage);
                    Log.e(TAG, "========================");
                    
                    // Retry after 10 seconds
                    activity.runOnUiThread(() -> {
                        bannerContainer.postDelayed(() -> {
                            Log.d(TAG, "Retrying banner load...");
                            loadBannerAd(activity, bannerContainer);
                        }, 10000);
                    });
                }
                
                @Override
                public void onBannerLeftApplication(BannerView bannerAdView) {
                    Log.d(TAG, "User left app via banner click");
                }
            });
            
            Log.d(TAG, "Banner listener set, calling load()...");
            bannerView.load();
            Log.d(TAG, "Banner load() called successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "✗ Exception while creating/loading banner: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Destroy banner to avoid leaks */
    public void destroyBanner() {
        Log.d(TAG, "destroyBanner called");
        if (bannerView != null) {
            bannerView.destroy();
            bannerView = null;
            Log.d(TAG, "Banner destroyed");
        }
    }

    /** Manually set premium status (for in-app purchases) */
    public void grantPremiumAccess(Activity activity) {
        setPremiumStatus(activity, true);
        Log.d(TAG, "✓ Premium access granted manually");
    }

    /** Revoke premium status */
    public void revokePremiumAccess(Activity activity) {
        setPremiumStatus(activity, false);
        Log.d(TAG, "✓ Premium access revoked");
    }

    public boolean isAdsInitialized() { 
        Log.d(TAG, "isAdsInitialized: " + adsInitialized);
        return adsInitialized; 
    }
    
    public boolean isInterstitialLoaded() { return isInterstitialLoaded; }
    public boolean isRewardedLoaded() { return isRewardedLoaded; }
}
// test
