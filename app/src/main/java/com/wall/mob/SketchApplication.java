package com.wall.mob;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.multidex.MultiDexApplication;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Updated SketchApplication with:
 * - Debounced showing of NoInternetActivity to avoid flashing on short network blips
 * - Active probe + NET_CAPABILITY_VALIDATED checks (already present)
 * - Non-blocking FCM token retrieval with retry and retry-on-network-validated
 * - Robust logging
 *
 * Make sure AndroidManifest has:
 *  - <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 *  - <application android:name=".SketchApplication" ...>
 *  - NoInternetActivity declared
 */
public class SketchApplication extends MultiDexApplication implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "SketchApplication";
    private static Context mApplicationContext;

    // Network
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Prevent launching offline activity for very short blips
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingShowOffline;
    private static final long SHOW_OFFLINE_DELAY_MS = 900L; // debounce before showing offline UI

    // Track visible state
    private volatile boolean noInternetActivityVisible = false;

    // Foreground tracking
    private volatile int startedActivities = 0;
    private volatile Activity currentActivity = null;

    // Active probe executor
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor();

    // Shared IO executor for background tasks (loading data, file I/O, parsing)
    private static final ExecutorService ioExecutor = Executors.newFixedThreadPool(4);

    public static ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    // FCM retry
    private static final int FCM_MAX_RETRIES = 5;

    public static Context getContext() {
        return mApplicationContext;
    }

    public void setNoInternetActivityVisible(boolean visible) {
        this.noInternetActivityVisible = visible;
    }

    public boolean isNoInternetActivityVisible() {
        return noInternetActivityVisible;
    }

    private boolean isAppInForeground() {
        return startedActivities > 0;
    }

    private Activity getCurrentActivity() {
        return currentActivity;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        ThemeUtils.applyThemeFromPrefs(this);
        LocaleHelper.applySavedLocale(this);

        mApplicationContext = getApplicationContext();

        Log.d(TAG, "APPLICATION STARTING");

        registerActivityLifecycleCallbacks(this);

        try {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization FAILED", e);
        }

        try {
            NotificationHelper.createNotificationChannel(this);
            Log.d(TAG, "Notification channel created");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create notification channel", e);
        }

        // Start non-blocking FCM token retrieval with retry attempts
        tryFetchFcmTokenWithRetry(0);

        registerNetworkCallback();

        // Initialize wallpaper repository
        WallpaperRepository.getInstance().init(this);

        // Initialize crash handler to catch and log all uncaught exceptions
        CrashHandler.initialize(this);
        Log.d(TAG, "CrashHandler initialized");

        // Wake up backend services
        new Thread(() -> {
            String[] wakeUrls = {
                "https://tool-veyr.onrender.com/health",
                "https://api-server.rahulkumarbknv.workers.dev/?query=nature&page=1&per_page=1"
            };
            for (String url : wakeUrls) {
                try {
                    java.net.HttpURLConnection urlConnection =
                            (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.setConnectTimeout(15000);
                    urlConnection.setReadTimeout(60000);
                    int code = urlConnection.getResponseCode();
                    Log.d(TAG, "Wake response for " + url + ": " + code);
                    urlConnection.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to wake " + url, e);
                }
            }
        }).start();

Log.d(TAG, "APPLICATION STARTED");
    }

    private void registerNetworkCallback() {
        try {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                Log.w(TAG, "ConnectivityManager unavailable - network monitoring disabled");
                return;
            }

            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Log.d(TAG, "Network available (onAvailable) — verifying validation");
                    // Cancel pending show if network comes back quickly
                    cancelPendingShowOffline();

                    try {
                        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
                        boolean validated = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                        Log.d(TAG, "onAvailable caps validated=" + validated);
                        if (!validated) {
                            // Active probe in background
                            verifyInternetReachabilityAndShowOrHide();
                        } else {
                            // Validated — hide UI and retry FCM if needed
                            hideNoInternetIfShown();
                            tryFetchFcmTokenWithRetry(0);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "onAvailable check failed", e);
                        verifyInternetReachabilityAndShowOrHide();
                    }
                }

                @Override
                public void onLost(Network network) {
                    Log.d(TAG, "Network lost (onLost)");
                    // Debounced show to avoid flashes on quick blips
                    scheduleShowOfflineWithDebounce();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    boolean validated = networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    Log.d(TAG, "onCapabilitiesChanged validated=" + validated);
                    if (!validated) {
                        // perform active probe to detect partial connectivity
                        verifyInternetReachabilityAndShowOrHide();
                    } else {
                        // confirmed validated -> hide offline UI & retry FCM
                        cancelPendingShowOffline();
                        hideNoInternetIfShown();
                        tryFetchFcmTokenWithRetry(0);
                    }
                }
            };

            connectivityManager.registerNetworkCallback(request, networkCallback);
            Log.d(TAG, "Network callback registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
        }
    }

    private void scheduleShowOfflineWithDebounce() {
        cancelPendingShowOffline();
        pendingShowOffline = () -> {
            // only show if still appropriate
            if (!noInternetActivityVisible) {
                showNoInternetIfAppForeground();
            } else {
                Log.d(TAG, "Offline UI already visible, skipping scheduled show");
            }
        };
        mainHandler.postDelayed(pendingShowOffline, SHOW_OFFLINE_DELAY_MS);
        Log.d(TAG, "Scheduled offline UI show in " + SHOW_OFFLINE_DELAY_MS + "ms");
    }

    private void cancelPendingShowOffline() {
        if (pendingShowOffline != null) {
            mainHandler.removeCallbacks(pendingShowOffline);
            pendingShowOffline = null;
            Log.d(TAG, "Canceled pending offline UI show");
        }
    }

    private void showNoInternetIfAppForeground() {
        if (!noInternetActivityVisible && isAppInForeground()) {
            try {
                Activity activity = getCurrentActivity();
                Intent i = new Intent(getApplicationContext(), NoInternetActivity.class);
                if (activity != null && !activity.isFinishing()) {
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    activity.startActivity(i);
                    Log.d(TAG, "Started NoInternetActivity from currentActivity");
                } else {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    getApplicationContext().startActivity(i);
                    Log.d(TAG, "Started NoInternetActivity using application context (fallback)");
                }
                noInternetActivityVisible = true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to start NoInternetActivity", e);
            }
        } else {
            Log.d(TAG, "Not launching NoInternetActivity (already visible or app not foreground)");
        }
    }

    private void hideNoInternetIfShown() {
        if (noInternetActivityVisible) {
            try {
                Intent hideIntent = new Intent("com.wall.mob.ACTION_HIDE_NO_INTERNET");
                sendBroadcast(hideIntent);
                noInternetActivityVisible = false;
                Log.d(TAG, "Broadcasted ACTION_HIDE_NO_INTERNET");
            } catch (Exception e) {
                Log.e(TAG, "Error broadcasting hide action", e);
            }
        }
    }

    private void verifyInternetReachabilityAndShowOrHide() {
        probeExecutor.submit(() -> {
            boolean internetOk = false;
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL("https://clients3.google.com/generate_204");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(4000);
                urlConnection.setReadTimeout(4000);
                urlConnection.setInstanceFollowRedirects(false);
                urlConnection.setUseCaches(false);
                urlConnection.connect();

                int code = urlConnection.getResponseCode();
                internetOk = (code == 204);
                Log.d(TAG, "Active probe response code=" + code + " internetOk=" + internetOk);
            } catch (Exception e) {
                Log.d(TAG, "Active probe failed: " + e.getMessage());
                internetOk = false;
            } finally {
                if (urlConnection != null) {
                    try {
                        InputStream is = urlConnection.getInputStream();
                        if (is != null) try { is.close(); } catch (Exception ignore) {}
                    } catch (Exception ignore) {}
                    urlConnection.disconnect();
                }
            }

            final boolean ok = internetOk;
            mainHandler.post(() -> {
                if (ok) {
                    hideNoInternetIfShown();
                    // Retry FCM now that internet is reachable
                    tryFetchFcmTokenWithRetry(0);
                } else {
                    // Schedule showing offline UI (debounced)
                    scheduleShowOfflineWithDebounce();
                }
            });
        });
    }

    /**
     * Non-blocking FCM token retrieval with retries and retry-on-network-validated.
     */
    private void tryFetchFcmTokenWithRetry(int attempt) {
        if (attempt > 0) Log.d(TAG, "Retrying FCM token retrieval, attempt=" + attempt);
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String token = task.getResult();
                    Log.d(TAG, "✓ FCM token retrieved: " + (token != null ? token.substring(0, Math.min(16, token.length())) + "..." : "null"));
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("fcm_token", token)
                        .putLong("fcm_token_timestamp", System.currentTimeMillis())
                        .apply();
                } else {
                    Log.w(TAG, "FCM token attempt failed: " + (task.getException() != null ? task.getException().getMessage() : "unknown"));
                    if (attempt < FCM_MAX_RETRIES) {
                        long delay = Math.min(60_000L, (1L << attempt) * 1000L); // exponential backoff capped at 60s
                        mainHandler.postDelayed(() -> tryFetchFcmTokenWithRetry(attempt + 1), delay);
                        Log.d(TAG, "Scheduled FCM retry in " + delay + "ms");
                    } else {
                        Log.w(TAG, "Exceeded FCM retry attempts");
                    }
                }
            });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        try {
            if (connectivityManager != null && networkCallback != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error unregistering network callback", e);
        }
        try {
            probeExecutor.shutdown();
            probeExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        unregisterActivityLifecycleCallbacks(this);
    }

    // Activity lifecycle callbacks to track foreground state and current Activity
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (activity.getClass().getName().startsWith(getPackageName())) {
            ThemeUtils.applySystemBars(activity);
        }
    }
    @Override
    public void onActivityStarted(Activity activity) {
        startedActivities++;
        currentActivity = activity;
        Log.d(TAG, "onActivityStarted: " + activity.getClass().getSimpleName() + " startedActivities=" + startedActivities);
    }
    @Override
    public void onActivityResumed(Activity activity) {
        currentActivity = activity;
        Log.d(TAG, "onActivityResumed: " + activity.getClass().getSimpleName());
    }
    @Override public void onActivityPaused(Activity activity) {}
    @Override
    public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        Log.d(TAG, "onActivityStopped: " + activity.getClass().getSimpleName() + " startedActivities=" + startedActivities);
        if (currentActivity == activity) currentActivity = null;
    }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override
    public void onActivityDestroyed(Activity activity) {
        if (currentActivity == activity) currentActivity = null;
    }
    
        @Override
    protected void attachBaseContext(Context base) {
        // Apply the saved language globally before the application fully starts
        super.attachBaseContext(LocaleHelper.setLocale(base));
    }
    
}
// test
