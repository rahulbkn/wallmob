package com.wall.mob;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NoInternetActivity with modern UI styling:
 * - Light status bar with dark icons
 * - Light navigation bar with dark icons
 * - Edge-to-edge display
 * - Uses NET_CAPABILITY_VALIDATED when available
 * - Falls back to an active HTTP probe (clients3.google.com/generate_204)
 * - Ensures a minimum visible time to avoid flicker
 */
public class NoInternetActivity extends BaseActivity {


    private static final String TAG = "NoInternetActivity";
    private BroadcastReceiver hideReceiver;
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Minimum time to keep this activity visible to avoid flicker (ms)
    private static final long MIN_VISIBLE_MS = 800L;
    private long shownAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Configure status and navigation bars BEFORE setContentView
        setupSystemBars();
        
        setContentView(R.layout.activity_no_internet);

        Log.d(TAG, "onCreate");

        try {
            ((SketchApplication) getApplication()).setNoInternetActivityVisible(true);
        } catch (Exception e) {
            Log.w(TAG, "Unable to set visibility flag on application", e);
        }

        TextView retryText = findViewById(R.id.no_internet_message);
        Button retryButton = findViewById(R.id.no_internet_retry);
        Button settingsButton = findViewById(R.id.no_internet_settings);

        retryButton.setOnClickListener(v -> {
            retryText.setText(R.string.checking_connection);
            performActiveProbe(result -> {
                if (result) {
                    Log.d(TAG, "Retry probe: online -> attemptFinish()");
                    attemptFinish();
                } else {
                    Log.d(TAG, "Retry probe: still offline");
                    retryText.setText(R.string.still_offline);
                }
            });
        });

        settingsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        hideReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.wall.mob.ACTION_HIDE_NO_INTERNET".equals(intent.getAction())) {
                    Log.d(TAG, "Received ACTION_HIDE_NO_INTERNET");
                    attemptFinish();
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(hideReceiver, new IntentFilter("com.wall.mob.ACTION_HIDE_NO_INTERNET"), Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(hideReceiver, new IntentFilter("com.wall.mob.ACTION_HIDE_NO_INTERNET"));
        }
    }

    /**
     * Setup system bars for modern edge-to-edge UI with light background
     */
    private void setupSystemBars() {
        // Use ThemeUtils to keep system bars consistent with DayNight theme
        ThemeUtils.applySystemBars(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart - checking network availability/validation");
        // If system reports validated, finish immediately
        if (isNetworkValidated()) {
            Log.d(TAG, "Network reported VALIDATED by system -> finish()");
            finish();
            return;
        }

        // Otherwise run an active probe; finish only when probe shows internet reachable
        performActiveProbe(result -> {
            if (result) {
                Log.d(TAG, "Active probe succeeded in onStart -> attemptFinish()");
                attemptFinish();
            } else {
                Log.d(TAG, "Active probe failed in onStart -> stay on offline screen");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        shownAt = System.currentTimeMillis();
        Log.d(TAG, "onResume shownAt=" + shownAt);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        try { unregisterReceiver(hideReceiver); } catch (Exception ignored) {}
        try { ((SketchApplication) getApplication()).setNoInternetActivityVisible(false); } catch (Exception ignored) {}
        try { probeExecutor.shutdownNow(); } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (isNetworkValidated()) {
            super.onBackPressed();
        } else {
            Log.d(TAG, "Back pressed while offline - ignored");
        }
    }

    private boolean isNetworkValidated() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) {
                Log.d(TAG, "isNetworkValidated: activeNetwork == null");
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
            boolean validated = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            Log.d(TAG, "isNetworkValidated -> " + validated);
            return validated;
        } catch (Exception e) {
            Log.w(TAG, "isNetworkValidated exception", e);
            return false;
        }
    }

    private void performActiveProbe(ProbeCallback callback) {
        probeExecutor.submit(() -> {
            boolean ok = false;
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL("https://clients3.google.com/generate_204");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(3500);
                urlConnection.setReadTimeout(3500);
                urlConnection.setInstanceFollowRedirects(false);
                urlConnection.setUseCaches(false);
                urlConnection.connect();
                int code = urlConnection.getResponseCode();
                ok = (code == 204);
                Log.d(TAG, "performActiveProbe response=" + code + " ok=" + ok);
            } catch (Exception e) {
                Log.d(TAG, "performActiveProbe failed: " + e.getMessage());
                ok = false;
            } finally {
                if (urlConnection != null) {
                    try {
                        InputStream is = urlConnection.getInputStream();
                        if (is != null) try { is.close(); } catch (Exception ignore) {}
                    } catch (Exception ignore) {}
                    urlConnection.disconnect();
                }
            }
            final boolean result = ok;
            mainHandler.post(() -> {
                try { callback.onResult(result); } catch (Exception e) { Log.w(TAG, "probe callback failed", e); }
            });
        });
    }

    /**
     * Attempt to finish the activity, but ensure we stay visible at least MIN_VISIBLE_MS to avoid flicker.
     * If minimum not reached, schedule finish for remaining time.
     */
    private void attemptFinish() {
        long now = System.currentTimeMillis();
        long elapsed = now - shownAt;
        if (elapsed >= MIN_VISIBLE_MS) {
            Log.d(TAG, "Minimum visible time passed (" + elapsed + "ms) -> finish()");
            finish();
        } else {
            long delay = MIN_VISIBLE_MS - elapsed;
            Log.d(TAG, "Minimum visible time NOT passed (elapsed=" + elapsed + "ms). Scheduling finish in " + delay + "ms");
            mainHandler.postDelayed(this::finish, delay);
        }
    }

    private interface ProbeCallback { void onResult(boolean reachable); }
}
// test
