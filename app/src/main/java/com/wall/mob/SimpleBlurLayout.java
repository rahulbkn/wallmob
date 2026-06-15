package com.wall.mob;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

public class SimpleBlurLayout extends LinearLayout {
    
    private Paint blurPaint;
    private float blurIntensity = 0.85f; // 0.0 to 1.0
    
    public SimpleBlurLayout(Context context) {
        super(context);
        init();
    }
    
    public SimpleBlurLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        blurPaint = new Paint();
        
        // For Android 12+ use blur effect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.TRANSPARENT),
                null,
                null
            ));
        }
        
        // Set semi-transparent white background
        setBackgroundColor(Color.argb(180, 255, 255, 255));
    }
    
    public void setBlurIntensity(float intensity) {
        this.blurIntensity = Math.max(0.0f, Math.min(1.0f, intensity));
        int alpha = (int)(180 * intensity);
        setBackgroundColor(Color.argb(alpha, 255, 255, 255));
        invalidate();
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Save canvas state
        canvas.save();
        
        // Create a layer for blur effect
        int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), blurPaint, Canvas.ALL_SAVE_FLAG);
        
        // Draw children
        super.dispatchDraw(canvas);
        
        // Apply blur effect (simple alpha overlay)
        canvas.drawColor(Color.argb((int)(100 * blurIntensity), 255, 255, 255));
        
        // Restore canvas
        canvas.restoreToCount(layer);
        canvas.restore();
    }
}