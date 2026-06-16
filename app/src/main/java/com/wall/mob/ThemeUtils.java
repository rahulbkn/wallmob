package com.wall.mob;  
  
import android.app.Activity;  
import android.content.Context;  
import android.content.SharedPreferences;  
import android.content.res.Configuration;  
import android.os.Build;  
import android.view.View;  
import android.view.Window;  
  
import androidx.appcompat.app.AppCompatDelegate;  
import androidx.core.content.ContextCompat;  
  
public class ThemeUtils {  
  
    private static final String PREF_NAME = "settings_prefs";  
    private static final String KEY_THEME  = "app_theme";  
  
    /** Call once in Application.onCreate() to restore the user's saved theme. */  
    public static void applyThemeFromPrefs(Context context) {  
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);  
        String theme = prefs.getString(KEY_THEME, "system");  
        applyTheme(theme);  
    }  
  
    /** Apply a theme mode string: "light", "dark", or "system". */  
    public static void applyTheme(String theme) {  
        switch (theme) {  
            case "light":  
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);  
                break;  
            case "dark":  
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);  
                break;  
            default:  
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);  
                break;  
        }  
    }  
  
    /** Apply theme-aware status bar and navigation bar colors. */  
    public static void applySystemBars(Activity activity) {  
        Window window = activity.getWindow();  
  
        boolean isNight = (activity.getResources().getConfiguration().uiMode  
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;  
  
        int statusColor = ContextCompat.getColor(activity, R.color.surface);  
        int navColor    = ContextCompat.getColor(activity, R.color.surface);  
  
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);  
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);  
        window.setStatusBarColor(statusColor);  
        window.setNavigationBarColor(navColor);  
  
        int flags = window.getDecorView().getSystemUiVisibility();  
        if (!isNight) {  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)  
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)  
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;  
        } else {  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)  
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)  
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;  
        }  
        window.getDecorView().setSystemUiVisibility(flags);  
    }  
}

// test
