@@
-                    if (Math.abs(verticalOffset) - appBarLayout.getTotalScrollRange() == 0) {
-                        // Collapsed (White background) -> Black Icon
-                        toolbar.getNavigationIcon().setTint(Color.BLACK);
-                    } else {
-                        // Expanded (Image background) -> White Icon
-                        toolbar.getNavigationIcon().setTint(Color.WHITE);
-                    }
+                    if (Math.abs(verticalOffset) - appBarLayout.getTotalScrollRange() == 0) {
+                        // Collapsed (surface/background) -> onSurface icon color
+                        toolbar.getNavigationIcon().setTint(ContextCompat.getColor(CategoryActivity.this, R.color.onSurface));
+                    } else {
+                        // Expanded (Image background) -> White Icon
+                        toolbar.getNavigationIcon().setTint(ContextCompat.getColor(CategoryActivity.this, R.color.white));
+                    }
                 }
             });
         }
@@
-        window.setStatusBarColor(Color.WHITE);
-        window.setNavigationBarColor(Color.WHITE);
+        window.setStatusBarColor(ContextCompat.getColor(this, R.color.white));
+        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.white));
@@
-                    int parsedColor = Color.parseColor(colorHex);
-                categoryHeaderImage.setBackgroundColor(parsedColor);
+                    int parsedColor = Color.parseColor(colorHex);
+                categoryHeaderImage.setBackgroundColor(parsedColor);
@@
-                    indicatorDrawable.setStroke(strokePx, Color.WHITE);
+                    indicatorDrawable.setStroke(strokePx, ContextCompat.getColor(CategoryActivity.this, R.color.onSurface));
@@
-                categoryNameText.setText(categoryName + " Tone Wallpapers");
+                categoryNameText.setText(categoryName + " Tone Wallpapers");
@@
-                categoryHeaderImage.setBackgroundColor(Color.parseColor("#F5F5F5"));
+                categoryHeaderImage.setBackgroundColor(ContextCompat.getColor(this, R.color.gray_light));
@@
-                categoryNameText.setText(categoryName + " Wallpapers");
+                categoryNameText.setText(categoryName + " Wallpapers");
             }
         } else {
