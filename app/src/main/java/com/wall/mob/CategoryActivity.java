@@
     private void setupStatusBar() {
-    Window window = getWindow();
-
-    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
-        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
-        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
-
-        window.setStatusBarColor(Color.WHITE);
-        window.setNavigationBarColor(Color.WHITE);
-
-        View decorView = window.getDecorView();
-        int flags = decorView.getSystemUiVisibility();
-
-        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
-            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // black status bar icons
-        }
-
-        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
-            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; // black nav icons
-        }
-
-        decorView.setSystemUiVisibility(flags);
-    }
+    ThemeUtils.applySystemBars(this);
 }
