@@
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
-            Window window = getWindow();
-            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
-            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
-            window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
-            window.setNavigationBarColor(ContextCompat.getColor(this, android.R.color.white));
-
-            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
-                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
-            }
-
-            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
-                int flags = window.getDecorView().getSystemUiVisibility();
-                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
-                window.getDecorView().setSystemUiVisibility(flags);
-            }
+            // Use ThemeUtils to apply system bar colors according to current theme (handles night mode)
+            ThemeUtils.applySystemBars(this);
         }
@@
         setClickListeners();
     }
