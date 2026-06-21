package com.wall.mob;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import java.io.File;
import android.graphics.Bitmap;
import android.widget.Button;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.view.ViewGroup;

public class WallpaperActionsHandler {
    private final WallpaperDetailsActivity activity;
    private final ImageButton favButton;
    private final ImageButton shareBtn;
    private final ImageButton downloadBtn;
    private final ImageButton editBtn;
    private boolean isFavorite = false;
    private SharedPreferences sharedPrefs;
    private TransactionManager transactionManager;
    private static final String PREFS_NAME = "GamePrefs";
    private static final String COINS_KEY = "coins";
    private static final String PREMIUM_USER_KEY = "is_premium_user";
    private static final int UNLIMITED_COINS = Integer.MAX_VALUE;

    public WallpaperActionsHandler(WallpaperDetailsActivity activity, ImageButton favButton, ImageButton shareBtn,
                                  ImageButton downloadBtn, ImageButton editBtn) {
        this.activity = activity;
        this.favButton = favButton;
        this.shareBtn = shareBtn;
        this.downloadBtn = downloadBtn;
        this.editBtn = editBtn;
        this.sharedPrefs = activity.getSharedPreferences(PREFS_NAME, activity.MODE_PRIVATE);
        this.transactionManager = new TransactionManager(activity);
    }

    public void setupButtonListeners(Wallpaper wallpaper) {
        favButton.setOnClickListener(v -> toggleFavorite(wallpaper));
        shareBtn.setOnClickListener(v -> shareWallpaper(wallpaper));
        downloadBtn.setOnClickListener(v -> downloadWallpaper(wallpaper));
        editBtn.setOnClickListener(v -> openImageEditor(wallpaper));
        checkFavoriteStatus(wallpaper);
    }

    private boolean isPremiumUser() {
        return sharedPrefs.getBoolean(PREMIUM_USER_KEY, false);
    }

    private boolean hasEnoughCoins(int requiredCoins) {
        return true; // Unlimited coins for all users
    }

    private void deductCoins(int coins, String action) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(COINS_KEY, UNLIMITED_COINS);
        editor.apply();

        Log.d("WallpaperActionsHandler", "Unlimited coins active. Deduction ignored for: " + action);

        transactionManager.addTransaction(
                new Transaction(action, 0, System.currentTimeMillis())
        );

        Intent intent = new Intent("COINS_UPDATED");
        intent.putExtra("new_coins", UNLIMITED_COINS);
        LocalBroadcastManager.getInstance(activity).sendBroadcast(intent);
    }

    private void showEarnCoinsDialog(int requiredCoins) {
        if (activity.isDestroyed() || activity.isFinishing()) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        View dialogView = inflater.inflate(R.layout.dialog_earn_coins, null);

        TextView tvMessage = (TextView) dialogView.findViewById(R.id.tvMessage);
        Button btnEarnCoins = (Button) dialogView.findViewById(R.id.btnEarnCoins);
        Button btnCancel = (Button) dialogView.findViewById(R.id.btnCancel);

        tvMessage.setText(activity.getString(R.string.coins_required_message, requiredCoins));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnEarnCoins.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, UnityAdActivity.class);
                activity.startActivity(intent);
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }
    
    private void toggleFavorite(Wallpaper wallpaper) {
        if (isFavorite) {
            FavoriteManager.removeFromFavorites(activity, wallpaper);
            isFavorite = false;
            Toast.makeText(activity, activity.getString(R.string.removed_from_favorites), Toast.LENGTH_SHORT).show();
        } else {
            FavoriteManager.addToFavorites(activity, wallpaper);
            isFavorite = true;
            Toast.makeText(activity, activity.getString(R.string.added_to_favorites), Toast.LENGTH_SHORT).show();
        }
        updateFavoriteButton();
    }

    private void updateFavoriteButton() {
        int color = androidx.core.content.ContextCompat.getColor(activity, isFavorite ? android.R.color.holo_red_dark : R.color.white);
        favButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void checkFavoriteStatus(Wallpaper wallpaper) {
        isFavorite = FavoriteManager.isFavorite(activity, wallpaper);
        updateFavoriteButton();
    }

    private void shareWallpaper(Wallpaper wallpaper) {
        if (activity.isDestroyedOrFinishing()) {
            return;
        }

        int coinsRequired = wallpaper.isPremium() ? 2 : 1;
        if (!hasEnoughCoins(coinsRequired)) {
            showEarnCoinsDialog(coinsRequired);
            return;
        }

        String shareType = wallpaper.isPremium() ? activity.getString(R.string.premium) : activity.getString(R.string.normal);
        WallpaperUtils.showProgressDialog(activity, activity.getString(R.string.preparing_share), false);
        if (activity.getEnhancedImageUri() != null) {
            WallpaperUtils.shareWallpaperFromUri(activity, activity.getEnhancedImageUri(), wallpaper);
            deductCoins(coinsRequired, activity.getString(R.string.shared_wallpaper_format, shareType));
        } else {
            try {
                Glide.with(activity)
                    .asBitmap()
                    .load(wallpaper.getImageUrl())
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                            if (!activity.isDestroyedOrFinishing()) {
                                WallpaperUtils.saveAndShareBitmap(activity, bitmap, wallpaper);
                                deductCoins(coinsRequired, activity.getString(R.string.shared_wallpaper_format, shareType));
                            } else {
                                WallpaperUtils.dismissProgressDialog(activity);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            WallpaperUtils.dismissProgressDialog(activity);
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            WallpaperUtils.dismissProgressDialog(activity);
                            if (!activity.isDestroyedOrFinishing()) {
                                Toast.makeText(activity, activity.getString(R.string.failed_load_image_sharing), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            } catch (Exception e) {
                Log.e("WallpaperActionsHandler", "Failed to start wallpaper sharing: " + e.getMessage(), e);
                WallpaperUtils.dismissProgressDialog(activity);
            }
        }
    }

    private void downloadWallpaper(Wallpaper wallpaper) {
        if (activity.isDestroyedOrFinishing()) {
            return;
        }

        // HYBRID STORAGE ACCESS FOR MODERN AND LEGACY ANDROID BUILD COMPLIANCE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Android 6.0 up to Android 12 requires manual approval check for legacy storage writes
            if (activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 200);
                Toast.makeText(activity, activity.getString(R.string.storage_permission_download), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        // NOTE: Android 13+ (Tiramisu and above) natively saves directly to MediaStore without demanding user runtime setup hooks.

        int coinsRequired = wallpaper.isPremium() ? 2 : 1;
        if (!hasEnoughCoins(coinsRequired)) {
            showEarnCoinsDialog(coinsRequired);
            return;
        }

        String downloadType = wallpaper.isPremium() ? activity.getString(R.string.premium) : activity.getString(R.string.normal);
        WallpaperUtils.showProgressDialog(activity, activity.getString(R.string.downloading_wallpaper), false);
        if (activity.getEnhancedImageUri() != null) {
            WallpaperUtils.downloadWallpaperFromUri(activity, activity.getEnhancedImageUri());
            deductCoins(coinsRequired, activity.getString(R.string.downloaded_wallpaper_format, downloadType));
        } else {
            try {
                Glide.with(activity)
                    .asBitmap()
                    .load(wallpaper.getImageUrl())
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                            if (!activity.isDestroyedOrFinishing()) {
                                FileUtil.saveBitmapToPublicGallery(activity, bitmap, wallpaper.getTitle());
                                WallpaperUtils.dismissProgressDialog(activity);
                                Toast.makeText(activity, activity.getString(R.string.wallpaper_saved_gallery), Toast.LENGTH_SHORT).show();
                                deductCoins(coinsRequired, activity.getString(R.string.downloaded_wallpaper_format, downloadType));
                            } else {
                                WallpaperUtils.dismissProgressDialog(activity);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            WallpaperUtils.dismissProgressDialog(activity);
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            WallpaperUtils.dismissProgressDialog(activity);
                            if (!activity.isDestroyedOrFinishing()) {
                                Toast.makeText(activity, activity.getString(R.string.failed_load_image_download), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            } catch (Exception e) {
                Log.e("WallpaperActionsHandler", "Failed to start wallpaper download: " + e.getMessage(), e);
                WallpaperUtils.dismissProgressDialog(activity);
            }
        }
    }

    private void openImageEditor(Wallpaper wallpaper) {
        if (activity.isDestroyedOrFinishing()) {
            return;
        }
        WallpaperUtils.showProgressDialog(activity, "Preparing image for editing...", false);
        try {
            Glide.with(activity)
                .asBitmap()
                .load(wallpaper.getImageUrl())
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                        if (!activity.isDestroyedOrFinishing()) {
                            WallpaperUtils.prepareImageForEdit(activity, bitmap, wallpaper.getTitle());
                        } else {
                            WallpaperUtils.dismissProgressDialog(activity);
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        WallpaperUtils.dismissProgressDialog(activity);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        WallpaperUtils.dismissProgressDialog(activity);
                        if (!activity.isDestroyedOrFinishing()) {
                            Toast.makeText(activity, activity.getString(R.string.failed_load_image_editing), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        } catch (Exception e) {
            Log.e("WallpaperActionsHandler", "Failed to start image editor: " + e.getMessage(), e);
            WallpaperUtils.dismissProgressDialog(activity);
        }
    }

    public Uri handleEditResult(Intent data) {
        String editedImagePath = data.getStringExtra("edited_image_path");
        if (editedImagePath != null) {
            File editedFile = new File(editedImagePath);
            if (editedFile.exists()) {
                return Uri.fromFile(editedFile);
            }
        }
        return null;
    }

    public void cleanup() {
        WallpaperUtils.dismissProgressDialog(activity);
    }

    public static void setPremiumStatus(SharedPreferences prefs, boolean isPremium) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREMIUM_USER_KEY, isPremium);
        editor.apply();
        Log.d("WallpaperActionsHandler", "Premium status updated: " + isPremium);
    }

    public static boolean checkPremiumStatus(SharedPreferences prefs) {
        return prefs.getBoolean(PREMIUM_USER_KEY, false);
    }
}
// test
