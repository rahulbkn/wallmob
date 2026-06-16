package com.wall.mob;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREF_NAME = "settings_prefs";
    private static final String KEY_LANG = "app_lang";
    private static final String DEFAULT_LANG = "en";

    public static Context setLocale(Context context) {
        return updateResources(context, getLanguage(context));
    }

    public static Context setNewLocale(Context context, String language) {
        String normalizedLanguage = normalizeLanguage(language);
        persistLanguage(context, normalizedLanguage);
        applyAppCompatLocale(normalizedLanguage);
        return updateResources(context, normalizedLanguage);
    }

    public static void applySavedLocale(Context context) {
        applyAppCompatLocale(getLanguage(context));
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return normalizeLanguage(prefs.getString(KEY_LANG, DEFAULT_LANG));
    }

    private static void persistLanguage(Context context, String language) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANG, normalizeLanguage(language)).apply();
    }

    private static void applyAppCompatLocale(String language) {
        AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(normalizeLanguage(language)));
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return DEFAULT_LANG;
        }
        return language.trim();
    }

    private static Context updateResources(Context context, String language) {
        Locale locale = Locale.forLanguageTag(normalizeLanguage(language));
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            config.setLocales(localeList);
        } else {
            config.setLocale(locale);
        }

        res.updateConfiguration(config, res.getDisplayMetrics());
        Context localizedContext = context.createConfigurationContext(config);
        return new ContextWrapper(localizedContext);
    }
}
