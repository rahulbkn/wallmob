package com.wall.mob;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WallpaperDetailsActivity extends BaseActivity {


    private static final String TAG = "WallpaperDetails";
    private static final String EXTRA_WALLPAPER = "extra_wallpaper";
    private static final int REQUEST_EDIT = 1001;

    private ImageView wallpaperImage;
    private ProgressBar imageProgressBar;
    private TextView wallpaperTitle;
    private TextView wallpaperAuthor;
    private TextView wallpaperSource;
    private ImageButton backButton;
    private ImageButton favButton;
    private ImageButton shareBtn;
    private ImageButton downloadBtn;
    private ImageButton editBtn;
    private View slideToSetContainer;
    private View slideThumb;
    private TextView slideText;

    private Wallpaper wallpaper;
    private boolean isDestroyed = false;
    private Uri enhancedImageUri = null;

    private ExecutorService executorService;

    private WallpaperImageLoader imageLoader;
    private SlideToSetHandler slideToSetHandler;
    private WallpaperActionsHandler actionsHandler;
    private WallpaperDeepLinkHandler deepLinkHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wallpaper_details);

        // Force unlimited coins
        getSharedPreferences("GamePrefs", MODE_PRIVATE)
                .edit()
                .putInt("coins", Integer.MAX_VALUE)
                .apply();

        executorService = Executors.newSingleThreadExecutor();

        // Transparent system UI
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(params);
        }

        // Views
        wallpaperImage = findViewById(R.id.wallpaperImage);
        imageProgressBar = findViewById(R.id.imageProgressBar);
        wallpaperTitle = findViewById(R.id.wallpaperTitle);
        wallpaperAuthor = findViewById(R.id.wallpaper_author);
        wallpaperSource = findViewById(R.id.wallpaper_source);
        backButton = findViewById(R.id.back_button);
        favButton = findViewById(R.id.fav_button);
        shareBtn = findViewById(R.id.share_btn);
        downloadBtn = findViewById(R.id.download_btn);
        editBtn = findViewById(R.id.edit_btn);
        slideToSetContainer = findViewById(R.id.slide_to_set_container);
        slideThumb = findViewById(R.id.slide_thumb);
        slideText = findViewById(R.id.slide_text);

        // Handlers
        imageLoader = new WallpaperImageLoader(this, wallpaperImage);
        slideToSetHandler =
                new SlideToSetHandler(this, slideToSetContainer, slideThumb, slideText);
        actionsHandler =
                new WallpaperActionsHandler(this, favButton, shareBtn, downloadBtn, editBtn);
        deepLinkHandler =
                new WallpaperDeepLinkHandler(
                        this,
                        wallpaperTitle,
                        wallpaperAuthor,
                        wallpaperSource,
                        imageLoader
                );

        backButton.setOnClickListener(v -> finish());

        // Handle initial intent
        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        Uri data = intent.getData();

        // =========================
        // 🔗 Handle Deep Link
        // =========================
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && data != null) {
            Log.d(TAG, "Opened via deep link: " + data);

            String path = data.getPath();
            String id = null;

            if (path != null) {
                if (path.startsWith("/w/")) {
                    String[] segments = path.split("/");
                    if (segments.length >= 3) {
                        id = data.getLastPathSegment();
                    }
                } else if (path.startsWith("/wallpaper/")) {
                    String[] segments = path.split("/");
                    if (segments.length >= 3) {
                        id = data.getLastPathSegment();
                    }
                }
            }

            if (id != null && !id.isEmpty()) {
                deepLinkHandler.loadWallpaperById(id, new WallpaperDeepLinkHandler.Callback() {
                    @Override
                    public void onSuccess(Wallpaper result) {
                        wallpaper = result;
                        bindWallpaperToUI();
                    }

                    @Override
                    public void onError() {
                        showLoadErrorAndFinish();
                    }
                });
                return;
            }

            if (path != null && path.equals("/wallpaper") && data.getQuery() != null) {
                Log.d(TAG, "Using old query parameter format");
                wallpaper = parseWallpaperFromQueryParams(data);
                if (wallpaper != null) {
                    bindWallpaperToUI();
                    return;
                }
            }
        }

        // =========================
        // 📱 Normal In-App Navigation
        // =========================
        Parcelable p = intent.getParcelableExtra(EXTRA_WALLPAPER);
        if (p instanceof Wallpaper) {
            wallpaper = (Wallpaper) p;
            bindWallpaperToUI();
            return;
        }

        showLoadErrorAndFinish();
    }

    private Wallpaper parseWallpaperFromQueryParams(Uri uri) {
        try {
            String title = uri.getQueryParameter("title");
            String author = uri.getQueryParameter("author");
            String source = uri.getQueryParameter("source");
            String category = uri.getQueryParameter("category");
            String image = uri.getQueryParameter("image");

            if (image == null || image.isEmpty()) {
                Log.e(TAG, "Missing required image URL in query params");
                return null;
            }

            String id = "shared_" + uri.getQueryParameter("ts");
            if (id.equals("shared_null")) {
                id = "shared_" + System.currentTimeMillis();
            }

            return new Wallpaper(
                    id,
                    image,
                    title != null ? title : "Shared Wallpaper",
                    category,
                    source != null ? source : "Shared",
                    author,
                    false
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse wallpaper from query params", e);
            return null;
        }
    }

    private void bindWallpaperToUI() {
        deepLinkHandler.updateUIWithWallpaper(wallpaper);
        actionsHandler.setupButtonListeners(wallpaper);
        slideToSetHandler.setupSlideToSet(wallpaper);

        // =========================
        // 📥 LOAD IMAGE (BLUR-UP EFFECT) — FIXED
        // =========================
        if (wallpaper == null || wallpaper.getImageUrl() == null || wallpaper.getImageUrl().isEmpty()) {
            Log.e(TAG, "Wallpaper imageUrl is null or empty, cannot load");
            return;
        }

        String fullImageUrl = wallpaper.getImageUrl();

        // ✅ FIX: Safe thumbnailUrl — fallback to fullImageUrl if null/empty
        String thumbUrl = (wallpaper.getThumbnailUrl() != null && !wallpaper.getThumbnailUrl().trim().isEmpty())
                ? wallpaper.getThumbnailUrl()
                : fullImageUrl;

        Log.d(TAG, "Loading image: " + fullImageUrl);
        Log.d(TAG, "Loading thumbnail: " + thumbUrl);

        imageProgressBar.setVisibility(View.VISIBLE);

        // ✅ FIX: Thumbnail loads with small override so Glide doesn't re-fetch full size
        RequestOptions thumbOptions = new RequestOptions()
                .override(120, 200)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop();

        RequestOptions fullOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop();

        Glide.with(this)
                .load(fullImageUrl)
                .apply(fullOptions)
                .thumbnail(
                        Glide.with(this)
                                .load(thumbUrl)
                                .apply(thumbOptions)
                )
                .transition(DrawableTransitionOptions.withCrossFade(500))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e,
                                               Object model,
                                               Target<Drawable> target,
                                               boolean isFirstResource) {
                        Log.e(TAG, "Full image load failed: " + (e != null ? e.getMessage() : "unknown"), e);
                        imageProgressBar.setVisibility(View.GONE);
                        Toast.makeText(WallpaperDetailsActivity.this,
                                "Image load failed. Check internet connection.",
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource,
                                                  Object model,
                                                  Target<Drawable> target,
                                                  DataSource dataSource,
                                                  boolean isFirstResource) {
                        imageProgressBar.setVisibility(View.GONE);
                        Log.d(TAG, "Image loaded successfully from: " + dataSource.name());
                        return false;
                    }
                })
                .into(wallpaperImage);
    }

    private void showLoadErrorAndFinish() {
        Toast.makeText(this, getString(R.string.failed_load_wallpaper_details), Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (isDestroyed || isFinishing()) return;

        if (requestCode == REQUEST_EDIT && resultCode == RESULT_OK && data != null) {
            enhancedImageUri = actionsHandler.handleEditResult(data);
            if (enhancedImageUri != null) {
                imageProgressBar.setVisibility(View.GONE);
                imageLoader.loadImage(enhancedImageUri, true);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        WallpaperUtils.dismissProgressDialog(this);
        actionsHandler.cleanup();
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    public boolean isDestroyedOrFinishing() {
        return isDestroyed || isFinishing();
    }

    public Uri getEnhancedImageUri() {
        return enhancedImageUri;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public static void start(Context context, Wallpaper wallpaper) {
        Intent intent = new Intent(context, WallpaperDetailsActivity.class);
        intent.putExtra(EXTRA_WALLPAPER, (Parcelable) wallpaper);
        context.startActivity(intent);
    }
}

// test
