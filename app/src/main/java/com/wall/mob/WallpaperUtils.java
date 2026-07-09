package com.wall.mob;

import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.RejectedExecutionException;

import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;

public class WallpaperUtils {
    private static AlertDialog progressDialog;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String FILE_PROVIDER_AUTHORITY = "com.wall.mob.provider";
    private static final int WALLPAPER_SYSTEM = WallpaperManager.FLAG_SYSTEM;
    private static final int WALLPAPER_LOCK = WallpaperManager.FLAG_LOCK;

    public static void showProgressDialog(WallpaperDetailsActivity activity, String message, boolean cancelable) {
        if (activity.isDestroyedOrFinishing()) {
            Log.w("WallpaperUtils", "Cannot show progress dialog: Activity is destroyed or finishing");
            return;
        }

        mainHandler.post(() -> {
            try {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                // Create a custom AlertDialog with a progress indicator
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                View dialogView = LayoutInflater.from(activity).inflate(R.layout.progress_dialog, null);

                TextView messageView = dialogView.findViewById(R.id.progress_message);
                if (messageView != null) {
                    messageView.setText(message);
                }

                builder.setView(dialogView);
                builder.setCancelable(cancelable);
                progressDialog = builder.create();

                // Make the background transparent
                if (progressDialog.getWindow() != null) {
                    progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                progressDialog.show();
            } catch (Exception e) {
                Log.e("WallpaperUtils", "Failed to show progress dialog: " + e.getMessage(), e);
            }
        });
    }

    public static void dismissProgressDialog(WallpaperDetailsActivity activity) {
        mainHandler.post(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    Log.e("WallpaperUtils", "Failed to dismiss progress dialog: " + e.getMessage(), e);
                }
            }
        });
    }

    public static Bitmap getCenterCroppedBitmap(Bitmap original, int targetWidth, int targetHeight) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        float scale = Math.max((float) targetWidth / originalWidth, (float) targetHeight / originalHeight);
        int scaledWidth = Math.round(originalWidth * scale);
        int scaledHeight = Math.round(originalHeight * scale);

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true);

        if (scaledWidth < targetWidth || scaledHeight < targetHeight) {
            scaledBitmap.recycle();
            throw new IllegalStateException("Scaled bitmap smaller than target: " + scaledWidth + "x" + scaledHeight);
        }

        int cropLeft = (scaledWidth - targetWidth) / 2;
        int cropTop = (scaledHeight - targetHeight) / 2;
        Bitmap cropped = Bitmap.createBitmap(scaledBitmap, cropLeft, cropTop, targetWidth, targetHeight);

        if (scaledBitmap != original) {
            scaledBitmap.recycle();
        }

        return cropped;
    }

    public static void setWallpaperFromBitmap(WallpaperDetailsActivity activity, Bitmap bitmap, int wallpaperFlag) {
        try {
            activity.getExecutorService().execute(() -> {
                try {
                    WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity);
                    wallpaperManager.setWallpaperOffsetSteps(1.0f, 1.0f);

                    final int TARGET_WIDTH = 1080;
                    final int TARGET_HEIGHT = 2460;
                    Bitmap croppedBitmap = getCenterCroppedBitmap(bitmap, TARGET_WIDTH, TARGET_HEIGHT);

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(croppedBitmap, null, true, wallpaperFlag);
                    } else {
                        wallpaperManager.setBitmap(croppedBitmap);
                    }

                    if (croppedBitmap != bitmap) {
                        croppedBitmap.recycle();
                    }

                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            String successMessage = getSuccessMessage(activity, wallpaperFlag);
                            Toast.makeText(activity, successMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (IOException | RuntimeException e) {
                    Log.e("WallpaperUtils", "Failed to set wallpaper: " + e.getMessage(), e);
                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            Toast.makeText(activity, activity.getString(R.string.failed_set_wallpaper, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e("WallpaperUtils", "Executor rejected task, activity may be destroyed", e);
            dismissProgressDialog(activity);
        }
    }

    public static void setWallpaperFromUri(WallpaperDetailsActivity activity, Uri imageUri, int wallpaperFlag) {
        try {
            activity.getExecutorService().execute(() -> {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), imageUri);
                    if (bitmap != null) {
                        setWallpaperFromBitmap(activity, bitmap, wallpaperFlag);
                    } else {
                        throw new IOException("Failed to load bitmap from URI");
                    }
                } catch (IOException | RuntimeException e) {
                    Log.e("WallpaperUtils", "Failed to set wallpaper from URI: " + e.getMessage(), e);
                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            Toast.makeText(activity, activity.getString(R.string.failed_set_wallpaper, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e("WallpaperUtils", "Executor rejected task, activity may be destroyed", e);
            dismissProgressDialog(activity);
        }
    }

    private static String getSuccessMessage(WallpaperDetailsActivity activity, int wallpaperFlag) {
        if (wallpaperFlag == WALLPAPER_SYSTEM) {
            return activity.getString(R.string.wallpaper_set_home_success);
        } else if (wallpaperFlag == WALLPAPER_LOCK) {
            return activity.getString(R.string.wallpaper_set_lock_success);
        } else {
            return activity.getString(R.string.wallpaper_set_both_success);
        }
    }

    public static void saveAndShareBitmap(
        WallpaperDetailsActivity activity,
        Bitmap bitmap,
        Wallpaper wallpaper) {

        try {
            activity.getExecutorService().execute(() -> {
                try {
                    File cacheDir = activity.getExternalCacheDir() != null
                            ? activity.getExternalCacheDir()
                            : activity.getCacheDir();

                    File shareDir = new File(cacheDir, "share");
                    if (!shareDir.exists() && !shareDir.mkdirs()) {
                        throw new IOException("Cannot create share directory");
                    }

                    File shareFile = new File(
                            shareDir,
                            "wallpaper_preview_" + System.currentTimeMillis() + ".jpg"
                    );

                    // 🔻 CREATE LOW QUALITY PREVIEW
                    Bitmap preview = createLowQualityPreview(bitmap);

                    try (FileOutputStream fos = new FileOutputStream(shareFile)) {
                        preview.compress(Bitmap.CompressFormat.JPEG, 60, fos);
                    }

                    if (preview != bitmap && !preview.isRecycled()) {
                        preview.recycle();
                    }

                    Uri shareUri = FileProvider.getUriForFile(
                            activity,
                            FILE_PROVIDER_AUTHORITY,
                            shareFile
                    );

                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            createEnhancedShareIntent(activity, shareUri, wallpaper);
                        }
                    });

                } catch (Exception e) {
                    Log.e("WallpaperUtils", "Share failed", e);
                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        Toast.makeText(activity, activity.getString(R.string.failed_share_preview), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            dismissProgressDialog(activity);
        }
    }

    public static void shareWallpaperFromUri(
        WallpaperDetailsActivity activity,
        Uri imageUri,
        Wallpaper wallpaper) {

        try {
            activity.getExecutorService().execute(() -> {
                try {
                    Bitmap bitmap;

                    if ("file".equals(imageUri.getScheme())) {
                        bitmap = BitmapFactory.decodeFile(imageUri.getPath());
                    } else {
                        bitmap = MediaStore.Images.Media.getBitmap(
                                activity.getContentResolver(),
                                imageUri
                        );
                    }

                    if (bitmap == null) {
                        throw new IOException("Bitmap load failed");
                    }

                    File cacheDir = activity.getExternalCacheDir() != null
                            ? activity.getExternalCacheDir()
                            : activity.getCacheDir();

                    File shareDir = new File(cacheDir, "share");
                    if (!shareDir.exists() && !shareDir.mkdirs()) {
                        throw new IOException("Cannot create share directory");
                    }

                    File shareFile = new File(
                            shareDir,
                            "wallpaper_preview_" + System.currentTimeMillis() + ".jpg"
                    );

                    // 🔻 LOW QUALITY PREVIEW
                    Bitmap preview = createLowQualityPreview(bitmap);

                    try (FileOutputStream fos = new FileOutputStream(shareFile)) {
                        preview.compress(Bitmap.CompressFormat.JPEG, 60, fos);
                    }

                    if (!bitmap.isRecycled()) bitmap.recycle();
                    if (!preview.isRecycled()) preview.recycle();

                    Uri shareUri = FileProvider.getUriForFile(
                            activity,
                            FILE_PROVIDER_AUTHORITY,
                            shareFile
                    );

                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            createEnhancedShareIntent(activity, shareUri, wallpaper);
                        }
                    });

                } catch (Exception e) {
                    Log.e("WallpaperUtils", "Share failed", e);
                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        Toast.makeText(activity, activity.getString(R.string.failed_share_preview), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            dismissProgressDialog(activity);
        }
    }

    private static void createEnhancedShareIntent(
        WallpaperDetailsActivity activity,
        Uri shareUri,
        Wallpaper wallpaper
    ) {
        String originalUrl = buildOriginalUrlWithDetails(wallpaper);
        
        // Show share dialog immediately with original URL (no delay)
        createShareIntentWithUrl(activity, shareUri, originalUrl, wallpaper);
        
        // Optionally shorten URL in background for future use (non-blocking)
        // shortenUrlWithBitly(originalUrl, shortUrl -> {
        //     // Could cache shortened URL for next time
        // });
    }

    private static void createShareIntentWithUrl(WallpaperDetailsActivity activity, Uri shareUri, String shortUrl, Wallpaper wallpaper) {
        try {
            String shareText = buildShareTextWithRealDetails(wallpaper, shortUrl);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/jpeg");
            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.share_subject_prefix, 
                (wallpaper.getTitle() != null ? wallpaper.getTitle() : activity.getString(R.string.wallpaper))));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (shareIntent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.share_via_wallmob)));
            } else {
                Toast.makeText(activity, activity.getString(R.string.no_share_apps), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("WallpaperUtils", "Failed to start share intent: " + e.getMessage(), e);
            Toast.makeText(activity, activity.getString(R.string.failed_share, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private static String buildShareTextWithRealDetails(Wallpaper wallpaper, String shortUrl) {
        StringBuilder shareText = new StringBuilder();
        if (wallpaper.getTitle() != null && !wallpaper.getTitle().isEmpty()) {
            shareText.append("🎨 ").append(wallpaper.getTitle()).append("\n\n");
        }
        if (wallpaper.getPhotographer() != null && !wallpaper.getPhotographer().isEmpty()) {
            shareText.append("📷 By: ").append(wallpaper.getPhotographer()).append("\n");
        }
        if (wallpaper.getSource() != null && !wallpaper.getSource().isEmpty()) {
            shareText.append("🌐 Source: ").append(wallpaper.getSource()).append("\n");
        }
        if (wallpaper.getCategory() != null && !wallpaper.getCategory().isEmpty()) {
            shareText.append("🏷️ Category: ").append(wallpaper.getCategory()).append("\n");
        }
        shareText.append("\n");
        shareText.append("📥 Download: ").append(shortUrl).append("\n\n");
        shareText.append("Shared via WallMob 🖼️\n");
        shareText.append("Discover more amazing wallpapers!");
        return shareText.toString();
    }

    private static String buildOriginalUrlWithDetails(Wallpaper wallpaper) {
        try {
            StringBuilder url = new StringBuilder("https://wallmob.pages.dev/wallpaper?");
            
            if (wallpaper.getTitle() != null && !wallpaper.getTitle().isEmpty()) {
                url.append("title=").append(urlEncode(wallpaper.getTitle())).append("&");
            }
            if (wallpaper.getPhotographer() != null && !wallpaper.getPhotographer().isEmpty()) {
                url.append("author=").append(urlEncode(wallpaper.getPhotographer())).append("&");
            }
            if (wallpaper.getSource() != null && !wallpaper.getSource().isEmpty()) {
                url.append("source=").append(urlEncode(wallpaper.getSource())).append("&");
            }
            if (wallpaper.getCategory() != null && !wallpaper.getCategory().isEmpty()) {
                url.append("category=").append(urlEncode(wallpaper.getCategory())).append("&");
            }
            if (wallpaper.getImageUrl() != null && !wallpaper.getImageUrl().isEmpty()) {
                url.append("image=").append(urlEncode(wallpaper.getImageUrl())).append("&");
            }
            url.append("ts=").append(System.currentTimeMillis());
            
            return url.toString();
        } catch (Exception e) {
            Log.e("WallpaperUtils", "Failed to build share URL", e);
            return "https://wallmob.pages.dev";
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    public static void saveBitmapToGallery(WallpaperDetailsActivity activity, Bitmap bitmap) {
        try {
            activity.getExecutorService().execute(() -> {
                try {
                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    File wallpapersDir = new File(picturesDir, "WallMob");
                    if (!wallpapersDir.exists() && !wallpapersDir.mkdirs()) {
                        throw new IOException("Cannot create WallMob directory");
                    }

                    String fileName = "wallpaper_" + System.currentTimeMillis() + ".jpg";
                    File imageFile = new File(wallpapersDir, fileName);

                    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)) {
                            throw new IOException("Failed to save image");
                        }
                    }

                    MediaStore.Images.Media.insertImage(
                        activity.getContentResolver(),
                        imageFile.getAbsolutePath(),
                        fileName,
                        "Wallpaper downloaded from WallMob"
                    );

                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            Toast.makeText(activity, activity.getString(R.string.wallpaper_saved_gallery), Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e("WallpaperUtils", "Failed to download wallpaper: " + e.getMessage(), e);
                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            Toast.makeText(activity, activity.getString(R.string.failed_download_wallpaper), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e("WallpaperUtils", "Executor rejected task, activity may be destroyed", e);
            dismissProgressDialog(activity);
        }
    }

    public static void downloadWallpaperFromUri(WallpaperDetailsActivity activity, Uri imageUri) {
        try {
            activity.getExecutorService().execute(() -> {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), imageUri);
                    if (bitmap != null) {
                        saveBitmapToGallery(activity, bitmap);
                    } else {
                        throw new IOException("Failed to load bitmap from URI");
                    }
                } catch (IOException | RuntimeException e) {
                    Log.e("WallpaperUtils", "Failed to download wallpaper from URI: " + e.getMessage(), e);
                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            Toast.makeText(activity, activity.getString(R.string.failed_download_wallpaper), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e("WallpaperUtils", "Executor rejected task, activity may be destroyed", e);
            dismissProgressDialog(activity);
        }
    }

    public static void prepareImageForEdit(WallpaperDetailsActivity activity, Bitmap bitmap, String wallpaperTitle) {
        try {
            activity.getExecutorService().execute(() -> {
                try {
                    File cacheDir = activity.getExternalCacheDir() != null ? activity.getExternalCacheDir() : activity.getCacheDir();
                    File editDir = new File(cacheDir, "edit");
                    if (!editDir.exists() && !editDir.mkdirs()) {
                        throw new IOException("Cannot create edit directory");
                    }

                    File tempFile = new File(editDir, "wallpaper_edit_" + System.currentTimeMillis() + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)) {
                            throw new IOException("Failed to save image for editing");
                        }
                    }

                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            Intent editIntent = new Intent(activity, EditActivity.class);
                            editIntent.putExtra("image_path", tempFile.getAbsolutePath());
                            editIntent.putExtra("wallpaper_title", wallpaperTitle);
                            activity.startActivityForResult(editIntent, 1001);
                        }
                    });
                } catch (Exception e) {
                    Log.e("WallpaperUtils", "Failed to prepare image for editing: " + e.getMessage(), e);
                    mainHandler.post(() -> {
                        dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            Toast.makeText(activity, activity.getString(R.string.failed_prepare_image_editing), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e("WallpaperUtils", "Executor rejected task, activity may be destroyed", e);
            dismissProgressDialog(activity);
        }
    }

    private static Bitmap createLowQualityPreview(Bitmap original) {
        int maxSize = 480; // max width or height

        int width = original.getWidth();
        int height = original.getHeight();

        float ratio = Math.min(
                (float) maxSize / width,
                (float) maxSize / height
        );

        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }
    private static void shortenUrlWithBitly(
        String longUrl,
        java.util.function.Consumer<String> callback
) {
    new Thread(() -> {
        try {
            String jsonBody = "{ \"long_url\": \"" + longUrl + "\" }";

            java.net.URL url = new java.net.URL("https://api-ssl.bitly.com/v4/shorten");
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "134104750ac1df4ee516a0cfaafd2c0169e77069");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }

            java.io.InputStream is = conn.getInputStream();
            java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
            String response = scanner.hasNext() ? scanner.next() : "";

            org.json.JSONObject json = new org.json.JSONObject(response);
            String shortLink = json.getString("link");

            mainHandler.post(() -> callback.accept(shortLink));

        } catch (Exception e) {
            Log.e("Bitly", "Shorten failed", e);
            mainHandler.post(() -> callback.accept(longUrl)); // fallback
        }
    }).start();
}
}
// test
