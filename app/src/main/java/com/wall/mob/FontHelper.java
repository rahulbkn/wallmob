package com.wall.mob;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

public class FontHelper {

    private static final String PREF_NAME = "settings_prefs";
    private static final String KEY_TEXT_SIZE = "app_text_size";
    private static final String DEFAULT_SIZE = "normal";

    public static Context setFontScale(Context context) {
        return updateResources(context, getTextSize(context));
    }

    public static String getTextSize(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TEXT_SIZE, DEFAULT_SIZE);
    }

    private static float getScaleFactor(String size) {
        switch (size) {
            case "small":
                return 0.85f;
            case "large":
                return 1.15f;
            case "normal":
            default:
                return 1.0f;
        }
    }

    private static Context updateResources(Context context, String size) {
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.fontScale = getScaleFactor(size);

        res.updateConfiguration(config, res.getDisplayMetrics());
        Context fontContext = context.createConfigurationContext(config);
        return new ContextWrapper(fontContext);
    }
}
