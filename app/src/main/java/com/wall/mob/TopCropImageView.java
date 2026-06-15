package com.wall.mob; // Make sure this matches your package name

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

public class TopCropImageView extends AppCompatImageView {

    public TopCropImageView(Context context) {
        super(context);
        setup();
    }

    public TopCropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public TopCropImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setup();
    }

    private void setup() {
        // We must use MATRIX scale type to manually control the crop
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        boolean changed = super.setFrame(l, t, r, b);
        
        if (getDrawable() != null) {
            Matrix matrix = getImageMatrix();
            float scale;
            
            int viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            int viewHeight = getHeight() - getPaddingTop() - getPaddingBottom();
            int drawableWidth = getDrawable().getIntrinsicWidth();
            int drawableHeight = getDrawable().getIntrinsicHeight();

            // Calculate scale so the image fills the width
            scale = (float) viewWidth / (float) drawableWidth;
            
            // If scaling by width makes the image too short, scale by height instead
            if (scale * drawableHeight < viewHeight) {
                scale = (float) viewHeight / (float) drawableHeight;
            }

            matrix.setScale(scale, scale);
            
            // Center the image horizontally, but leave Y at 0 to anchor to the TOP
            float dx = (viewWidth - (drawableWidth * scale)) * 0.5f;
            matrix.postTranslate(dx, 0);

            setImageMatrix(matrix);
        }
        return changed;
    }
}