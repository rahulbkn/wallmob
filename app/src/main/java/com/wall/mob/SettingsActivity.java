@@
         if (getSupportActionBar() != null) {
             getSupportActionBar().setDisplayHomeAsUpEnabled(true);
             getSupportActionBar().setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
-            toolbar.getNavigationIcon().setTint(getResources().getColor(android.R.color.black));
+            // Tint navigation icon to theme-aware onSurface color
+            toolbar.getNavigationIcon().setTint(getResources().getColor(R.color.onSurface));
         }
@@
     private void applyThemeFromPreference() {
         SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
         String theme = prefs.getString(KEY_THEME, "system");
         applyTheme(theme);
     }
@@
     private void showLanguageDialog() {
@@
                 .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                     String selectedCode = codes[which];
                     
                     // Save and set the new language
                     LocaleHelper.setNewLocale(this, selectedCode);
                     
                     dialog.dismiss();
                     updateUI();
                     
                     // Restart app to apply language globally
                     new Handler(Looper.getMainLooper()).postDelayed(() -> {
                         Intent restartIntent = new Intent(this, MainActivity.class);
                         restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                         startActivity(restartIntent);
                         finish();
                     }, 300);
                 })
                 .show();
     }
