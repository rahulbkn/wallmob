package com.wall.mob;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    private ImageView ivThemeIcon;
    private TextView tvThemeDesc, tvLanguageDesc, tvCacheSize, tvChangerInterval;
    private LinearLayout btnTheme, btnLanguage, btnClearCache, btnNotifications, btnFeedback, btnRateUs, btnTelegram, btnAbout;
    private LinearLayout btnChangerToggle, btnChangerInterval;
    private SwitchCompat switchNotifications, switchChanger;
    
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "settings_prefs";
    private static final String KEY_THEME = "app_theme";
    private static final String KEY_LANG = "app_lang";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyThemeFromPreference();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ThemeUtils.applySystemBars(this);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.getNavigationIcon().setTint(androidx.core.content.ContextCompat.getColor(this, R.color.onSurface));
        }

        // Initialize Views
        tvThemeDesc = findViewById(R.id.tv_theme_desc);
        tvLanguageDesc = findViewById(R.id.tv_language_desc);
        tvCacheSize = findViewById(R.id.tv_cache_size);
        
        btnTheme = findViewById(R.id.btn_theme);
        btnLanguage = findViewById(R.id.btn_language);
        btnClearCache = findViewById(R.id.btn_clear_cache);
        btnNotifications = findViewById(R.id.btn_notifications);
        btnFeedback = findViewById(R.id.btn_feedback);
        btnRateUs = findViewById(R.id.btn_rate_us);
        btnTelegram = findViewById(R.id.btn_telegram);
        btnAbout = findViewById(R.id.btn_about);
        
        ivThemeIcon = findViewById(R.id.iv_theme_icon);
        switchNotifications = findViewById(R.id.switch_notifications);
        switchChanger = findViewById(R.id.switch_changer);
        tvChangerInterval = findViewById(R.id.tv_changer_interval);
        btnChangerToggle = findViewById(R.id.btn_changer_toggle);
        btnChangerInterval = findViewById(R.id.btn_changer_interval);

        // Load Initial Data
        updateUI();
        calculateCacheSize();

        // Click Listeners
        btnTheme.setOnClickListener(v -> showThemeDialog());
        btnLanguage.setOnClickListener(v -> showLanguageDialog());
        btnClearCache.setOnClickListener(v -> clearAppCache());

        // New Click Listeners
        switchNotifications.setChecked(sharedPreferences.getBoolean(KEY_NOTIFICATIONS, true));
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(KEY_NOTIFICATIONS, isChecked).apply();
        });
        
        btnFeedback.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:your-email@example.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_email_subject));
            startActivity(intent);
        });

        btnRateUs.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
            }
        });

        btnTelegram.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/your_telegram_channel")));
        });

        // Wallpaper Changer
        boolean changerEnabled = WallpaperChangerScheduler.isEnabled(this);
        switchChanger.setChecked(changerEnabled);
        updateChangerIntervalText();

        switchChanger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            WallpaperChangerScheduler.setEnabled(this, isChecked);
        });

        btnChangerToggle.setOnClickListener(v -> {
            switchChanger.setChecked(!switchChanger.isChecked());
        });

        btnChangerInterval.setOnClickListener(v -> showChangerIntervalDialog());
    }

    private void showChangerIntervalDialog() {
        String[] options = {"Every 6 hours", "Every 12 hours", "Every 24 hours", "Every 48 hours"};
        int[] values = {6, 12, 24, 48};
        int current = WallpaperChangerScheduler.getIntervalHours(this);
        int checked = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) { checked = i; break; }
        }

        new AlertDialog.Builder(this)
                .setTitle("Change Interval")
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    WallpaperChangerScheduler.setIntervalHours(this, values[which]);
                    if (switchChanger.isChecked()) {
                        WallpaperChangerScheduler.schedule(this);
                    }
                    updateChangerIntervalText();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateChangerIntervalText() {
        int hours = WallpaperChangerScheduler.getIntervalHours(this);
        tvChangerInterval.setText("Every " + hours + " hours");
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateThemeIcon(String theme) {
    switch (theme) {
        case "light":
            ivThemeIcon.setImageResource(R.drawable.ic_light);
            break;

        case "dark":
            ivThemeIcon.setImageResource(R.drawable.ic_dark);
            break;

        default: // system
            ivThemeIcon.setImageResource(R.drawable.ic_system);
            break;
    }
}

    // Apply theme before UI initialization
    private void applyThemeFromPreference() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, "system");
        ThemeUtils.applyTheme(theme);
    }

    private void updateUI() {
    String currentTheme = sharedPreferences.getString(KEY_THEME, "system");

    updateThemeIcon(currentTheme);

    String[] themeValues = getResources().getStringArray(R.array.theme_values);
    String[] themeOptions = getResources().getStringArray(R.array.theme_options);

    int themeIndex = Arrays.asList(themeValues).indexOf(currentTheme);
    if (themeIndex >= 0) {
        tvThemeDesc.setText(themeOptions[themeIndex]);
    }

    String currentLang = sharedPreferences.getString(KEY_LANG, "en");
    String[] langCodes = getResources().getStringArray(R.array.language_codes);
    String[] langOptions = getResources().getStringArray(R.array.language_options);

    int langIndex = Arrays.asList(langCodes).indexOf(currentLang);
    if (langIndex >= 0) {
        tvLanguageDesc.setText(langOptions[langIndex]);
    }
}

    // --- THEME LOGIC ---
    private void showThemeDialog() {
        String[] options = getResources().getStringArray(R.array.theme_options);
        String[] values = getResources().getStringArray(R.array.theme_values);

        String currentTheme = sharedPreferences.getString(KEY_THEME, "system");
        int checkedItem = Arrays.asList(values).indexOf(currentTheme);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.select_theme))
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    String selectedValue = values[which];
                    sharedPreferences.edit().putString(KEY_THEME, selectedValue).apply();
                    applyTheme(selectedValue);
                    dialog.dismiss();
                    restartApp();
                })
                .show();
    }

    private void applyTheme(String themeValue) {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putString(KEY_THEME, themeValue).apply();
        ThemeUtils.applyTheme(themeValue);
    }

    // --- LANGUAGE LOGIC ---
        
    private void showLanguageDialog() {
        String[] options = getResources().getStringArray(R.array.language_options);
        String[] codes = getResources().getStringArray(R.array.language_codes);
        String currentLang = LocaleHelper.getLanguage(this);
        int checkedItem = java.util.Arrays.asList(codes).indexOf(currentLang);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.select_language))
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    String selectedCode = codes[which];
                    LocaleHelper.setNewLocale(this, selectedCode);
                    dialog.dismiss();
                    restartApp();
                })
                .show();
    }

    // --- CACHE LOGIC ---

private void calculateCacheSize() {
    try {
        long cacheSize = getDirSize(getCacheDir());

        File externalCache = getExternalCacheDir();
        if (externalCache != null) {
            cacheSize += getDirSize(externalCache);
        }

        tvCacheSize.setText(android.text.format.Formatter.formatFileSize(this, cacheSize));
    } catch (Exception e) {
        tvCacheSize.setText(R.string.cache_fallback);
    }
}

private void clearAppCache() {
    try {
        Glide.get(this).clearMemory();

        new Thread(() -> {
            try {
                Glide.get(this).clearDiskCache();
            } catch (Exception ignored) {
            }

            deleteDir(getCacheDir());

            File externalCache = getExternalCacheDir();
            if (externalCache != null) {
                deleteDir(externalCache);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                calculateCacheSize();
                Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
            });
        }).start();

    } catch (Exception e) {
        Toast.makeText(this, R.string.failed_to_clear_cache, Toast.LENGTH_SHORT).show();
    }
}

private long getDirSize(File dir) {
    long size = 0;

    if (dir == null || !dir.exists()) {
        return 0;
    }

    File[] files = dir.listFiles();
    if (files == null) {
        return 0;
    }

    for (File file : files) {
        if (file.isDirectory()) {
            size += getDirSize(file);
        } else {
            size += file.length();
        }
    }

    return size;
}

private boolean deleteDir(File dir) {
    if (dir == null || !dir.exists()) {
        return false;
    }

    File[] files = dir.listFiles();
    if (files != null) {
        for (File file : files) {
            if (file.isDirectory()) {
                deleteDir(file);
            } else {
                file.delete();
            }
        }
    }

    return dir.delete();
}

    // --- RESTART LOGIC ---
    private void restartApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }
}
