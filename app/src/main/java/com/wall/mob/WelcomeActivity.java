package com.wall.mob;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.Arrays;

public class WelcomeActivity extends BaseActivity {

    private static final String PREF_NAME = "settings_prefs";
    private static final String KEY_THEME = "app_theme";
    private static final String KEY_LANG = "app_lang";
    public static final String KEY_IS_FIRST_RUN = "isFirstRun";

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before UI initialization
        applyThemeFromPreference();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Initialize Views
        Button btnLanguage = findViewById(R.id.btn_welcome_language);
        Button btnTheme = findViewById(R.id.btn_welcome_theme);
        Button btnGetStarted = findViewById(R.id.btn_get_started);
        TextView tvModel = findViewById(R.id.tv_device_model);
        TextView tvOS = findViewById(R.id.tv_os_version);

        // Set initial button text
        updateButtonText(btnLanguage, btnTheme);

        // Set Device Info
        DeviceUtils deviceUtils = new DeviceUtils(this);
        tvModel.setText(getString(R.string.device_model_label, deviceUtils.getDeviceModel()));
        tvOS.setText(getString(R.string.device_os_label, deviceUtils.getOSVersion()));

        ThemeUtils.applySystemBars(this);

        // Click Listeners
        btnTheme.setOnClickListener(v -> showThemeDialog());
        btnLanguage.setOnClickListener(v -> showLanguageDialog());
        btnGetStarted.setOnClickListener(v -> {
            sharedPreferences.edit().putBoolean(KEY_IS_FIRST_RUN, false).apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void updateButtonText(Button btnLanguage, Button btnTheme) {
        // Update Theme button
        String currentTheme = sharedPreferences.getString(KEY_THEME, "system");
        String[] themeValues = getResources().getStringArray(R.array.theme_values);
        String[] themeOptions = getResources().getStringArray(R.array.theme_options);
        int themeIndex = Arrays.asList(themeValues).indexOf(currentTheme);
        if (themeIndex >= 0) {
            btnTheme.setText(getString(R.string.theme) + ": " + themeOptions[themeIndex]);
        }

        // Update Language button
        String currentLang = LocaleHelper.getLanguage(this);
        String[] langCodes = getResources().getStringArray(R.array.language_codes);
        String[] langOptions = getResources().getStringArray(R.array.language_options);
        int langIndex = Arrays.asList(langCodes).indexOf(currentLang);
        if (langIndex >= 0) {
            btnLanguage.setText(getString(R.string.language) + ": " + langOptions[langIndex]);
        }
    }

    private void applyThemeFromPreference() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, "system");
        ThemeUtils.applyTheme(theme);
    }

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
                    ThemeUtils.applyTheme(selectedValue);
                    dialog.dismiss();
                    restartWelcomeActivity();
                })
                .show();
    }

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
                    restartWelcomeActivity();
                })
                .show();
    }

    private void restartWelcomeActivity() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0);
    }
}
