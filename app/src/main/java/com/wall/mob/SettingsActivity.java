package com.wall.mob;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import android.widget.ImageView;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

public class SettingsActivity extends BaseActivity {


    private ImageView ivThemeIcon;

    private TextView tvThemeDesc, tvLanguageDesc, tvCacheSize;
    private LinearLayout btnTheme, btnLanguage, btnClearCache, btnPrivacy;
    
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "settings_prefs";
    private static final String KEY_THEME = "app_theme";
    private static final String KEY_LANG = "app_lang";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme BEFORE setContentView
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
            // Tint navigation icon to theme-aware onSurface color
            toolbar.getNavigationIcon().setTint(androidx.core.content.ContextCompat.getColor(this, R.color.onSurface));
        }

        // Initialize Views
        tvThemeDesc = findViewById(R.id.tv_theme_desc);
        tvLanguageDesc = findViewById(R.id.tv_language_desc);
        tvCacheSize = findViewById(R.id.tv_cache_size);
        
        btnTheme = findViewById(R.id.btn_theme);
        btnLanguage = findViewById(R.id.btn_language);
        btnClearCache = findViewById(R.id.btn_clear_cache);
        btnPrivacy = findViewById(R.id.btn_privacy);
        ivThemeIcon = findViewById(R.id.iv_theme_icon);

        // Load Initial Data
        updateUI();
        calculateCacheSize();

        // Click Listeners
        btnTheme.setOnClickListener(v -> showThemeDialog());
        btnLanguage.setOnClickListener(v -> showLanguageDialog());
        btnClearCache.setOnClickListener(v -> clearAppCache());
        btnPrivacy.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://your-privacy-policy-url.com"));
            startActivity(browserIntent);
        });
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

                sharedPreferences.edit()
                        .putString(KEY_THEME, selectedValue)
                        .apply();

                // Update icon immediately
                updateThemeIcon(selectedValue);

                // Update theme description if updateUI() handles it
                updateUI();

                // Apply theme
                applyTheme(selectedValue);

                dialog.dismiss();
            })
            .show();
}

    private void applyTheme(String themeValue) {
        // Persist the selection so SketchApplication can restore it on cold start
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
                    
                    // Save and set the new language
                    LocaleHelper.setNewLocale(this, selectedCode);
                    
                    dialog.dismiss();
                    updateUI();
                })
                .show();
    }

    // --- CACHE LOGIC ---
    private void calculateCacheSize() {
        new Thread(() -> {
            long size = 0;
            size += getDirSize(getCacheDir());
            size += getDirSize(getExternalCacheDir());
            
            String sizeStr;
            if (size > 1024 * 1024) {
                sizeStr = String.format(Locale.getDefault(), "%.2f MB", (float) size / (1024 * 1024));
            } else {
                sizeStr = String.format(Locale.getDefault(), "%.2f KB", (float) size / 1024);
            }

            new Handler(Looper.getMainLooper()).post(() -> tvCacheSize.setText(sizeStr));
        }).start();
    }

    private long getDirSize(File dir) {
        long size = 0;
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        size += file.length();
                    } else if (file.isDirectory()) {
                        size += getDirSize(file);
                    }
                }
            }
        }
        return size;
    }

    private void clearAppCache() {
        tvCacheSize.setText(getString(R.string.clearing_cache));
        new Thread(() -> {
            deleteDir(getCacheDir());
            deleteDir(getExternalCacheDir());
            Glide.get(this).clearDiskCache();

            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show();
                tvCacheSize.setText(R.string.cache_zero_mb);
            });
        }).start();
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return dir != null && dir.delete();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}

// test
