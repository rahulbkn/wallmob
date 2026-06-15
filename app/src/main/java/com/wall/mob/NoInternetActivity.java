@@
     private void setupStatusBar() {
-        Window window = getWindow();
-        
-        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
-            // Android 11+ (API 30+)
-            WindowCompat.setDecorFitsSystemWindows(window, false);
-            window.setStatusBarColor(Color.TRANSPARENT);
-            window.setNavigationBarColor(Color.TRANSPARENT);
-            
-            // Light status and navigation bars (dark icons)
-            WindowCompat.getInsetsController(window, window.getDecorView())
-                    .setAppearanceLightStatusBars(true);
-            WindowCompat.getInsetsController(window, window.getDecorView())
-                    .setAppearanceLightNavigationBars(true);
-                    
-        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
-            // Android 8.0+ (API 26+)
-            window.getDecorView().setSystemUiVisibility(
-                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
-                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
-                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
-                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
-            );
-            window.setStatusBarColor(Color.WHITE);
-            window.setNavigationBarColor(Color.WHITE);
-            
-        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
-            // Android 6.0+ (API 23+)
-            window.getDecorView().setSystemUiVisibility(
-                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
-                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
-                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
-            );
-            window.setStatusBarColor(Color.WHITE);
-            window.setNavigationBarColor(Color.BLACK);
-        } else {
-            // Android 5.0+ (API 21+)
-            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
-            window.setStatusBarColor(Color.BLACK);
-            window.setNavigationBarColor(Color.BLACK);
-        }
+        // Use ThemeUtils to keep system bars consistent with DayNight theme
+        ThemeUtils.applySystemBars(this);
     }
