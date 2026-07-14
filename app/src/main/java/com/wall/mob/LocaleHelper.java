package com.wall.mob;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class LocaleHelper {

    private static final String PREF_NAME = "settings_prefs";
    private static final String KEY_LANG = "app_lang";
    private static final String DEFAULT_LANG = "en";

    public static void setNewLocale(Context context, String language) {
        String normalized = normalizeLanguage(language);
        persistLanguage(context, normalized);
        applyLocale(normalized);
    }

    public static void applySavedLocale(Context context) {
        applyLocale(getLanguage(context));
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return normalizeLanguage(prefs.getString(KEY_LANG, DEFAULT_LANG));
    }

    private static void persistLanguage(Context context, String language) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANG, normalizeLanguage(language))
                .apply();
    }

    private static void applyLocale(String language) {
        AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(normalizeLanguage(language)));
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return DEFAULT_LANG;
        }
        return language.trim();
    }
}
