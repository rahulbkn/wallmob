package com.wall.mob;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.content.ContextCompat;

public class ThemeUtils {

    public static void applySystemBars(Activity activity) {
        Window window = activity.getWindow();

        boolean isNight = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        int statusColor = ContextCompat.getColor(activity, R.color.surface);
        int navColor = ContextCompat.getColor(activity, R.color.surface);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(statusColor);
            window.setNavigationBarColor(navColor);

            int flags = window.getDecorView().getSystemUiVisibility();

            if (!isNight) {
                // Light background -> dark icons
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                // Dark background -> light icons
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }

            window.getDecorView().setSystemUiVisibility(flags);
        }
    }
}
