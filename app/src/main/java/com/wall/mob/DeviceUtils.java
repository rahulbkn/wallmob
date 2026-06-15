package com.wall.mob;

import android.content.Context;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

public class DeviceUtils {
    private final int deviceWidth;
    private final int deviceHeight;
    private final float aspectRatio;
    private final int pixelDensity;
    private final int scaleFactor;

    public DeviceUtils(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);

        // Get actual device dimensions
        this.deviceWidth = size.x;
        this.deviceHeight = size.y;
        this.aspectRatio = (float) deviceHeight / deviceWidth;

        // Get pixel density
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        this.pixelDensity = metrics.densityDpi;
        
        // Calculate scale factor based on density
        if (pixelDensity <= 160) {
            this.scaleFactor = 1; // mdpi
        } else if (pixelDensity <= 240) {
            this.scaleFactor = 2; // hdpi
        } else if (pixelDensity <= 320) {
            this.scaleFactor = 2; // xhdpi
        } else if (pixelDensity <= 480) {
            this.scaleFactor = 3; // xxhdpi
        } else {
            this.scaleFactor = 4; // xxxhdpi
        }
    }

    public int getDeviceWidth() {
        return deviceWidth;
    }

    public int getDeviceHeight() {
        return deviceHeight;
    }

    public float getAspectRatio() {
        return aspectRatio;
    }

    public int getPixelDensity() {
        return pixelDensity;
    }

    public int getScaleFactor() {
        return scaleFactor;
    }

    // Get optimal image width for downloading (2x device width for quality)
    public int getOptimalImageWidth() {
        return deviceWidth * 2;
    }

    // Get optimal image height for downloading (2x device height for quality)
    public int getOptimalImageHeight() {
        return deviceHeight * 2;
    }

    // Get minimum acceptable width (for API filters)
    public int getMinImageWidth() {
        return deviceWidth / 2;
    }

    // Get minimum acceptable height (for API filters)
    public int getMinImageHeight() {
        return deviceHeight / 2;
    }

    // Check if image dimensions are suitable for this device
    public boolean isImageSuitable(int imageWidth, int imageHeight) {
        if (imageWidth < getMinImageWidth() || imageHeight < getMinImageHeight()) {
            return false;
        }

        float imageAspectRatio = (float) imageHeight / imageWidth;
        float aspectRatioDiff = Math.abs(imageAspectRatio - aspectRatio);
        
        // Allow 10% aspect ratio difference
        return aspectRatioDiff < 0.1f * aspectRatio;
    }

    @Override
    public String toString() {
        return "DeviceUtils{" +
                "width=" + deviceWidth +
                ", height=" + deviceHeight +
                ", aspectRatio=" + String.format("%.2f", aspectRatio) +
                ", density=" + pixelDensity +
                ", scaleFactor=" + scaleFactor +
                '}';
    }
}