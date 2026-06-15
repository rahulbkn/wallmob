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
  
public class SettingsActivity extends AppCompatActivity {  
  
    private static final String PREF_NAME = "settings_prefs";  
    private static final String KEY_THEME  = "app_theme";  
  
    private TextView tvThemeDesc;  
    private TextView tvLanguageDesc;  
    private TextView tvCacheSize;  
  
    @Override  
    protected void attachBaseContext(Context base) {  
        super.attachBaseContext(LocaleHelper.setLocale(base));  
    }  
  
    @Override  
    protected void onCreate(Bundle savedInstanceState) {  
        applyThemeFromPreference();  
        super.onCreate(savedInstanceState);  
        setContentView(R.layout.activity_settings);  
  
        Toolbar toolbar = findViewById(R.id.toolbar);  
        setSupportActionBar(toolbar);  
        if (getSupportActionBar() != null) {  
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);  
            getSupportActionBar().setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material);  
            getSupportActionBar().setTitle(getString(R.string.settings));  
        }  
        if (toolbar.getNavigationIcon() != null) {  
            toolbar.getNavigationIcon().setTint(getResources().getColor(R.color.onSurface));  
        }  
  
        ThemeUtils.applySystemBars(this);  
  
        tvThemeDesc    = findViewById(R.id.tv_theme_desc);  
        tvLanguageDesc = findViewById(R.id.tv_language_desc);  
        tvCacheSize    = findViewById(R.id.tv_cache_size);  
  
        LinearLayout btnTheme      = findViewById(R.id.btn_theme);  
        LinearLayout btnLanguage   = findViewById(R.id.btn_language);  
        LinearLayout btnClearCache = findViewById(R.id.btn_clear_cache);  
        LinearLayout btnPrivacy    = findViewById(R.id.btn_privacy);  
  
        btnTheme.setOnClickListener(v -> showThemeDialog());  
        btnLanguage.setOnClickListener(v -> showLanguageDialog());  
        btnClearCache.setOnClickListener(v -> clearCache());  
        if (btnPrivacy != null) {  
            btnPrivacy.setOnClickListener(v ->  
                startActivity(new Intent(Intent.ACTION_VIEW,  
                        Uri.parse("https://wallmob.pages.dev/privacy"))));  
        }  
  
        updateUI();  
        calculateCacheSize();  
    }  
  
    @Override  
    public boolean onOptionsItemSelected(MenuItem item) {  
        if (item.getItemId() == android.R.id.home) {  
            onBackPressed();  
            return true;  
        }  
        return super.onOptionsItemSelected(item);  
    }  
  
    // ── Theme ────────────────────────────────────────────────────────────────  
  
    private void applyThemeFromPreference() {  
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);  
        String theme = prefs.getString(KEY_THEME, "system");  
        ThemeUtils.applyTheme(theme);  
    }  
  
    private void showThemeDialog() {  
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);  
        String current = prefs.getString(KEY_THEME, "system");  
  
        String[] options = {  
            getString(R.string.system_default),  
            getString(R.string.light),  
            getString(R.string.dark)  
        };  
        String[] codes = {"system", "light", "dark"};  
  
        int checkedItem = 0;  
        for (int i = 0; i < codes.length; i++) {  
            if (codes[i].equals(current)) { checkedItem = i; break; }  
        }  
  
        new AlertDialog.Builder(this)  
            .setTitle(getString(R.string.select_theme))  
            .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {  
                String selected = codes[which];  
                prefs.edit().putString(KEY_THEME, selected).apply();  
                ThemeUtils.applyTheme(selected);  
                dialog.dismiss();  
                updateUI();  
                recreate();  
            })  
            .show();  
    }  
  
    // ── Language ─────────────────────────────────────────────────────────────  
  
    private void showLanguageDialog() {  
        String current = LocaleHelper.getLanguage(this);  
  
        String[] options = {  
            getString(R.string.english),  
            getString(R.string.hindi),  
            getString(R.string.japanese)  
        };  
        String[] codes = {"en", "hi", "ja"};  
  
        int checkedItem = 0;  
        for (int i = 0; i < codes.length; i++) {  
            if (codes[i].equals(current)) { checkedItem = i; break; }  
        }  
  
        new AlertDialog.Builder(this)  
            .setTitle(getString(R.string.select_language))  
            .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {  
                String selectedCode = codes[which];  
                LocaleHelper.setNewLocale(this, selectedCode);  
                dialog.dismiss();  
                updateUI();  
  
                new Handler(Looper.getMainLooper()).postDelayed(() -> {  
                    Intent restartIntent = new Intent(this, MainActivity.class);  
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);  
                    startActivity(restartIntent);  
                    finish();  
                }, 300);  
            })  
            .show();  
    }  
  
    // ── Cache ─────────────────────────────────────────────────────────────────  
  
    private void clearCache() {  
        new Thread(() -> {  
            Glide.get(this).clearDiskCache();  
            runOnUiThread(() -> {  
                Glide.get(this).clearMemory();  
                Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show();  
                calculateCacheSize();  
            });  
        }).start();  
    }  
  
    private void calculateCacheSize() {  
        new Thread(() -> {  
            long bytes = 0;  
            try { bytes = getFolderSize(getCacheDir()); } catch (Exception ignored) {}  
            final String size = formatSize(bytes);  
            runOnUiThread(() -> { if (tvCacheSize != null) tvCacheSize.setText(size); });  
        }).start();  
    }  
  
    private long getFolderSize(java.io.File dir) {  
        long size = 0;  
        if (dir != null && dir.listFiles() != null) {  
            for (java.io.File f : dir.listFiles())  
                size += f.isDirectory() ? getFolderSize(f) : f.length();  
        }  
        return size;  
    }  
  
    private String formatSize(long bytes) {  
        if (bytes < 1024) return bytes + " B";  
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);  
        return String.format("%.1f MB", bytes / (1024.0 * 1024));  
    }  
  
    // ── UI update ─────────────────────────────────────────────────────────────  
  
    private void updateUI() {  
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);  
        String theme = prefs.getString(KEY_THEME, "system");  
        String lang  = LocaleHelper.getLanguage(this);  
  
        if (tvThemeDesc != null) {  
            switch (theme) {  
                case "light": tvThemeDesc.setText(getString(R.string.light)); break;  
                case "dark":  tvThemeDesc.setText(getString(R.string.dark));  break;  
                default:      tvThemeDesc.setText(getString(R.string.system_default)); break;  
            }  
        }  
  
        if (tvLanguageDesc != null) {  
            switch (lang) {  
                case "hi": tvLanguageDesc.setText(getString(R.string.hindi));    break;  
                case "ja": tvLanguageDesc.setText(getString(R.string.japanese)); break;  
                default:   tvLanguageDesc.setText(getString(R.string.english));  break;  
            }  
        }  
    }  
}
