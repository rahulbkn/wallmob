@@
         int darkColor = Color.parseColor("#121212"); 
-        int goldColor = Color.parseColor("#C9A84C"); 
-        int whiteColor = Color.WHITE;
+        int darkColor = ContextCompat.getColor(this, R.color.premium_background);
+        int goldColor = ContextCompat.getColor(this, R.color.premium_gold);
+        int whiteColor = ContextCompat.getColor(this, R.color.white);
@@
-            if (toolbar != null) toolbar.setBackgroundColor(darkColor);
-        if (appBarLayout != null) appBarLayout.setBackgroundColor(darkColor);
+        if (toolbar != null) toolbar.setBackgroundColor(darkColor);
+        if (appBarLayout != null) appBarLayout.setBackgroundColor(darkColor);
@@
-            searchLayout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2A2A2A")));
+            searchLayout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.search_premium_bg)));
@@
-            if (searchLayout.getChildAt(1) instanceof TextView) {
-                ((TextView) searchLayout.getChildAt(1)).setTextColor(Color.LTGRAY);
-            }
+            if (searchLayout.getChildAt(1) instanceof TextView) {
+                ((TextView) searchLayout.getChildAt(1)).setTextColor(ContextCompat.getColor(this, R.color.gray_medium));
+            }
@@
-            bottomNavigationView.setBackgroundColor(darkColor);
+            bottomNavigationView.setBackgroundColor(darkColor);
@@
-        int whiteColor = Color.WHITE;
-        int blackColor = Color.BLACK;
-        int grayColor = Color.parseColor("#666666");
+        int whiteColor = ContextCompat.getColor(this, R.color.white);
+        int blackColor = ContextCompat.getColor(this, R.color.black);
+        int grayColor = ContextCompat.getColor(this, R.color.gray_dark);
@@
-            Window window = getWindow();
-            window.setStatusBarColor(whiteColor);
-            window.setNavigationBarColor(whiteColor);
+            Window window = getWindow();
+            window.setStatusBarColor(whiteColor);
+            window.setNavigationBarColor(whiteColor);
@@
-        if (toolbar != null) toolbar.setBackgroundColor(whiteColor);
+        if (toolbar != null) toolbar.setBackgroundColor(whiteColor);
