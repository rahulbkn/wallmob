package com.wall.mob;

import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;
import android.graphics.Color;
import android.widget.Button;
import android.view.ViewGroup;
import android.util.Log;

public class SlideToSetHandler {
    private final WallpaperDetailsActivity activity;
    private final View slideToSetContainer;
    private final View slideThumb;
    private final TextView slideText;
    private float initialX;
    private boolean isSliding = false;
    private int thumbMaxPosition;
    private static final int WALLPAPER_SYSTEM = WallpaperManager.FLAG_SYSTEM;
    private static final int WALLPAPER_LOCK = WallpaperManager.FLAG_LOCK;
    private SharedPreferences sharedPrefs;
    private static final String PREFS_NAME = "GamePrefs";
    private static final String COINS_KEY = "coins";
    private static final int UNLIMITED_COINS = Integer.MAX_VALUE;

    public SlideToSetHandler(WallpaperDetailsActivity activity, View slideToSetContainer, View slideThumb, TextView slideText) {
        this.activity = activity;
        this.slideToSetContainer = slideToSetContainer;
        this.slideThumb = slideThumb;
        this.slideText = slideText;
        this.sharedPrefs = activity.getSharedPreferences(PREFS_NAME, activity.MODE_PRIVATE);
    }

    private boolean hasEnoughCoins(int requiredCoins) {
        return true; // Always enough coins
    }

    private void deductCoins(int coins) {
        // Force unlimited coins
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(COINS_KEY, UNLIMITED_COINS);
        editor.apply();

        Log.d("SlideToSetHandler", "Unlimited coins enabled. Deduction ignored: " + coins);

        // Notify UI (important)
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

        // Use regular theme instead of translucent
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // Remove default background and set transparent window
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // Remove default padding
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

    public void setupSlideToSet(Wallpaper wallpaper) {
        slideToSetContainer.post(() -> thumbMaxPosition = slideToSetContainer.getWidth() - slideThumb.getWidth());

        slideThumb.setOnTouchListener((v, event) -> {
            // FIX 1: Prevent division by zero if layout hasn't fully rendered
            if (thumbMaxPosition <= 0) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = event.getRawX();
                    isSliding = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (isSliding) {
                        float deltaX = event.getRawX() - initialX;
                        if (deltaX > 0) {
                            float newX = Math.min(deltaX, thumbMaxPosition);
                            slideThumb.setTranslationX(newX);
                            float progress = newX / thumbMaxPosition;
                            slideText.setAlpha(1.0f - progress);
                            slideToSetContainer.setBackgroundResource(progress > 0.7f ? R.drawable.slide_bg_active : R.drawable.slide_bg);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isSliding) {
                        float progress = slideThumb.getTranslationX() / thumbMaxPosition;
                        if (progress > 0.7f) {
                            int coinsRequired = wallpaper.isPremium() ? 2 : 1;
                            if (!hasEnoughCoins(coinsRequired)) {
                                showEarnCoinsDialog(coinsRequired);
                                resetSlideButton();
                            } else {
                                showWallpaperSelectionDialog(wallpaper);
                                slideThumb.animate().translationX(thumbMaxPosition).setDuration(200).start();
                                new Handler(Looper.getMainLooper()).postDelayed(this::resetSlideButton, 1000);
                            }
                        } else {
                            resetSlideButton();
                        }
                        isSliding = false;
                    }
                    return true;
            }
            return false;
        });
    }

    private void showWallpaperSelectionDialog(Wallpaper wallpaper) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Light_Dialog);
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_wallpaper_selection, null);

        MaterialButton btnHomeScreen = dialogView.findViewById(R.id.btn_home_screen);
        MaterialButton btnLockScreen = dialogView.findViewById(R.id.btn_lock_screen);
        MaterialButton btnBoth = dialogView.findViewById(R.id.btn_both);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);

        builder.setView(dialogView);
        builder.setCancelable(true);

        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnHomeScreen.setOnClickListener(v -> {
            dialog.dismiss();
            setAsWallpaper(wallpaper, WALLPAPER_SYSTEM);
        });

        btnLockScreen.setOnClickListener(v -> {
            dialog.dismiss();
            setAsWallpaper(wallpaper, WALLPAPER_LOCK);
        });

        btnBoth.setOnClickListener(v -> {
            dialog.dismiss();
            setAsWallpaper(wallpaper, WALLPAPER_SYSTEM | WALLPAPER_LOCK);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void resetSlideButton() {
        slideThumb.animate().translationX(0).setDuration(300).start();
        slideText.animate().alpha(1.0f).setDuration(300).start();
        slideToSetContainer.setBackgroundResource(R.drawable.slide_bg);
    }

    private void setAsWallpaper(Wallpaper wallpaper, int wallpaperFlag) {
        if (activity.isDestroyedOrFinishing()) {
            return;
        }

        int coinsRequired = wallpaper.isPremium() ? 3 : 1;
        if (!hasEnoughCoins(coinsRequired)) {
            showEarnCoinsDialog(coinsRequired);
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            slideThumb.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        } else {
            slideThumb.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }

        String message = getWallpaperMessage(wallpaperFlag);
        WallpaperUtils.showProgressDialog(activity, message, false);

        if (activity.getEnhancedImageUri() != null) {
            WallpaperUtils.setWallpaperFromUri(activity, activity.getEnhancedImageUri(), wallpaperFlag);
            deductCoins(coinsRequired);
            
            // FIX 2: Dismiss the progress dialog after setting an enhanced wallpaper
            WallpaperUtils.dismissProgressDialog(activity);
        } else {
            try {
                Glide.with(activity)
                    .asBitmap()
                    .load(wallpaper.getImageUrl())
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                            if (!activity.isDestroyedOrFinishing()) {
                                WallpaperUtils.setWallpaperFromBitmap(activity, bitmap, wallpaperFlag);
                                deductCoins(coinsRequired);
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
                                Toast.makeText(activity, activity.getString(R.string.failed_load_wallpaper_image), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            } catch (Exception e) {
                WallpaperUtils.dismissProgressDialog(activity);
                Toast.makeText(activity, activity.getString(R.string.failed_start_wallpaper_loading, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getWallpaperMessage(int wallpaperFlag) {
        if (wallpaperFlag == WALLPAPER_SYSTEM) {
            return "Setting home screen wallpaper...";
        } else if (wallpaperFlag == WALLPAPER_LOCK) {
            return "Setting lock screen wallpaper...";
        } else {
            return "Setting wallpaper for both screens...";
        }
    }
}