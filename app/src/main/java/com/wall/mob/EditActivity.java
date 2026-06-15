package com.wall.mob;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.graphics.Color;

public class EditActivity extends AppCompatActivity {

    private ImageView imageView, backButton;
    private SeekBar blurSeekBar;
    private Button btnOriginal, btnSepia, btnGrayscale, btnVintage, btnReset, btnApply;
    
    private Bitmap originalBitmap;
    private Bitmap currentBitmap;
    private RenderScript renderScript;
    
    private enum FilterType { ORIGINAL, SEPIA, GRAYSCALE, VINTAGE };
    
    private FilterType currentFilter = FilterType.ORIGINAL;
    private float currentBlurRadius = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit);
        
        
        
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



        initViews();
        setupListeners();
        loadImageFromIntent();
        initializeRenderScript();
        btnOriginal.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.white));
    }

    private void initViews() {
        imageView = findViewById(R.id.imageView);
        blurSeekBar = findViewById(R.id.blurSeekBar);
        btnOriginal = findViewById(R.id.btnOriginal);
        btnSepia = findViewById(R.id.btnSepia);
        btnGrayscale = findViewById(R.id.btnGrayscale);
        btnVintage = findViewById(R.id.btnVintage);
        btnReset = findViewById(R.id.btnReset);
        backButton = findViewById(R.id.back_button);
        btnApply = findViewById(R.id.btnApply);
    }

    private void setupListeners() {
        // Blur seekbar listener
        blurSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && originalBitmap != null) {
                    currentBlurRadius = progress;
                    applyFilters();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Color filter buttons
        btnOriginal.setOnClickListener(v -> applyColorFilter(FilterType.ORIGINAL));
        btnSepia.setOnClickListener(v -> applyColorFilter(FilterType.SEPIA));
        btnGrayscale.setOnClickListener(v -> applyColorFilter(FilterType.GRAYSCALE));
        btnVintage.setOnClickListener(v -> applyColorFilter(FilterType.VINTAGE));

        // Action buttons
        btnReset.setOnClickListener(v -> resetImage());
        btnApply.setOnClickListener(v -> saveAndReturnToDetails());
        backButton.setOnClickListener(v -> finish());
    }

    private void loadImageFromIntent() {
        Intent intent = getIntent();
        
        if (intent.hasExtra("image_path")) {
            String imagePath = intent.getStringExtra("image_path");
            loadImageFromPath(imagePath);
        } else {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadImageFromPath(String path) {
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap != null) {
            setOriginalBitmap(bitmap);
        } else {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setOriginalBitmap(Bitmap bitmap) {
        // Scale down large images to prevent memory issues
        int maxSize = 1920;
        if (bitmap.getWidth() > maxSize || bitmap.getHeight() > maxSize) {
            float scale = Math.min((float) maxSize / bitmap.getWidth(), 
                                 (float) maxSize / bitmap.getHeight());
            int newWidth = Math.round(bitmap.getWidth() * scale);
            int newHeight = Math.round(bitmap.getHeight() * scale);
            bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        }
        
        originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        currentBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        imageView.setImageBitmap(currentBitmap);
    }

    private void initializeRenderScript() {
        renderScript = RenderScript.create(this);
    }

    private void applyColorFilter(FilterType filterType) {
        currentFilter = filterType;
        updateButtonStates();
        applyFilters();
    }

    private void updateButtonStates() {
        // Reset all button backgrounds
        btnOriginal.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.black));
        btnSepia.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.black));
        btnGrayscale.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.black));
        btnVintage.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.black));
        
        // Highlight selected filter
        switch (currentFilter) {
            case ORIGINAL:
                btnOriginal.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.white));
                break;
            case SEPIA:
                btnSepia.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.white));
                break;
            case GRAYSCALE:
                btnGrayscale.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.white));
                break;
            case VINTAGE:
                btnVintage.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.white));
                break;
        }
    }

    private void applyFilters() {
        if (originalBitmap == null) return;

        // Start with original bitmap
        Bitmap tempBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);

        // Apply color filter first
        tempBitmap = applyColorFilterToBitmap(tempBitmap, currentFilter);

        // Apply blur if needed
        if (currentBlurRadius > 0) {
            tempBitmap = applyBlur(tempBitmap, currentBlurRadius);
        }

        currentBitmap = tempBitmap;
        imageView.setImageBitmap(currentBitmap);
    }

    private Bitmap applyColorFilterToBitmap(Bitmap bitmap, FilterType filterType) {
        if (filterType == FilterType.ORIGINAL) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }

        ColorMatrix colorMatrix = new ColorMatrix();
        
        switch (filterType) {
            case SEPIA:
                colorMatrix.setSaturation(0);
                ColorMatrix sepiaMatrix = new ColorMatrix();
                sepiaMatrix.set(new float[]{
                    0.393f, 0.769f, 0.189f, 0, 0,
                    0.349f, 0.686f, 0.168f, 0, 0,
                    0.272f, 0.534f, 0.131f, 0, 0,
                    0, 0, 0, 1, 0
                });
                colorMatrix.postConcat(sepiaMatrix);
                break;
                
            case GRAYSCALE:
                colorMatrix.setSaturation(0);
                break;
                
            case VINTAGE:
                colorMatrix.set(new float[]{
                    0.6f, 0.3f, 0.1f, 0, 40,
                    0.2f, 0.7f, 0.1f, 0, 20,
                    0.2f, 0.1f, 0.4f, 0, 20,
                    0, 0, 0, 1, 0
                });
                break;
        }

        Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        ColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        
        return result;
    }

    private Bitmap applyBlur(Bitmap bitmap, float radius) {
        if (renderScript == null) return bitmap;

        Bitmap outputBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        
        Allocation input = Allocation.createFromBitmap(renderScript, bitmap);
        Allocation output = Allocation.createFromBitmap(renderScript, outputBitmap);
        
        ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        script.setRadius(Math.max(1f, Math.min(25f, radius)));
        script.setInput(input);
        script.forEach(output);
        
        output.copyTo(outputBitmap);
        
        input.destroy();
        output.destroy();
        script.destroy();
        
        return outputBitmap;
    }

    private void resetImage() {
        if (originalBitmap != null) {
            currentFilter = FilterType.ORIGINAL;
            currentBlurRadius = 0f;
            blurSeekBar.setProgress(0);
            updateButtonStates();
            currentBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            imageView.setImageBitmap(currentBitmap);
        }
    }

    private void saveAndReturnToDetails() {
        if (currentBitmap == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Save edited image to app's cache directory (no permission needed)
            File cacheDir = getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = getCacheDir();
            }
            
            File editDir = new File(cacheDir, "edit");
            if (!editDir.exists() && !editDir.mkdirs()) {
                throw new IOException("Cannot create edit directory");
            }
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "edited_wallpaper_" + timeStamp + ".jpg";
            File editedFile = new File(editDir, fileName);
            
            FileOutputStream out = new FileOutputStream(editedFile);
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            
            // Return the edited image path to WallpaperDetailsActivity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("edited_image_path", editedFile.getAbsolutePath());
            setResult(RESULT_OK, resultIntent);
            finish();
            
            Toast.makeText(this, "Changes applied successfully!", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save changes", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (renderScript != null) {
            renderScript.destroy();
        }
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
    }
}