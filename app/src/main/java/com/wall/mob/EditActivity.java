package com.wall.mob;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// GPUImage Imports
import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.GPUImageView;
import jp.co.cyberagent.android.gpuimage.filter.*;

public class EditActivity extends BaseActivity {

    private GPUImageView gpuImageView;
    private ImageView backButton;
    private SeekBar blurSeekBar, brightnessSeekBar, contrastSeekBar;
    private Button btnReset, btnApply;
    private RecyclerView filterRecyclerView;

    private Bitmap originalBitmap;
    private GPUImageFilter currentBaseFilter = new GPUImageFilter(); // Default is Original (Empty Filter)
    
    // Edit state variables
    private float currentBlurRadius = 0f;
    private float currentBrightness = 0f;
    private float currentContrast = 1f;

    // Filter Tray Variables
    private List<FilterItem> filterItems;
    private FilterAdapter filterAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit);
        setupWindow();
        initViews();
        setupListeners();
        setupFilterTray();
        loadImageFromIntent();
    }

    private void setupWindow() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
    }

    private void initViews() {
        gpuImageView = findViewById(R.id.gpuImageView);
        blurSeekBar = findViewById(R.id.blurSeekBar);
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar);
        contrastSeekBar = findViewById(R.id.contrastSeekBar);
        filterRecyclerView = findViewById(R.id.filterRecyclerView);
        
        btnReset = findViewById(R.id.btnReset);
        backButton = findViewById(R.id.back_button);
        btnApply = findViewById(R.id.btnApply);
    }

    private void setupListeners() {
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                
                if (seekBar.getId() == R.id.blurSeekBar) {
                    currentBlurRadius = progress; 
                } else if (seekBar.getId() == R.id.brightnessSeekBar) {
                    currentBrightness = (progress - 100) / 100f;
                } else if (seekBar.getId() == R.id.contrastSeekBar) {
                    currentContrast = progress / 100f;
                }
                applyFilters();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        blurSeekBar.setOnSeekBarChangeListener(listener);
        brightnessSeekBar.setOnSeekBarChangeListener(listener);
        contrastSeekBar.setOnSeekBarChangeListener(listener);

        btnReset.setOnClickListener(v -> resetImage());
        btnApply.setOnClickListener(v -> saveAndReturnToDetails());
        backButton.setOnClickListener(v -> finish());
    }

        private void setupFilterTray() {
        filterItems = new ArrayList<>();
        
        // 1. Original
        filterItems.add(new FilterItem("Original", new GPUImageFilter()));
        
        // 2. The Classics
        filterItems.add(new FilterItem("Sepia", new GPUImageSepiaToneFilter()));
        filterItems.add(new FilterItem("B&W", new GPUImageGrayscaleFilter()));

        // --- INSTAGRAM-STYLE FILTERS ---

        // "Warm" (Similar to Juno / Toaster) - Boosts Reds, warms up the image
        GPUImageColorMatrixFilter warmFilter = new GPUImageColorMatrixFilter();
        warmFilter.setColorMatrix(new float[]{
            1.15f, 0.0f,  0.0f,  0.0f,
            0.0f,  1.05f, 0.0f,  0.0f,
            0.0f,  0.0f,  0.90f, 0.0f,
            0.0f,  0.0f,  0.0f,  1.0f
        });
        filterItems.add(new FilterItem("Warm", warmFilter));

        // "Cool" (Similar to Clarendon) - Accentuates Blues, drops Reds slightly
        GPUImageColorMatrixFilter coolFilter = new GPUImageColorMatrixFilter();
        coolFilter.setColorMatrix(new float[]{
            0.90f, 0.0f,  0.0f,  0.0f,
            0.0f,  1.00f, 0.0f,  0.0f,
            0.0f,  0.0f,  1.15f, 0.0f,
            0.0f,  0.0f,  0.0f,  1.0f
        });
        filterItems.add(new FilterItem("Cool", coolFilter));

        // "Faded" (Similar to Reyes / Aden) - Softens contrast, vintage pastel vibe
        GPUImageColorMatrixFilter fadedFilter = new GPUImageColorMatrixFilter();
        fadedFilter.setColorMatrix(new float[]{
            0.85f, 0.1f,  0.1f,  0.0f,
            0.1f,  0.85f, 0.1f,  0.0f,
            0.1f,  0.1f,  0.85f, 0.0f,
            0.0f,  0.0f,  0.0f,  1.0f
        });
        filterItems.add(new FilterItem("Faded", fadedFilter));

        // "Cinematic" (Similar to Lark) - Pushes Greens and Blues, cinematic feel
        GPUImageColorMatrixFilter cinematicFilter = new GPUImageColorMatrixFilter();
        cinematicFilter.setColorMatrix(new float[]{
            0.95f, 0.05f, 0.05f, 0.0f,
            0.0f,  1.10f, 0.0f,  0.0f,
            0.0f,  0.0f,  1.10f, 0.0f,
            0.0f,  0.0f,  0.0f,  1.0f
        });
        filterItems.add(new FilterItem("Cinematic", cinematicFilter));

        // "Vintage" Retro Matrix
        GPUImageColorMatrixFilter vintageFilter = new GPUImageColorMatrixFilter();
        vintageFilter.setColorMatrix(new float[]{
            0.6f, 0.3f, 0.1f, 0.0f,
            0.2f, 0.7f, 0.1f, 0.0f,
            0.2f, 0.1f, 0.4f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        });
        filterItems.add(new FilterItem("Vintage", vintageFilter));

        // --- ARTISTIC & EFFECTS FILTERS ---
        
        filterItems.add(new FilterItem("Vignette", new GPUImageVignetteFilter()));
        
        // Haze creates a dreamy/foggy overlay
        GPUImageHazeFilter hazeFilter = new GPUImageHazeFilter();
        hazeFilter.setDistance(0.2f);
        hazeFilter.setSlope(-0.1f);
        filterItems.add(new FilterItem("Haze", hazeFilter));

        filterItems.add(new FilterItem("Sketch", new GPUImageSketchFilter())); // Pencil drawing
        filterItems.add(new FilterItem("Toon", new GPUImageToonFilter()));     // Cartoon effect
        filterItems.add(new FilterItem("Posterize", new GPUImagePosterizeFilter()));
        filterItems.add(new FilterItem("Pixelate", new GPUImagePixelationFilter()));
        filterItems.add(new FilterItem("Invert", new GPUImageColorInvertFilter()));

        // Setup RecyclerView Adapter
        filterAdapter = new FilterAdapter(filterItems, item -> {
            currentBaseFilter = item.filter;
            applyFilters();
        });
        
        filterRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        filterRecyclerView.setAdapter(filterAdapter);
    }


    private void loadImageFromIntent() {
        Intent intent = getIntent();
        if (intent.hasExtra("image_path")) {
            Bitmap bitmap = BitmapFactory.decodeFile(intent.getStringExtra("image_path"));
            if (bitmap != null) {
                originalBitmap = bitmap;
                gpuImageView.setImage(originalBitmap);
                generateFilterThumbnails(originalBitmap);
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void generateFilterThumbnails(Bitmap original) {
        // Create a tiny bitmap for performance on older hardware
        final Bitmap thumbBase = Bitmap.createScaledBitmap(original, 150, 150, false);
        final GPUImage thumbnailGpuImage = new GPUImage(this);
        thumbnailGpuImage.setImage(thumbBase);

        // Run on background thread so the UI doesn't freeze
        new Thread(() -> {
            for (FilterItem item : filterItems) {
                // If it's not the "Original" dummy filter
                if (!item.name.equals("Original")) {
                    thumbnailGpuImage.setFilter(item.filter);
                    item.thumbnail = thumbnailGpuImage.getBitmapWithFilterApplied();
                } else {
                    item.thumbnail = thumbBase;
                }
                // Notify adapter item by item so the tray loads dynamically
                runOnUiThread(() -> filterAdapter.notifyDataSetChanged());
            }
        }).start();
    }

    private void applyFilters() {
        GPUImageFilterGroup filterGroup = new GPUImageFilterGroup();

        // 1. Add Selected Pre-applied Base Filter
        filterGroup.addFilter(currentBaseFilter);

        // 2. Add Slider Adjustments
        if (currentBrightness != 0f) {
            filterGroup.addFilter(new GPUImageBrightnessFilter(currentBrightness));
        }
        if (currentContrast != 1f) {
            filterGroup.addFilter(new GPUImageContrastFilter(currentContrast));
        }
        if (currentBlurRadius > 0) {
            filterGroup.addFilter(new GPUImageGaussianBlurFilter(currentBlurRadius / 5f));
        }

        gpuImageView.setFilter(filterGroup);
        gpuImageView.requestRender();
    }

    private void resetImage() {
        currentBaseFilter = new GPUImageFilter();
        currentBlurRadius = 0f;
        currentBrightness = 0f;
        currentContrast = 1f;

        blurSeekBar.setProgress(0);
        brightnessSeekBar.setProgress(100);
        contrastSeekBar.setProgress(100);

        applyFilters();
    }

    private void saveAndReturnToDetails() {
        try {
            Bitmap resultBitmap = gpuImageView.getGPUImage().getBitmapWithFilterApplied();
            
            File cacheDir = getExternalCacheDir();
            if (cacheDir == null) cacheDir = getCacheDir();
            
            File editDir = new File(cacheDir, "edit");
            if (!editDir.exists() && !editDir.mkdirs()) throw new IOException("Cannot create edit directory");
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File editedFile = new File(editDir, "edited_wallpaper_" + timeStamp + ".jpg");
            
            FileOutputStream out = new FileOutputStream(editedFile);
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            out.flush();
            out.close();
            
            Intent resultIntent = new Intent();
            resultIntent.putExtra("edited_image_path", editedFile.getAbsolutePath());
            setResult(RESULT_OK, resultIntent);
            finish();
            
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save changes", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
    }

    // --- INNER CLASSES FOR FILTER TRAY ---

    private static class FilterItem {
        String name;
        GPUImageFilter filter;
        Bitmap thumbnail;

        FilterItem(String name, GPUImageFilter filter) {
            this.name = name;
            this.filter = filter;
        }
    }

    private interface OnFilterClickListener {
        void onFilterClick(FilterItem item);
    }

    private static class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.ViewHolder> {
        private final List<FilterItem> items;
        private final OnFilterClickListener listener;

        FilterAdapter(List<FilterItem> items, OnFilterClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_filter, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FilterItem item = items.get(position);
            holder.nameText.setText(item.name);
            
            if (item.thumbnail != null) {
                holder.thumbnailImage.setImageBitmap(item.thumbnail);
            } else {
                holder.thumbnailImage.setImageBitmap(null); // Clear while generating
            }

            holder.itemView.setOnClickListener(v -> listener.onFilterClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView thumbnailImage;
            TextView nameText;

            ViewHolder(View itemView) {
                super(itemView);
                thumbnailImage = itemView.findViewById(R.id.filterThumbnail);
                nameText = itemView.findViewById(R.id.filterName);
            }
        }
    }
}
